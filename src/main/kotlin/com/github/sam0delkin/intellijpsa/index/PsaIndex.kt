@file:Suppress("PLUGIN_IS_NOT_ENABLED", "ktlint:standard:no-wildcard-imports")

package com.github.sam0delkin.intellijpsa.index

import com.github.sam0delkin.intellijpsa.exception.IndexNotReadyException
import com.github.sam0delkin.intellijpsa.exception.IndexingDisabledException
import com.github.sam0delkin.intellijpsa.services.CompletionService
import com.github.sam0delkin.intellijpsa.services.PsiElementModel
import com.github.sam0delkin.intellijpsa.services.RequestType
import com.github.sam0delkin.intellijpsa.settings.Settings
import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.application.ex.ApplicationUtil
import com.intellij.openapi.application.runReadAction
import com.intellij.openapi.components.Service
import com.intellij.openapi.components.service
import com.intellij.openapi.progress.ProcessCanceledException
import com.intellij.openapi.progress.ProgressIndicator
import com.intellij.openapi.progress.ProgressManager
import com.intellij.openapi.progress.Task.Backgroundable
import com.intellij.openapi.project.DumbService
import com.intellij.openapi.project.Project
import com.intellij.openapi.util.Key
import com.intellij.openapi.vfs.VirtualFile
import com.intellij.openapi.vfs.VirtualFileManager
import com.intellij.psi.PsiElement
import com.intellij.psi.PsiFile
import com.intellij.psi.PsiManager
import com.intellij.psi.util.PsiTreeUtil
import com.intellij.util.gist.GistManager
import com.intellij.util.io.EnumeratorStringDescriptor
import com.jetbrains.rd.util.string.printToString
import kotlinx.serialization.Serializable
import kotlinx.serialization.decodeFromString
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.*
import java.util.concurrent.ExecutorService
import java.util.concurrent.Executors
import java.util.concurrent.TimeUnit
import kotlin.concurrent.thread

@Serializable
data class IndexedPsiElementModel(
    val model: PsiElementModel,
    val textRange: String,
)

private val psaFileDataKey = Key<String>("PSA_FILE_DATA_KEY")
private val psaFileIndicatorKey = Key<ProgressIndicator>("PSA_FILE_INDICATOR_KEY")
private val psaGoToElementKey = Key<String>("PSA_GOTO_ELEMENT_KEY")
private val psaCompletionElementKey = Key<String>("PSA_COMPLETION_ELEMENT_KEY")

