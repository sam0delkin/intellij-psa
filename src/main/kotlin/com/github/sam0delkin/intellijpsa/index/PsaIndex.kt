@file:Suppress("PLUGIN_IS_NOT_ENABLED")

package com.github.sam0delkin.intellijpsa.index

import com.github.sam0delkin.intellijpsa.services.CompletionService
import com.github.sam0delkin.intellijpsa.services.PsiElementModel
import com.github.sam0delkin.intellijpsa.services.RequestType
import com.github.sam0delkin.intellijpsa.settings.Settings
import com.github.sam0delkin.intellijpsa.util.PsiUtils
import com.intellij.openapi.application.ex.ApplicationUtil
import com.intellij.openapi.application.runReadAction
import com.intellij.openapi.components.service
import com.intellij.openapi.progress.ProcessCanceledException
import com.intellij.openapi.progress.ProgressIndicator
import com.intellij.openapi.progress.ProgressManager
import com.intellij.openapi.progress.Task.Backgroundable
import com.intellij.openapi.project.DumbService
import com.intellij.openapi.project.Project
import com.intellij.openapi.vfs.VirtualFileManager
import com.intellij.openapi.util.Key
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
import com.github.sam0delkin.intellijpsa.exception.Exception.*
import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.vfs.VirtualFile

@Serializable
data class IndexedPsiElementModel(
    val model: PsiElementModel,
    val path: String,
    val label: String,
    val text: String,
    val textRange: String
)

private val psaFileKey = Key<String>("PSA_FILE_DATA_KEY")

