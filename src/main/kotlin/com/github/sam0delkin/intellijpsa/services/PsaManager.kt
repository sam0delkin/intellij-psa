@file:Suppress("PLUGIN_IS_NOT_ENABLED", "ktlint:standard:no-wildcard-imports")

package com.github.sam0delkin.intellijpsa.services

import com.github.sam0delkin.intellijpsa.model.*
import com.github.sam0delkin.intellijpsa.model.IndexedPsiElementModel
import com.github.sam0delkin.intellijpsa.settings.*
import com.github.sam0delkin.intellijpsa.util.ExecutionUtils
import com.github.sam0delkin.intellijpsa.util.FileUtils
import com.intellij.execution.configurations.GeneralCommandLine
import com.intellij.execution.process.ProcessOutput
import com.intellij.notification.NotificationGroupManager
import com.intellij.notification.NotificationType
import com.intellij.openapi.application.ex.ApplicationUtil
import com.intellij.openapi.components.Service
import com.intellij.openapi.components.service
import com.intellij.openapi.progress.*
import com.intellij.openapi.progress.Task.Backgroundable
import com.intellij.openapi.project.Project
import com.intellij.openapi.project.guessProjectDir
import com.intellij.psi.*
import com.intellij.psi.util.elementType
import com.jetbrains.rd.util.string.printToString
import kotlinx.coroutines.runBlocking
import kotlinx.serialization.decodeFromString
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import java.io.File
import java.lang.reflect.InvocationTargetException
import java.lang.reflect.Method
import kotlin.NoSuchElementException
import kotlin.collections.ArrayList
import kotlin.collections.HashMap
import kotlin.collections.HashSet
import kotlin.reflect.full.memberFunctions

enum class RequestType {
    Completion,
    BatchCompletion,
    GoTo,
    BatchGoTo,
    Info,
    GenerateFileFromTemplate,
    GetStaticCompletions,
    PerformEditorAction,
}

private const val MAX_STRING_LENGTH = 1000