private val gist =
    GistManager.getInstance().newPsiFileGist("PSA", 1, EnumeratorStringDescriptor.INSTANCE) {
        val previousIndicator = it.getUserData(psaFileIndicatorKey)
        if (null !== previousIndicator) {
            previousIndicator.cancel()
        }

        val existingData = it.getUserData(psaFileDataKey)
        if (existingData != null) {
            it.putUserData(psaFileDataKey, null)

            return@newPsiFileGist existingData
        }
        val project = it.project
        val progressManager = ProgressManager.getInstance()
        val completionService = project.service<CompletionService>()
        val settings = completionService.getSettings()

        if (!settings.pluginEnabled || !settings.indexingEnabled || settings.debug) {
            return@newPsiFileGist "{}"
        }

        if (DumbService.isDumb(project)) {
            DumbService.getInstance(project).runWhenSmart {
                val psaIndex = project.service<PsaIndex>()
                psaIndex.reindexFile(it)
            }

            return@newPsiFileGist "null"
        }
        val file = it
        val elements = ArrayList<String>()
        val processor =
            PsiFileProcessor(completionService, elements)

        PsiTreeUtil.processElements(it, processor)

        if (elements.size > settings.indexingMaxElements) {
            return@newPsiFileGist "null"
        }

        progressManager.run(
            object : Backgroundable(project, "PSA: Indexing File " + it.name + "...") {
                override fun run(indicator: ProgressIndicator) {
                    file.putUserData(psaFileIndicatorKey, indicator)
                    try {
                        ApplicationUtil.runWithCheckCanceled(
                            {
                                val workerPool: ExecutorService = Executors.newFixedThreadPool(settings.indexingConcurrency)
                                if (indicator.isCanceled) {
                                    workerPool.shutdownNow()
                                    return@runWithCheckCanceled
                                }

                                if (elements.isEmpty()) {
                                    workerPool.shutdownNow()
                                    return@runWithCheckCanceled
                                }

                                val map = HashMap<String, String>()
                                val language = file.language
                                var languageString = language.id

                                if (language.baseLanguage !== null && !settings.isLanguageSupported(languageString)) {
                                    languageString = language.baseLanguage!!.id
                                }

                                val batchCount = settings.indexingBatchCount
                                var batchCurrent = 0
                                var processed = 0
                                indicator.text2 = "Processing elements..."

                                while (true) {
                                    if (indicator.isCanceled) {
                                        workerPool.shutdownNow()
                                        return@runWithCheckCanceled
                                    }

                                    val currentElements: List<String> =
                                        try {
                                            elements.slice(IntRange(batchCurrent, batchCurrent + batchCount - 1))
                                        } catch (e: IndexOutOfBoundsException) {
                                            elements.slice(IntRange(batchCurrent, elements.size - 1))
                                        }
                                    val currentElementModels = ArrayList<IndexedPsiElementModel>()
                                    val modelsWorkerPool: ExecutorService = Executors.newFixedThreadPool(settings.indexingConcurrency)

                                    for (currentElement in currentElements) {
                                        val data = currentElement.split("::")
                                        indicator.text2 = "Parsing " + elements.size + " elements..."
                                        modelsWorkerPool.submit {
                                            runReadAction {
                                                var innerElement: PsiElement? = null
                                                runReadAction {
                                                    innerElement = file.findElementAt(data[1].toInt())
                                                }
                                                if (null === innerElement) {
                                                    return@runReadAction
                                                }

                                                if (innerElement!!.getUserData(psaGoToElementKey) != null) {
                                                    map[
                                                        PsaIndex.generateGoToKeyByElement(
                                                            innerElement!!,
                                                        ),
                                                    ] = innerElement!!.getUserData(psaGoToElementKey).toString()
                                                    map[
                                                        PsaIndex.generateCompletionsKeyElement(
                                                            innerElement!!,
                                                        ),
                                                    ] = innerElement!!.getUserData(psaCompletionElementKey).toString()

                                                    return@runReadAction
                                                }

                                                val currentModel =
                                                    completionService.psiElementToModel(innerElement!!)
                                                currentElementModels.add(
                                                    IndexedPsiElementModel(
                                                        currentModel,
                                                        innerElement!!.textRange.printToString(),
                                                    ),
                                                )

                                                if (indicator.isCanceled) {
                                                    modelsWorkerPool.shutdownNow()
                                                    workerPool.shutdownNow()
                                                    return@runReadAction
                                                }
                                            }
                                        }

                                        if (indicator.isCanceled) {
                                            modelsWorkerPool.shutdownNow()
                                            workerPool.shutdownNow()
                                            return@runWithCheckCanceled
                                        }
                                    }

                                    modelsWorkerPool.shutdown()
                                    modelsWorkerPool.awaitTermination(1, TimeUnit.HOURS)

                                    if (currentElementModels.isEmpty()) {
                                        break
                                    }

                                    // running completions
                                    if (settings.supportsBatch) {
                                        workerPool.submit {
                                            val goToCompletions =
                                                completionService
                                                    .getCompletions(
                                                        settings,
                                                        currentElementModels.toTypedArray(),
                                                        null,
                                                        RequestType.BatchGoTo,
                                                        languageString,
                                                        null,
                                                        false,
                                                        false,
                                                        true,
                                                        indicator,
                                                    )?.jsonArray

                                            for (i in 0 until currentElementModels.size) {
                                                val model = currentElementModels[i]
                                                val completionsObject = goToCompletions?.jsonArray?.get(i)?.jsonObject
                                                var innerElement: PsiElement? = null
                                                runReadAction {
                                                    innerElement = file.findElementAt(model.model.textRange!!.startOffset)
                                                }
                                                if (null === innerElement) {
                                                    continue
                                                }

                                                if (null !== completionsObject &&
                                                    completionsObject.containsKey("completions") &&
                                                    !completionsObject
                                                        .get(
                                                            "completions",
                                                        )?.jsonArray
                                                        ?.isEmpty()!!
                                                ) {
                                                    val encodeToString = Json.encodeToString(completionsObject)
                                                    map[
                                                        PsaIndex.generateGoToKeyByModel(
                                                            model,
                                                        ),
                                                    ] = encodeToString
                                                    innerElement!!.putUserData(psaGoToElementKey, encodeToString)
                                                } else {
                                                    innerElement!!.putUserData(psaGoToElementKey, "{}")
                                                }
                                            }

                                            val completions =
                                                completionService.getCompletions(
                                                    settings,
                                                    currentElementModels.toTypedArray(),
                                                    null,
                                                    RequestType.BatchCompletion,
                                                    languageString,
                                                    null,
                                                    false,
                                                    false,
                                                    true,
                                                    indicator,
                                                )

                                            for (i in 0 until currentElementModels.size) {
                                                val model = currentElementModels[i]
                                                val completionsObject = completions?.jsonArray?.get(i)?.jsonObject
                                                var innerElement: PsiElement? = null
                                                runReadAction {
                                                    innerElement = file.findElementAt(model.model.textRange!!.startOffset)
                                                }

                                                if (null === innerElement) {
                                                    continue
                                                }

                                                if (null !== completionsObject &&
                                                    completionsObject.containsKey("completions") &&
                                                    !completionsObject
                                                        .get(
                                                            "completions",
                                                        )?.jsonArray
                                                        ?.isEmpty()!!
                                                ) {
                                                    val encodeToString = Json.encodeToString(completionsObject)
                                                    map[
                                                        PsaIndex.generateCompletionsKeyByModel(
                                                            model,
                                                        ),
                                                    ] = encodeToString
                                                    innerElement!!.putUserData(psaCompletionElementKey, encodeToString)
                                                } else {
                                                    innerElement!!.putUserData(psaCompletionElementKey, "{}")
                                                }
                                            }
                                            processed += batchCount
                                            indicator.fraction = processed.toDouble() / elements.size
                                        }
                                    } else {
                                        var i = 0
                                        for (model in currentElementModels) {
                                            workerPool.submit {
                                                val goToCompletions =
                                                    completionService
                                                        .getCompletions(
                                                            settings,
                                                            arrayOf(model),
                                                            null,
                                                            RequestType.GoTo,
                                                            languageString,
                                                            null,
                                                            false,
                                                            false,
                                                            true,
                                                            indicator,
                                                        )?.jsonObject
                                                var innerElement: PsiElement? = null
                                                runReadAction {
                                                    innerElement = file.findElementAt(model.model.textRange!!.startOffset)
                                                }

                                                if (null !== innerElement) {
                                                    if (null !== goToCompletions &&
                                                        goToCompletions.containsKey("completions") &&
                                                        !goToCompletions.get("completions")?.jsonArray?.isEmpty()!!
                                                    ) {
                                                        val encodeToString = Json.encodeToString(goToCompletions)
                                                        map[
                                                            PsaIndex.generateGoToKeyByModel(
                                                                model,
                                                            ),
                                                        ] = encodeToString
                                                        innerElement!!.putUserData(psaGoToElementKey, encodeToString)
                                                    } else {
                                                        innerElement!!.putUserData(psaGoToElementKey, "{}")
                                                    }
                                                }

                                                val completions =
                                                    completionService
                                                        .getCompletions(
                                                            settings,
                                                            arrayOf(model),
                                                            null,
                                                            RequestType.Completion,
                                                            languageString,
                                                            null,
                                                            false,
                                                            false,
                                                            true,
                                                            indicator,
                                                        )?.jsonObject
                                                if (null !== innerElement) {
                                                    if (null !== completions &&
                                                        completions.containsKey("completions") &&
                                                        !completions.get("completions")?.jsonArray?.isEmpty()!!
                                                    ) {
                                                        val encodeToString = Json.encodeToString(completions)
                                                        map[
                                                            PsaIndex.generateGoToKeyByModel(
                                                                model,
                                                            ),
                                                        ] = Json.encodeToString(completions)
                                                        innerElement!!.putUserData(psaCompletionElementKey, encodeToString)
                                                    } else {
                                                        innerElement!!.putUserData(psaCompletionElementKey, "{}")
                                                    }
                                                }

                                                processed += batchCount
                                                indicator.fraction = (processed.toDouble() + i) / elements.size
                                                i++
                                            }
                                        }
                                    }
                                    batchCurrent += batchCount
                                }

                                workerPool.shutdown()
                                workerPool.awaitTermination(
                                    1,
                                    TimeUnit.HOURS,
                                )

                                if (indicator.isCanceled) {
                                    return@runWithCheckCanceled
                                }

                                runReadAction {
                                    file.putUserData(psaFileDataKey, Json.encodeToString(map))
                                    val psaIndex = project.service<PsaIndex>()
                                    psaIndex.reindexFile(file)
                                }
                            },
                            indicator,
                        )
                    } catch (_: ProcessCanceledException) {
                    }
                }
            },
        )

        return@newPsiFileGist "null"
    }

