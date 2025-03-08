package com.github.sam0delkin.intellijpsa.index

import com.github.sam0delkin.intellijpsa.services.CompletionService
import com.github.sam0delkin.intellijpsa.services.RequestType
import com.intellij.openapi.application.ex.ApplicationUtil
import com.intellij.openapi.application.runReadAction
import com.intellij.openapi.components.service
import com.intellij.openapi.progress.ProcessCanceledException
import com.intellij.openapi.progress.ProgressIndicator
import com.intellij.openapi.progress.ProgressManager
import com.intellij.openapi.progress.Task.Backgroundable
import com.intellij.openapi.project.DumbService
import com.intellij.openapi.util.Key
import com.intellij.psi.PsiElement
import com.intellij.psi.PsiFile
import com.intellij.psi.util.PsiTreeUtil
import com.jetbrains.rd.util.string.printToString
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import java.util.concurrent.ExecutorService
import java.util.concurrent.Executors
import java.util.concurrent.TimeUnit
import java.util.concurrent.locks.ReentrantLock

class PsaFileIndex {
    companion object {
        private val psaFileDataKey = Key<String>("PSA_FILE_DATA_KEY")
        private val psaCurrentFileDataKey = Key<String>("PSA_FILE_DATA_KEY")
        private val psaFileReindexKey = Key<Boolean>("PSA_FILE_REINDEX_KEY")
        private val psaFileForceReindexKey = Key<Boolean>("PSA_FILE_FORCE_REINDEX_KEY")
        private val psaFileIndicatorKey = Key<ProgressIndicator>("PSA_FILE_INDICATOR_KEY")
        val psaGoToElementKey = Key<String>("PSA_GOTO_ELEMENT_KEY")
        val psaCompletionElementKey = Key<String>("PSA_COMPLETION_ELEMENT_KEY")
        private val mainLock: ReentrantLock = ReentrantLock()

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

        fun reindexFile(
            file: PsiFile,
            force: Boolean = false,
        ) {
            file.putUserData(psaFileReindexKey, true)
            if (force) {
                file.putUserData(psaFileForceReindexKey, true)
            }

            getForFile(file)
        }

        fun getForFile(file: PsiFile): String {
//            val previousIndicator = file.getUserData(psaFileIndicatorKey)
            var reindex = false
            var forceReindex = false
            if (null !== file.getUserData(psaFileReindexKey)) {
                reindex = file.getUserData(psaFileReindexKey)!!
            }
            if (null !== file.getUserData(psaFileForceReindexKey)) {
                forceReindex = file.getUserData(psaFileForceReindexKey)!!
            }

            val currentData = file.getUserData(psaCurrentFileDataKey)
            if (currentData != null && !reindex && !forceReindex) {
                return currentData
            }

            val existingData = file.getUserData(psaFileDataKey)
            if (existingData != null) {
                file.putUserData(psaFileDataKey, null)
                file.putUserData(psaFileForceReindexKey, false)
                file.putUserData(psaFileReindexKey, false)
                file.putUserData(psaCurrentFileDataKey, existingData)

                return existingData
            }
            val project = file.project
            val progressManager = ProgressManager.getInstance()
            val completionService = project.service<CompletionService>()
            val settings = completionService.getSettings()

            if (!settings.pluginEnabled || !settings.indexingEnabled || settings.debug) {
                return "{}"
            }

            if (DumbService.isDumb(project)) {
                DumbService.getInstance(project).runWhenSmart {
                    reindexFile(file)
                }

                return "null"
            }
            val elements = ArrayList<String>()

            val processor =
                PsiFileProcessor(completionService, elements)

            PsiTreeUtil.processElements(file, processor)

            if (elements.size > settings.indexingMaxElements) {
                return "null"
            }

            progressManager.run(
                object : Backgroundable(project, "PSA: Indexing File " + file.name + "...") {
                    override fun run(indicator: ProgressIndicator) {
                        file.putUserData(psaFileIndicatorKey, indicator)
                        try {
                            mainLock.lock()
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

                                                    if (!forceReindex && innerElement!!.getUserData(psaGoToElementKey) != null) {
                                                        map[
                                                            generateGoToKeyByElement(
                                                                innerElement!!,
                                                            ),
                                                        ] = innerElement!!.getUserData(psaGoToElementKey).toString()
                                                        map[
                                                            generateCompletionsKeyElement(
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
                                            batchCurrent += batchCount

                                            if (batchCurrent >= elements.size) {
                                                break
                                            }

                                            continue
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
                                                            debug = false,
                                                            useIndex = false,
                                                            fromIndex = true,
                                                            indicator = indicator,
                                                        )

                                                if (null === goToCompletions) {
                                                    return@submit
                                                }

                                                for (i in 0 until currentElementModels.size) {
                                                    val model = currentElementModels[i]
                                                    var innerElement: PsiElement? = null
                                                    runReadAction {
                                                        innerElement = file.findElementAt(model.model.textRange!!.startOffset)
                                                    }
                                                    if (null === innerElement) {
                                                        continue
                                                    }

                                                    if (null !== goToCompletions.completions &&
                                                        goToCompletions.completions.isNotEmpty()
                                                    ) {
                                                        val encodeToString = Json.encodeToString(goToCompletions)
                                                        map[
                                                            generateGoToKeyByModel(
                                                                model,
                                                            ),
                                                        ] = encodeToString
                                                        innerElement!!.putUserData(psaGoToElementKey, encodeToString)
                                                    } else {
                                                        map[
                                                            generateGoToKeyByModel(
                                                                model,
                                                            ),
                                                        ] = "{}"
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
                                                        debug = false,
                                                        useIndex = false,
                                                        fromIndex = true,
                                                        indicator = indicator,
                                                    )

                                                if (null == completions) {
                                                    return@submit
                                                }

                                                for (i in 0 until currentElementModels.size) {
                                                    val model = currentElementModels[i]
                                                    var innerElement: PsiElement? = null
                                                    runReadAction {
                                                        innerElement = file.findElementAt(model.model.textRange!!.startOffset)
                                                    }

                                                    if (null === innerElement) {
                                                        continue
                                                    }

                                                    if (null !== completions.completions &&
                                                        completions.completions.isNotEmpty()
                                                    ) {
                                                        val encodeToString = Json.encodeToString(completions)
                                                        map[
                                                            generateCompletionsKeyByModel(
                                                                model,
                                                            ),
                                                        ] = encodeToString
                                                        innerElement!!.putUserData(psaCompletionElementKey, encodeToString)
                                                    } else {
                                                        map[
                                                            generateCompletionsKeyByModel(
                                                                model,
                                                            ),
                                                        ] = "{}"
                                                        innerElement!!.putUserData(psaCompletionElementKey, "{}")
                                                    }

                                                    if (indicator.isCanceled) {
                                                        modelsWorkerPool.shutdownNow()
                                                        workerPool.shutdownNow()
                                                        return@submit
                                                    }
                                                }
                                                processed += batchCount
                                                indicator.fraction = processed.toDouble() / elements.size

                                                if (indicator.isCanceled) {
                                                    modelsWorkerPool.shutdownNow()
                                                    workerPool.shutdownNow()
                                                    return@submit
                                                }
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
                                                                debug = false,
                                                                useIndex = false,
                                                                fromIndex = true,
                                                                indicator = indicator,
                                                            )

                                                    if (null === goToCompletions) {
                                                        return@submit
                                                    }

                                                    var innerElement: PsiElement? = null
                                                    runReadAction {
                                                        innerElement = file.findElementAt(model.model.textRange!!.startOffset)
                                                    }

                                                    if (null !== innerElement) {
                                                        if (null !== goToCompletions.completions &&
                                                            goToCompletions.completions.isNotEmpty()
                                                        ) {
                                                            val encodeToString = Json.encodeToString(goToCompletions)
                                                            map[
                                                                generateGoToKeyByModel(
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
                                                                debug = false,
                                                                useIndex = false,
                                                                fromIndex = true,
                                                                indicator = indicator,
                                                            )

                                                    if (null === completions) {
                                                        return@submit
                                                    }

                                                    if (null !== innerElement) {
                                                        if (null !== completions.completions &&
                                                            completions.completions.isNotEmpty()
                                                        ) {
                                                            val encodeToString = Json.encodeToString(completions)
                                                            map[
                                                                generateGoToKeyByModel(
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
                                        reindexFile(file)
                                    }
                                },
                                indicator,
                            )
                        } catch (_: ProcessCanceledException) {
                        } finally {
                            mainLock.unlock()
                        }
                    }
                },
            )

            return "null"
        }

        fun getForElement(
            psiElement: PsiElement,
            type: RequestType,
        ): String? {
            var data = psiElement.getUserData(psaGoToElementKey)
            if (null !== data && type === RequestType.GoTo) {
                return data
            }

            data = psiElement.getUserData(psaCompletionElementKey)
            if (null !== data && type === RequestType.Completion) {
                return data
            }

            return null
        }
    }
}