private val gist = GistManager.getInstance().newPsiFileGist("PSA", 1, EnumeratorStringDescriptor.INSTANCE) {
    val existingData = it.getUserData(psaFileKey)
    if (existingData != null) {
        it.putUserData(psaFileKey, null)

        return@newPsiFileGist existingData
    }
    val project = it.project
    val progressManager = ProgressManager.getInstance()
    val completionService = project.service<CompletionService>()
    val settings = completionService.getSettings()

    if (!settings.indexingEnabled) {
        return@newPsiFileGist "null"
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
    progressManager.run(object : Backgroundable(project, "PSA: Indexing File " + it.name + "...") {
        override fun run(indicator: ProgressIndicator) {
            try {
                ApplicationUtil.runWithCheckCanceled({
                    val workerPool: ExecutorService = Executors.newFixedThreadPool(settings.indexingConcurrency)
                    val modelsWorkerPool: ExecutorService = Executors.newFixedThreadPool(settings.indexingConcurrency)
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

                    // generating models by elements
                    val elementModels = ArrayList<IndexedPsiElementModel>()
                    for (currentElement in elements) {
                        val data = currentElement.split("::")
                        indicator.text2 = "Parsing elements..."
                        modelsWorkerPool.submit {
                            runReadAction {
                                var innerElement: PsiElement? = null
                                runReadAction {
                                    innerElement = file.findElementAt(data[1].toInt())
                                }
                                if (null === innerElement) {
                                    return@runReadAction
                                }

                                val currentModel = completionService.psiElementToModel(innerElement!!)
                                elementModels.add(
                                    IndexedPsiElementModel(
                                        currentModel,
                                        PsiUtils.getPsiElementPath(innerElement!!),
                                        PsiUtils.getPsiElementLabel(innerElement!!),
                                        innerElement!!.text,
                                        innerElement!!.textRange.printToString()
                                    )
                                )

                            }
                        }
                    }

                    modelsWorkerPool.shutdown()
                    modelsWorkerPool.awaitTermination(1, TimeUnit.HOURS)

                    val batchCount = 20
                    var batchCurrent = 0
                    var processed = 0
                    indicator.text2 = "Processing elements..."

                    while (true) {
                        if (indicator.isCanceled) {
                            workerPool.shutdownNow()
                            return@runWithCheckCanceled
                        }

                        val currentElementModels: List<IndexedPsiElementModel> = try {
                            elementModels.slice(IntRange(batchCurrent, batchCurrent + batchCount - 1))
                        } catch (e: IndexOutOfBoundsException) {
                            elementModels.slice(IntRange(batchCurrent, elementModels.size - 1))
                        }

                        if (currentElementModels.isEmpty()) {
                            break
                        }

                        // running completions
                        if (settings.supportsBatch) {
                            workerPool.submit {
                                val goToCompletions = completionService.getCompletions(
                                    settings,
                                    currentElementModels.toTypedArray(),
                                    null,
                                    RequestType.BatchGoTo,
                                    languageString,
                                    null,
                                    false,
                                    false,
                                    true
                                )?.jsonArray

                                if (null !== goToCompletions && goToCompletions.jsonArray.isNotEmpty()) {
                                    for (i in 0 until currentElementModels.size) {
                                        val model = currentElementModels[i]
                                        val completionsObject = goToCompletions.jsonArray.get(i).jsonObject
                                        if (completionsObject.containsKey("completions") && !completionsObject.get(
                                                "completions"
                                            )?.jsonArray?.isEmpty()!!
                                        ) {
                                            var innerElement: PsiElement? = null
                                            runReadAction {
                                                innerElement = file.findElementAt(model.model.textRange!!.startOffset)
                                            }
                                            if (null === innerElement) {
                                                continue
                                            }
                                            map[PsaIndex.generateGoToKeyByModel(
                                                model
                                            )] = Json.encodeToString(completionsObject)
                                        }
                                    }
                                }

                                val completions = completionService.getCompletions(
                                    settings,
                                    currentElementModels.toTypedArray(),
                                    null,
                                    RequestType.BatchCompletion,
                                    languageString,
                                    null,
                                    false,
                                    false,
                                    true
                                )

                                if (null !== completions && completions.jsonArray.isNotEmpty()) {
                                    for (i in 0 until currentElementModels.size) {
                                        val model = currentElementModels[i]
                                        val completionsObject = completions.jsonArray.get(i).jsonObject
                                        if (completionsObject.containsKey("completions") && !completionsObject.get(
                                                "completions"
                                            )?.jsonArray?.isEmpty()!!
                                        ) {
                                            var innerElement: PsiElement? = null
                                            runReadAction {
                                                innerElement = file.findElementAt(model.model.textRange!!.startOffset)
                                            }
                                            if (null === innerElement) {
                                                continue
                                            }
                                            map[PsaIndex.generateCompletionsKeyByModel(
                                                model
                                            )] = Json.encodeToString(completionsObject)
                                            for (completion in completionsObject.get("completions")?.jsonArray!!) {
                                                val newLabel = model.label.replace(
                                                    model.text,
                                                    completion.jsonObject.get("text")!!.jsonPrimitive.content
                                                )
                                                if (!settings.elementPaths.contains(newLabel)) {
                                                    settings.elementPaths[newLabel] = true
                                                }
                                            }
                                        }
                                    }
                                }
                                processed += batchCount
                                indicator.fraction = processed.toDouble() / elementModels.size
                            }
                        } else {
                            var i = 0
                            for (model in currentElementModels) {
                                workerPool.submit {
                                    val goToCompletions = completionService.getCompletions(
                                        settings,
                                        arrayOf(model),
                                        null,
                                        RequestType.GoTo,
                                        languageString,
                                        null,
                                        false,
                                        false,
                                        true
                                    )?.jsonObject
                                    if (null !== goToCompletions) {
                                        if (goToCompletions.containsKey("completions") && !goToCompletions.get("completions")?.jsonArray?.isEmpty()!!) {
                                            var innerElement: PsiElement? = null
                                            runReadAction {
                                                innerElement = file.findElementAt(model.model.textRange!!.startOffset)
                                            }
                                            if (null !== innerElement) {
                                                map[PsaIndex.generateGoToKeyByModel(
                                                    model
                                                )] = Json.encodeToString(goToCompletions)
                                            }
                                        }
                                    }

                                    val completions = completionService.getCompletions(
                                        settings,
                                        arrayOf(model),
                                        null,
                                        RequestType.Completion,
                                        languageString,
                                        null,
                                        false,
                                        false,
                                        true
                                    )?.jsonObject
                                    if (null !== completions) {
                                        if (completions.containsKey("completions") && !completions.get("completions")?.jsonArray?.isEmpty()!!) {
                                            var innerElement: PsiElement? = null
                                            runReadAction {
                                                innerElement = file.findElementAt(model.model.textRange!!.startOffset)
                                            }
                                            if (null !== innerElement) {
                                                map[PsaIndex.generateGoToKeyByModel(
                                                    model
                                                )] = Json.encodeToString(completions)

                                                for (completion in completions.get("completions")?.jsonArray!!) {
                                                    val newLabel = model.label.replace(
                                                        model.text,
                                                        completion.jsonObject.get("text")!!.jsonPrimitive.content
                                                    )
                                                    if (!settings.elementPaths.contains(newLabel)) {
                                                        settings.elementPaths[newLabel] = true
                                                    }
                                                }
                                            }
                                        }
                                    }

                                    processed += batchCount
                                    indicator.fraction = (processed.toDouble() + i) / elementModels.size
                                    i++
                                }
                            }
                        }
                        batchCurrent += batchCount
                    }

                    workerPool.shutdown()
                    workerPool.awaitTermination(1, TimeUnit.HOURS)

                    if (indicator.isCanceled) {
                        return@runWithCheckCanceled
                    }

                    runReadAction {
                        file.putUserData(psaFileKey, Json.encodeToString(map))
                        val psaIndex = project.service<PsaIndex>()
                        psaIndex.reindexFile(file)
                    }
                }, indicator)
            } catch (_: ProcessCanceledException) {
            }
        }
    })

    return@newPsiFileGist "null"
}

class PsaIndex(private val project: Project) {
    companion object {
        val generateGoToKeyByModel = fun(model: IndexedPsiElementModel): String {
            return "PSA_GOTO:" + model.textRange + ":" + model.model.filePath
        }
        val generateCompletionsKeyByModel = fun(model: IndexedPsiElementModel): String {
            return "PSA_COMPLETIONS:" + model.textRange + ":" + model.model.filePath
        }
        val generateKeyByRequestType = fun(requestType: RequestType, model: IndexedPsiElementModel): String {
            if (requestType === RequestType.GoTo) {
                return generateGoToKeyByModel(model)
            }
            return generateCompletionsKeyByModel(model)
        }
    }

    fun get(key: String): String? {
        if (!this.project.service<Settings>().indexingEnabled) {
            throw IndexingDisabledException()
        }

        val parts = key.split(":")
        if (parts.size > 1) {
            val virtualFile = VirtualFileManager.getInstance().findFileByUrl("file://" + parts[2])
            if (virtualFile != null) {
                val psiFile = PsiManager.getInstance(project).findFile(virtualFile)
                if (psiFile != null) {
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
        }

        return null
    }

    fun getForFile(file: PsiFile): String? {
        return gist.getFileData(file)
    }

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