@Service(Service.Level.PROJECT)
class PsaManager(
    private val project: Project,
) {
    private val baseMethods = PsiElement::class.memberFunctions.map { el -> el.name }
    private val ignoredMethods =
        arrayOf(
            "clone",
            "getPsi",
            "getPrevPsiSibling",
            "getNextPsiSibling",
            "getFirstPsiChild",
            "getTreePrev",
            "getTreeNext",
            "copyElement",
        )
    private val elementIgnoredMethods = HashMap<String, List<Method>>()
    var lastResultSucceed: Boolean = true
    var lastResultMessage: String = ""

    fun getSettings(): Settings = this.project.service()

    fun getInfo(
        settings: Settings,
        project: Project,
        path: String,
        debug: Boolean? = null,
    ): InfoModel {
        val commandLine: GeneralCommandLine?
        var result: ProcessOutput? = null
        val innerDebug = if (null !== debug) debug else settings.debug

        commandLine = GeneralCommandLine(path)
        commandLine.environment["PSA_TYPE"] = RequestType.Info.toString()
        commandLine.environment["PSA_DEBUG"] = if (innerDebug) "1" else "0"
        commandLine.setWorkDirectory(project.guessProjectDir()?.path)
        val indicator = ProgressIndicatorProvider.getGlobalProgressIndicator() ?: EmptyProgressIndicator()

        ApplicationUtil.runWithCheckCanceled(
            {
                result =
                    runBlocking {
                        ExecutionUtils.executeWithIndicatorAndTimeout(
                            commandLine,
                            indicator,
                            settings.executionTimeout,
                        )
                    }
            },
            indicator,
        )

        if (null === result) {
            throw Exception("Failed to get info")
        }

        if (0 != result!!.exitCode) {
            throw Exception(result!!.stdout + "\n" + result!!.stderr)
        }

        return Json.decodeFromString<InfoModel>(result!!.stdout)
    }

    fun getStaticCompletions(
        settings: Settings,
        project: Project,
        path: String,
        debug: Boolean? = null,
        progressIndicator: ProgressIndicator? = null,
    ): StaticCompletionsModel? {
        val commandLine: GeneralCommandLine?
        var result: ProcessOutput?
        val innerDebug = if (null !== debug) debug else settings.debug

        commandLine = GeneralCommandLine(path)
        commandLine.environment["PSA_TYPE"] = RequestType.GetStaticCompletions.toString()
        commandLine.environment["PSA_DEBUG"] = if (innerDebug) "1" else "0"
        commandLine.setWorkDirectory(project.guessProjectDir()?.path)
        val indicator = progressIndicator ?: ProgressIndicatorProvider.getGlobalProgressIndicator() ?: EmptyProgressIndicator()

        try {
            ApplicationUtil.runWithCheckCanceled(
                {
                    result =
                        runBlocking {
                            ExecutionUtils.executeWithIndicatorAndTimeout(
                                commandLine,
                                indicator,
                                settings.executionTimeout,
                            )
                        }
                },
                indicator,
            )

            result =
                ExecutionUtils.executeWithIndicatorAndTimeout(
                    commandLine,
                    indicator,
                    settings.executionTimeout,
                )

            if (result!!.isCancelled) {
                throw ProcessCanceledException()
            }

            if (0 != result!!.exitCode) {
                throw Exception(result!!.stdout + "\n" + result!!.stderr)
            }

            this.lastResultSucceed = true
            this.lastResultMessage = ""

            return Json.decodeFromString<StaticCompletionsModel>(result!!.stdout)
        } catch (e: Throwable) {
            this.lastResultSucceed = false
            this.lastResultMessage = e.message ?: "Unexpected Error"
            if (settings.debug) {
                NotificationGroupManager
                    .getInstance()
                    .getNotificationGroup("PSA Notification")
                    .createNotification(
                        "Failed to update static completions <br/>" + e.message,
                        NotificationType.ERROR,
                    ).notify(project)
            }
        }

        return null
    }

    fun updateStaticCompletions(
        settings: Settings,
        project: Project,
        path: String,
        debug: Boolean? = null,
    ) {
        if (!settings.supportsStaticCompletions) {
            return
        }

        var previousIndicator: ProgressIndicator? = null

        ProgressManager.getInstance().run(
            object : Backgroundable(project, "PSA: Updating static completions ...") {
                override fun run(indicator: ProgressIndicator) {
                    if (previousIndicator != null) {
                        previousIndicator!!.cancel()
                    }

                    previousIndicator = indicator

                    if (indicator.isCanceled) {
                        return
                    }

                    val completions = getStaticCompletions(settings, project, path, debug, indicator)

                    if (indicator.isCanceled) {
                        return
                    }

                    settings.staticCompletionConfigs = completions?.staticCompletions
                }
            },
        )
    }

    fun updateInfo(
        settings: Settings,
        info: InfoModel?,
    ) {
        if (null === info) {
            return
        }

        settings.supportedLanguages = info.supportedLanguages.joinToString(",")

        settings.goToFilter = info.goToElementFilter?.joinToString(",") ?: ""
        settings.supportsBatch = info.supportsBatch ?: false
        settings.supportsStaticCompletions = info.supportsStaticCompletions ?: false
        settings.editorActions = info.editorActions

        settings.singleFileCodeTemplates = ArrayList()
        settings.multipleFileCodeTemplates = ArrayList()

        if (info.templates != null) {
            for (template in info.templates) {
                val templateObject: SingleFileCodeTemplate =
                    if (template.type == TemplateType.SINGLE_FILE) {
                        SingleFileCodeTemplate()
                    } else {
                        MultipleFileCodeTemplate()
                    }
                if (null != template.pathRegex) {
                    templateObject.pathRegex = template.pathRegex
                }

                templateObject.name = template.name
                templateObject.title = template.title

                if (templateObject is MultipleFileCodeTemplate) {
                    if (null !== template.fileCount) {
                        templateObject.fileCount = template.fileCount
                    } else {
                        throw Exception("`templates` must be an array of objects with `file_count` field")
                    }
                }

                templateObject.formFields = ArrayList()

                for (field in template.fields) {
                    val fieldObject = TemplateFormField()
                    fieldObject.options = ArrayList()

                    fieldObject.name = field.name
                    fieldObject.title = field.title
                    fieldObject.type = field.type
                    fieldObject.focused = field.focused

                    for (option in field.options) {
                        fieldObject.options!!.add(option)
                    }

                    templateObject.formFields!!.add(fieldObject)
                }

                if (templateObject is MultipleFileCodeTemplate) {
                    settings.multipleFileCodeTemplates!!.add(templateObject)
                } else {
                    settings.singleFileCodeTemplates!!.add(templateObject)
                }
            }
        }
    }

    fun generateTemplateCode(
        settings: Settings,
        project: Project,
        actionPath: String,
        templateName: String,
        templateType: String,
        originatorFieldName: String?,
        formFields: Map<String, String>,
    ): TemplateDataModel? {
        val commandLine: GeneralCommandLine?
        val result: ProcessOutput?

        try {
            val data =
                Json.encodeToString(
                    GenerateFileFromTemplateData(
                        actionPath,
                        templateType,
                        templateName,
                        originatorFieldName,
                        formFields,
                    ),
                )
            val filePath = FileUtils.writeToTmpFile("psa_tmp", data)
            commandLine = GeneralCommandLine(settings.scriptPath)
            commandLine.environment["PSA_CONTEXT"] = filePath
            commandLine.environment["PSA_TYPE"] = RequestType.GenerateFileFromTemplate.toString()
            commandLine.environment["PSA_DEBUG"] = if (settings.debug) "1" else "0"
            commandLine.setWorkDirectory(project.guessProjectDir()?.path)
            val indicator = ProgressIndicatorProvider.getGlobalProgressIndicator() ?: EmptyProgressIndicator()

            result =
                ExecutionUtils.executeWithIndicatorAndTimeout(
                    commandLine,
                    indicator,
                    settings.executionTimeout,
                )

            if (result.isCancelled) {
                throw ProcessCanceledException()
            }

            if (0 != result.exitCode) {
                throw Exception(result.stdout + "\n" + result.stderr)
            }

            this.lastResultSucceed = true
            this.lastResultMessage = ""

            return Json.decodeFromString<TemplateDataModel>(result.stdout)
        } catch (e: Exception) {
            this.lastResultSucceed = false
            this.lastResultMessage = e.message ?: "Unexpected Error"
            if (settings.debug) {
                NotificationGroupManager
                    .getInstance()
                    .getNotificationGroup("PSA Notification")
                    .createNotification(
                        "Template generation returned not valid JSON<br/>" + e.message,
                        NotificationType.ERROR,
                    ).notify(project)
            }
        }

        return null
    }

    fun performAction(
        settings: Settings,
        project: Project,
        action: EditorActionInputModel,
    ): String? {
        val commandLine: GeneralCommandLine?
        val result: ProcessOutput?

        try {
            val data =
                Json.encodeToString(action)
            val filePath = FileUtils.writeToTmpFile("psa_tmp", data)
            commandLine = GeneralCommandLine(settings.scriptPath)
            commandLine.environment["PSA_CONTEXT"] = filePath
            commandLine.environment["PSA_TYPE"] = RequestType.PerformEditorAction.toString()
            commandLine.environment["PSA_DEBUG"] = if (settings.debug) "1" else "0"
            commandLine.setWorkDirectory(project.guessProjectDir()?.path)
            val indicator = ProgressIndicatorProvider.getGlobalProgressIndicator() ?: EmptyProgressIndicator()

            result =
                ExecutionUtils.executeWithIndicatorAndTimeout(
                    commandLine,
                    indicator,
                    settings.executionTimeout,
                )

            if (result.isCancelled) {
                throw ProcessCanceledException()
            }

            if (0 != result.exitCode) {
                throw Exception(result.stdout + "\n" + result.stderr)
            }

            this.lastResultSucceed = true
            this.lastResultMessage = ""

            return result.stdout
        } catch (e: Exception) {
            this.lastResultSucceed = false
            this.lastResultMessage = e.message ?: "Unexpected Error"
            if (settings.debug) {
                NotificationGroupManager
                    .getInstance()
                    .getNotificationGroup("PSA Notification")
                    .createNotification(
                        "Template generation returned not valid JSON<br/>" + e.message,
                        NotificationType.ERROR,
                    ).notify(project)
            }
        }

        return null
    }

    private fun getCompletionsOutput(
        settings: Settings,
        model: Array<IndexedPsiElementModel>,
        requestType: RequestType,
        language: String,
        editorOffset: Int? = null,
        debug: Boolean? = null,
        fromIndex: Boolean = false,
        indicator: ProgressIndicator? = null,
    ): ProcessOutput? {
        if (!settings.pluginEnabled) {
            return null
        }

        if (!settings.isLanguageSupported(language)) {
            return null
        }
        val data: String
        if (model.size == 1 && requestType !in arrayOf(RequestType.BatchGoTo, RequestType.BatchCompletion)) {
            val firstModel = model[0]
            val file = File(firstModel.model.filePath!!)
            if (
                firstModel.model.filePath?.indexOf("example") != 0 &&
                file.parent.indexOf(settings.getScriptDir()!!) >= 0
            ) {
                return null
            }
            data = Json.encodeToString(firstModel.model)
        } else {
            data = Json.encodeToString(model.map { e -> e.model })
        }
        val filePath = FileUtils.writeToTmpFile("psa_tmp", data)
        val commandLine: GeneralCommandLine?
        var result: ProcessOutput? = null
        val debugValue = if (null !== debug) debug else settings.debug
        val normalizedIndicator = indicator ?: (ProgressIndicatorProvider.getGlobalProgressIndicator() ?: EmptyProgressIndicator())

        commandLine = GeneralCommandLine(settings.scriptPath)
        commandLine.environment["PSA_CONTEXT"] = filePath
        commandLine.environment["PSA_TYPE"] = requestType.toString()
        commandLine.environment["PSA_LANGUAGE"] = language
        commandLine.environment["PSA_OFFSET"] = if (null !== editorOffset) editorOffset.toString() else ""
        commandLine.environment["PSA_DEBUG"] = if (debugValue) "1" else "0"
        commandLine.setWorkDirectory(project.guessProjectDir()?.path)

        if (fromIndex) {
            result =
                ExecutionUtils.executeWithIndicatorAndTimeout(
                    commandLine,
                    normalizedIndicator,
                    settings.executionTimeout,
                )
        } else {
            ApplicationUtil.runWithCheckCanceled(
                {
                    result =
                        runBlocking {
                            ExecutionUtils.executeWithIndicatorAndTimeout(
                                commandLine,
                                normalizedIndicator,
                                settings.executionTimeout,
                            )
                        }
                },
                normalizedIndicator,
            )
        }

        try {
            val fileVal = File(filePath)
            if (fileVal.exists() && fileVal.isFile) {
                fileVal.delete()
            }
        } catch (_: Throwable) {
        }

        return result
    }

    fun getCompletions(
        settings: Settings,
        model: Array<IndexedPsiElementModel>,
        requestType: RequestType,
        language: String,
        editorOffset: Int? = null,
        debug: Boolean? = null,
        fromIndex: Boolean = false,
        indicator: ProgressIndicator? = null,
    ): CompletionsModel? {
        val result =
            getCompletionsOutput(
                settings,
                model,
                requestType,
                language,
                editorOffset,
                debug,
                fromIndex,
                indicator,
            )

        this.lastResultSucceed = true
        this.lastResultMessage = ""

        if (null === result) {
            return null
        }

        if (result.isCancelled) {
            this.lastResultMessage = "Process Execution Cancelled."
            this.lastResultSucceed = false

            return null
        }

        if (0 != result.exitCode) {
            if (result.isTimeout) {
                this.lastResultMessage = "Process Execution Timeout exceeded."
            } else {
                this.lastResultMessage = result.stdout + "\n" + result.stderr
            }
            this.lastResultSucceed = false

            if (settings.debug) {
                NotificationGroupManager
                    .getInstance()
                    .getNotificationGroup("PSA Notification")
                    .createNotification(this.lastResultMessage, NotificationType.ERROR)
                    .notify(project)
            }

            return null
        }

        return Json.decodeFromString<CompletionsModel>(result.stdout)
    }

    fun psiElementToModel(
        element: PsiElement,
        processParent: Boolean = true,
        processOptions: Boolean = true,
        processChildOptions: Boolean = true,
        processNext: Boolean = true,
        processPrev: Boolean = true,
        processedElements: ArrayList<PsiElement>? = null,
    ): PsiElementModel {
        val filePath = if (null === processedElements) element.containingFile.virtualFile.path else null
        val currentProcessedElements = if (null !== processedElements) processedElements else ArrayList()
        val options = mutableMapOf<String, PsiElementModelChild>()
        val elementType = element.elementType.printToString()
        val methods: List<Method>

        if (this.elementIgnoredMethods[elementType] !== null) {
            methods = this.elementIgnoredMethods[elementType]!!
        } else {
            methods =
                element.javaClass.methods.filter { method ->
                    !this.baseMethods.contains(method.name) &&
                        !this.ignoredMethods.contains(method.name) &&
                        method.parameterTypes.isEmpty()
                }
            this.elementIgnoredMethods[elementType] = methods
        }

        var elementName: String? = null
        val elementFqn: String? = null
        try {
            val nameMethod = element.javaClass.methods.first { el -> el.name === "name" }
            elementName = nameMethod.invoke(element) as String
        } catch (_: NoSuchElementException) {
        }

        var elementText = element.text
        if (elementText.length > MAX_STRING_LENGTH) {
            elementText = elementText.substring(0, MAX_STRING_LENGTH)
        }
        var parentElement: PsiElementModel? = null
        var nextElement: PsiElementModel? = null
        var prevElement: PsiElementModel? = null
        val hashCode: String = System.identityHashCode(element).toString()
        val signature: ArrayList<String> = ArrayList()
        val signatureMethods = element.javaClass.methods.filter { m -> m.name === "getSignature" }
        if (signatureMethods.isNotEmpty()) {
            val method = signatureMethods.first()
            var signatureValue =
                try {
                    method.invoke(element) as String
                } catch (e: InvocationTargetException) {
                    ""
                }
            signatureValue = signatureValue.replace(Regex("#."), "")
            signature.addAll(signatureValue.split("|"))
        }

        if (currentProcessedElements.contains(element)) {
            return PsiElementModel(
                hashCode,
                elementType,
                options,
                elementName,
                elementFqn,
                signature,
                elementText,
                null,
                null,
                null,
                null,
            )
        }

        currentProcessedElements.add(element)

        for (method in methods) {
            val interfaces = this.getAllExtendedOrImplementedInterfacesRecursively(method.returnType)
            val componentTypeInterfaces =
                if (method.returnType.componentType !== null) {
                    this.getAllExtendedOrImplementedInterfacesRecursively(
                        method.returnType.componentType,
                    )
                } else {
                    HashSet()
                }
            try {
                if (
                    !method.returnType.isAssignableFrom(String::class.java) &&
                    !method.returnType.isAssignableFrom(Number::class.java) &&
                    !interfaces.any { e -> e.isAssignableFrom(PsiElement::class.java) } &&
                    !componentTypeInterfaces.any { e -> e.isAssignableFrom(PsiElement::class.java) }
                ) {
                    continue
                }

                var result = method.invoke(element)
                var optionName = method.name
                if (0 == optionName.indexOf("get")) {
                    optionName = optionName.substring(3)
                }

                if (result is String) {
                    if (result.length > 250) {
                        result = result.substring(0, 250)
                    }
                    options[optionName] = PsiElementModelChild(null, result)
                } else if (result is PsiElement && processOptions && processChildOptions) {
                    options[optionName] =
                        PsiElementModelChild(
                            this.psiElementToModel(
                                result,
                                processParent = false,
                                processOptions = true,
                                processChildOptions = false,
                                processNext = false,
                                processPrev = false,
                                processedElements = currentProcessedElements,
                            ),
                            null,
                        )
                } else if (result is Array<*> && result.isArrayOf<PsiElement>() && processOptions && processChildOptions) {
                    val arr: Array<PsiElementModel?> = arrayOfNulls(result.size)

                    for ((index, item) in (result).withIndex()) {
                        arr[index] =
                            this.psiElementToModel(
                                item as PsiElement,
                                processParent = false,
                                processOptions = true,
                                processChildOptions = false,
                                processNext = false,
                                processPrev = false,
                                processedElements = currentProcessedElements,
                            )
                    }
                    options[optionName] = PsiElementModelChild(null, null, arr)
                }
            } catch (e: Throwable) {
                continue
            }
        }

        if (element.parent !== null && processParent) {
            parentElement =
                this.psiElementToModel(
                    element.parent,
                    processParent = true,
                    processOptions = true,
                    processChildOptions = true,
                    processNext = true,
                    processPrev = true,
                    processedElements = currentProcessedElements,
                )
        }
        if (processNext && element.nextSibling !== null) {
            nextElement =
                this.psiElementToModel(
                    element.nextSibling,
                    processParent = false,
                    processOptions = true,
                    processChildOptions = true,
                    processNext = true,
                    processPrev = false,
                    processedElements = currentProcessedElements,
                )
        }
        if (processPrev && element.prevSibling !== null) {
            prevElement =
                this.psiElementToModel(
                    element.prevSibling,
                    processParent = false,
                    processOptions = true,
                    processChildOptions = true,
                    processNext = false,
                    processPrev = true,
                    processedElements = currentProcessedElements,
                )
        }

        return PsiElementModel(
            hashCode,
            elementType,
            options,
            elementName,
            elementFqn,
            signature,
            elementText,
            parentElement,
            prevElement,
            nextElement,
            if (null !== element.textRange) {
                PsiElementModelTextRange(
                    element.textRange.startOffset,
                    element.textRange.endOffset,
                )
            } else {
                null
            },
            filePath,
        )
    }

    private fun getAllExtendedOrImplementedInterfacesRecursively(clazz: Class<*>): Set<Class<*>> {
        val res: MutableSet<Class<*>> = HashSet()
        val interfaces = clazz.interfaces
        if (interfaces.isNotEmpty()) {
            res.addAll(interfaces)
            for (interfaze in interfaces) {
                res.addAll(getAllExtendedOrImplementedInterfacesRecursively(interfaze))
            }
        }
        return res
    }
}