@Service(Service.Level.PROJECT)
class PsaIndex(
    private val project: Project,
) {
    companion object {
        val generateGoToKeyByModel = fun(model: IndexedPsiElementModel): String = "PSA_GOTO:" + model.textRange
        val generateGoToKeyByElement =
            @Suppress("ktlint:standard:max-line-length")
            fun(element: PsiElement): String = "PSA_GOTO:" + element.textRange
        val generateCompletionsKeyByModel =
            @Suppress("ktlint:standard:max-line-length")
            fun(model: IndexedPsiElementModel): String = "PSA_COMPLETIONS:" + model.textRange
        val generateCompletionsKeyElement =
            @Suppress("ktlint:standard:max-line-length")
            fun(element: PsiElement): String = "PSA_COMPLETIONS:" + element.textRange
        val generateKeyByRequestType = fun(
            requestType: RequestType,
            model: IndexedPsiElementModel,
        ): String {
            if (requestType === RequestType.GoTo) {
                return generateGoToKeyByModel(model)
            }
            return generateCompletionsKeyByModel(model)
        }
    }

    fun get(
        type: RequestType,
        position: Int,
        key: String,
        filePath: String?,
    ): String? {
        val settings = this.project.service<Settings>()
        if (!settings.indexingEnabled || settings.debug) {
            throw IndexingDisabledException()
        }

        if (null === filePath) {
            return null
        }

        val virtualFile = VirtualFileManager.getInstance().findFileByUrl("file://" + filePath)
        if (virtualFile != null) {
            val psiFile = PsiManager.getInstance(project).findFile(virtualFile)
            if (psiFile != null) {
                val innerElement = psiFile.findElementAt(position)

                if (null !== innerElement) {
                    var data = innerElement.getUserData(psaGoToElementKey)
                    if (null !== data && type === RequestType.GoTo) {
                        return data
                    }

                    data = innerElement.getUserData(psaCompletionElementKey)
                    if (null !== data && type === RequestType.Completion) {
                        return data
                    }
                }

                val data = getForFile(psiFile)
                try {
                    val map = Json.decodeFromString<Map<String, String>>(data!!)
                    if (map.containsKey(key)) {
                        return map[key]
                    }
                } catch (_: Exception) {
                    throw IndexNotReadyException()
                }
            }
        }

        return null
    }

    fun getForFile(file: PsiFile): String? = gist.getFileData(file)

    fun reindexFile(file: PsiFile) {
        ApplicationManager.getApplication().invokeLater {
            thread {
                GistManager.getInstance().invalidateData(file.virtualFile)

                val psaIndex = project.service<PsaIndex>()
                runReadAction {
                    psaIndex.getForFile(file)
                }
            }
        }
    }

    fun reindexFile(file: VirtualFile) {
        val psiFile = PsiManager.getInstance(project).findFile(file)
        if (null === psiFile) {
            return
        }

        this.reindexFile(psiFile)
    }
}
