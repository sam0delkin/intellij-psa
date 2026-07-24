@file:Suppress("PLUGIN_IS_NOT_ENABLED", "ktlint:standard:no-wildcard-imports")

package com.github.sam0delkin.intellijpsa.services

import com.github.sam0delkin.intellijpsa.index.INDEX_ID
import com.github.sam0delkin.intellijpsa.model.*
import com.github.sam0delkin.intellijpsa.model.action.EditorActionInputModel
import com.github.sam0delkin.intellijpsa.model.completion.CompletionsModel
import com.github.sam0delkin.intellijpsa.model.psi.IndexedPsiElementModel
import com.github.sam0delkin.intellijpsa.model.psi.PsiElementModel
import com.github.sam0delkin.intellijpsa.model.psi.PsiElementModelChild
import com.github.sam0delkin.intellijpsa.model.psi.PsiElementModelTextRange
import com.github.sam0delkin.intellijpsa.model.template.GenerateFileFromTemplateData
import com.github.sam0delkin.intellijpsa.model.template.TemplateDataModel
import com.github.sam0delkin.intellijpsa.services.server.ServerManager
import com.github.sam0delkin.intellijpsa.settings.*
import com.github.sam0delkin.intellijpsa.util.ExecutionUtils
import com.github.sam0delkin.intellijpsa.util.FileUtils
import com.intellij.execution.process.ProcessOutput
import com.intellij.notification.NotificationGroupManager
import com.intellij.notification.NotificationType
import com.intellij.openapi.application.ex.ApplicationUtil
import com.intellij.openapi.application.runReadAction
import com.intellij.openapi.components.Service
import com.intellij.openapi.components.service
import com.intellij.openapi.progress.*
import com.intellij.openapi.progress.Task.Backgroundable
import com.intellij.openapi.project.Project
import com.intellij.psi.*
import com.intellij.psi.util.elementType
import com.intellij.util.indexing.FileBasedIndex
import com.jetbrains.rd.util.string.printToString
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.decodeFromJsonElement
import kotlinx.serialization.json.encodeToJsonElement
import org.apache.velocity.app.Velocity
import org.apache.velocity.util.introspection.UberspectImpl
import java.io.File
import java.lang.reflect.InvocationTargetException
import java.lang.reflect.Method
import java.security.MessageDigest
import java.util.Properties
import kotlin.collections.ArrayList
import kotlin.collections.HashMap
import kotlin.collections.HashSet
import kotlin.reflect.full.memberFunctions

private const val MAX_STRING_LENGTH = 1000

@Service(Service.Level.PROJECT)
class PsaManager(
    private val project: Project,
) {
    private val baseMethods: Set<String> = PsiElement::class.memberFunctions.map { it.name }.toHashSet()

    private val ignoredMethods =
        setOf(
            "clone",
            "getPsi",
            "getPrevPsiSibling",
            "getNextPsiSibling",
            "getFirstPsiChild",
            "getTreePrev",
            "getTreeNext",
            "copyElement",
            "getUserDataString",
        )

    private val interfaceCache = HashMap<Class<*>, Set<Class<*>>>()

    private val elementEligibleMethods = HashMap<String, List<Method>>()
    var lastResultSucceed: Boolean = true
    var lastResultMessage: String = ""

    init {
        try {
            val properties = Properties()
            properties.setProperty(
                "introspector.uberspect.class",
                UberspectImpl::class.java.name,
            )
            Velocity.init(properties)
        } catch (_: Throwable) {
            // Ignore in tests or if already initialized
        }
    }

    fun getSettings(): Settings = this.project.service()

    fun getInfo(
        settings: Settings,
        project: Project,
        debug: Boolean? = null,
    ): InfoModel {
        val innerDebug = if (null !== debug) debug else settings.debug

        val stdout: String =
            if (settings.isServerModeActive()) {
                val response =
                    project.service<ServerManager>().sendRequest(
                        settings,
                        RequestType.Info.toString(),
                        null,
                        innerDebug,
                        null,
                        null,
                    )

                if (null !== response.error) {
                    throw Exception(response.error)
                }

                response.result?.toString() ?: throw Exception("Failed to get info")
            } else {
                var result: ProcessOutput? = null
                val commandLine = ExecutionUtils.getCommandLine(settings, project)
                commandLine.environment["PSA_TYPE"] = RequestType.Info.toString()
                commandLine.environment["PSA_DEBUG"] = if (innerDebug) "1" else "0"
                ExecutionUtils.setWorkDirectoryIfExists(commandLine, project)
                val indicator = ProgressIndicatorProvider.getGlobalProgressIndicator() ?: EmptyProgressIndicator()

                ApplicationUtil.runWithCheckCanceled(
                    {
                        result =
                            ExecutionUtils.executeWithIndicatorAndTimeout(
                                commandLine,
                                indicator,
                                settings.executionTimeout,
                            )
                    },
                    indicator,
                )

                if (null === result) {
                    throw Exception("Failed to get info")
                }

                if (0 != result.exitCode) {
                    throw Exception(result.stdout + "\n" + result.stderr)
                }

                result.stdout
            }

        for (extension in EP_NAME.extensionList) {
            extension.updateInfo(project, stdout)
        }

        val json =
            Json {
                ignoreUnknownKeys = true
            }

        return json.decodeFromString<InfoModel>(stdout)
    }

    fun getStaticCompletions(
        settings: Settings,
        project: Project,
        debug: Boolean? = null,
        progressIndicator: ProgressIndicator? = null,
    ): StaticCompletionsModel? {
        val innerDebug = if (null !== debug) debug else settings.debug
        val indicator = progressIndicator ?: ProgressIndicatorProvider.getGlobalProgressIndicator() ?: EmptyProgressIndicator()

        try {
            val stdout: String =
                if (settings.isServerModeActive()) {
                    val response =
                        project.service<ServerManager>().sendRequest(
                            settings,
                            RequestType.GetStaticCompletions.toString(),
                            null,
                            innerDebug,
                            null,
                            null,
                        )

                    if (null !== response.error) {
                        throw Exception(response.error)
                    }

                    response.result?.toString() ?: throw Exception("Failed to get static completions")
                } else {
                    var result: ProcessOutput? = null
                    val commandLine = ExecutionUtils.getCommandLine(settings, project)
                    commandLine.environment["PSA_TYPE"] = RequestType.GetStaticCompletions.toString()
                    commandLine.environment["PSA_DEBUG"] = if (innerDebug) "1" else "0"
                    ExecutionUtils.setWorkDirectoryIfExists(commandLine, project)

                    ApplicationUtil.runWithCheckCanceled(
                        {
                            result =
                                ExecutionUtils.executeWithIndicatorAndTimeout(
                                    commandLine,
                                    indicator,
                                    settings.executionTimeout,
                                )
                        },
                        indicator,
                    )

                    if (null === result) {
                        throw Exception("Failed to get static completions")
                    }

                    if (result.isCancelled) {
                        throw ProcessCanceledException()
                    }

                    if (0 != result.exitCode) {
                        throw Exception(result.stdout + "\n" + result.stderr)
                    }

                    result.stdout
                }

            this.lastResultSucceed = true
            this.lastResultMessage = ""

            return runReadAction {
                val json = Json.decodeFromString<StaticCompletionsModel>(stdout)

                return@runReadAction json
            }
        } catch (e: Throwable) {
            this.lastResultSucceed = false
            this.lastResultMessage = e.message ?: "Unexpected Error"
            if (settings.debug || settings.showErrors) {
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
        debug: Boolean? = null,
    ) {
        if (!settings.supportsStaticCompletions) {
            return
        }

        if (null == settings.scriptPath) {
            return
        }

        val previousIndicator: ProgressIndicator? = null

        ProgressManager.getInstance().run(
            object : Backgroundable(project, "PSA: Updating static completions ...") {
                override fun run(indicator: ProgressIndicator) {
                    previousIndicator?.cancel()

                    if (indicator.isCanceled) {
                        return
                    }

                    val completions = getStaticCompletions(settings, project, debug, indicator)

                    if (indicator.isCanceled) {
                        return
                    }

                    setStaticCompletionConfigs(completions?.staticCompletions)

                    if (settings.resolveReferences) {
                        val oldHash = settings.staticCompletionsHash
                        updateStaticCompletionsHash(settings)

                        if (oldHash != settings.staticCompletionsHash) {
                            settings.targetElementTypes = arrayListOf()
                            FileBasedIndex.getInstance().requestRebuild(INDEX_ID)
                        }
                    }
                }
            },
        )
    }

    fun updateStaticCompletionsHash(settings: Settings) {
        val md = MessageDigest.getInstance("MD5")
        val digest =
            md.digest(Json.encodeToString(this.getStaticCompletionConfigs()).toByteArray())
        settings.staticCompletionsHash = digest.joinToString("") { "%02x".format(it) }
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
        settings.editorActions =
            info.editorActions?.mapTo(ArrayList()) { a ->
                PersistedEditorAction().apply {
                    name = a.name
                    title = a.title
                    groupName = a.groupName
                    pathRegex = a.pathRegex
                    source = a.source
                    target = a.target
                    contextAction = a.contextAction
                    contextual = a.contextual
                }
            }

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
        try {
            val templateData =
                GenerateFileFromTemplateData(
                    actionPath,
                    templateType,
                    templateName,
                    originatorFieldName,
                    formFields,
                )

            val stdout: String =
                if (settings.isServerModeActive()) {
                    val response =
                        project.service<ServerManager>().sendRequest(
                            settings,
                            RequestType.GenerateFileFromTemplate.toString(),
                            null,
                            settings.debug,
                            null,
                            Json.encodeToJsonElement(templateData),
                        )

                    if (null !== response.error) {
                        throw Exception(response.error)
                    }

                    response.result?.toString() ?: throw Exception("Failed to generate template code")
                } else {
                    val data = Json.encodeToString(templateData)
                    val filePath = FileUtils.writeToTmpFile("psa_tmp", data)
                    val commandLine = ExecutionUtils.getCommandLine(settings, project)
                    commandLine.environment["PSA_CONTEXT"] = filePath
                    commandLine.environment["PSA_TYPE"] = RequestType.GenerateFileFromTemplate.toString()
                    commandLine.environment["PSA_DEBUG"] = if (settings.debug) "1" else "0"
                    ExecutionUtils.setWorkDirectoryIfExists(commandLine, project)
                    val indicator = ProgressIndicatorProvider.getGlobalProgressIndicator() ?: EmptyProgressIndicator()

                    val result =
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

                    result.stdout
                }

            this.lastResultSucceed = true
            this.lastResultMessage = ""

            return Json.decodeFromString<TemplateDataModel>(stdout)
        } catch (e: Exception) {
            this.lastResultSucceed = false
            this.lastResultMessage = e.message ?: "Unexpected Error"
            if (settings.debug || settings.showErrors) {
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
        try {
            val stdout: String =
                if (settings.isServerModeActive()) {
                    val response =
                        project.service<ServerManager>().sendRequest(
                            settings,
                            RequestType.PerformEditorAction.toString(),
                            null,
                            settings.debug,
                            null,
                            Json.encodeToJsonElement(action),
                        )

                    if (null !== response.error) {
                        throw Exception(response.error)
                    }

                    val resultElement = response.result ?: throw Exception("Failed to perform action")

                    Json.decodeFromJsonElement<String>(resultElement)
                } else {
                    val data = Json.encodeToString(action)
                    val filePath = FileUtils.writeToTmpFile("psa_tmp", data)
                    val commandLine = ExecutionUtils.getCommandLine(settings, project)
                    commandLine.environment["PSA_CONTEXT"] = filePath
                    commandLine.environment["PSA_TYPE"] = RequestType.PerformEditorAction.toString()
                    commandLine.environment["PSA_DEBUG"] = if (settings.debug) "1" else "0"
                    ExecutionUtils.setWorkDirectoryIfExists(commandLine, project)
                    val indicator = ProgressIndicatorProvider.getGlobalProgressIndicator() ?: EmptyProgressIndicator()

                    val result =
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

                    result.stdout
                }

            this.lastResultSucceed = true
            this.lastResultMessage = ""

            return stdout
        } catch (e: Exception) {
            this.lastResultSucceed = false
            this.lastResultMessage = e.message ?: "Unexpected Error"
            if (settings.debug || settings.showErrors) {
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

    private data class CompletionsRawResult(
        val stdout: String? = null,
        val errorMessage: String? = null,
        val cancelled: Boolean = false,
        val timedOut: Boolean = false,
    )

    private fun getCompletionsOutput(
        settings: Settings,
        model: Array<IndexedPsiElementModel>,
        requestType: RequestType,
        language: String,
        editorOffset: Int? = null,
    ): CompletionsRawResult? {
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
                file.parent.indexOf(settings.getScriptDir()!!) >= 0
            ) {
                return null
            }
            data = Json.encodeToString(firstModel.model)
        } else {
            data = Json.encodeToString(model.map { e -> e.model })
        }

        if (settings.isServerModeActive()) {
            val response =
                project.service<ServerManager>().sendRequest(
                    settings,
                    requestType.toString(),
                    language,
                    settings.debug,
                    editorOffset,
                    Json.parseToJsonElement(data),
                )

            return CompletionsRawResult(
                stdout = response.result?.toString(),
                errorMessage = response.error,
            )
        }

        val filePath = FileUtils.writeToTmpFile("psa_tmp", data)
        var result: ProcessOutput? = null
        val normalizedIndicator = ProgressIndicatorProvider.getGlobalProgressIndicator() ?: EmptyProgressIndicator()

        val commandLine = ExecutionUtils.getCommandLine(settings, project)
        commandLine.environment["PSA_CONTEXT"] = filePath
        commandLine.environment["PSA_TYPE"] = requestType.toString()
        commandLine.environment["PSA_LANGUAGE"] = language
        commandLine.environment["PSA_OFFSET"] = if (null !== editorOffset) editorOffset.toString() else ""
        commandLine.environment["PSA_DEBUG"] = if (settings.debug) "1" else "0"
        ExecutionUtils.setWorkDirectoryIfExists(commandLine, project)

        ApplicationUtil.runWithCheckCanceled(
            {
                result =
                    ExecutionUtils.executeWithIndicatorAndTimeout(
                        commandLine,
                        normalizedIndicator,
                        settings.executionTimeout,
                    )
            },
            normalizedIndicator,
        )

        try {
            val fileVal = File(filePath)
            if (fileVal.exists() && fileVal.isFile) {
                fileVal.delete()
            }
        } catch (_: Throwable) {
        }

        val finalResult = result

        return CompletionsRawResult(
            stdout = finalResult?.stdout,
            cancelled = finalResult?.isCancelled == true,
            timedOut = finalResult?.isTimeout == true,
            errorMessage =
                if (finalResult != null && 0 != finalResult.exitCode && !finalResult.isTimeout) {
                    finalResult.stdout + "\n" + finalResult.stderr
                } else {
                    null
                },
        )
    }

    fun getCompletions(
        settings: Settings,
        model: Array<IndexedPsiElementModel>,
        requestType: RequestType,
        language: String,
        editorOffset: Int? = null,
    ): ExtendedCompletionsModel? {
        val result =
            getCompletionsOutput(
                settings,
                model,
                requestType,
                language,
                editorOffset,
            )

        this.lastResultSucceed = true
        this.lastResultMessage = ""

        if (null === result) {
            return null
        }

        if (result.cancelled) {
            this.lastResultMessage = "Process Execution Cancelled."
            this.lastResultSucceed = false

            return null
        }

        if (result.timedOut) {
            this.lastResultMessage = "Process Execution Timeout exceeded."
            this.lastResultSucceed = false

            if (settings.debug || settings.showErrors) {
                NotificationGroupManager
                    .getInstance()
                    .getNotificationGroup("PSA Notification")
                    .createNotification(this.lastResultMessage, NotificationType.ERROR)
                    .notify(project)
            }

            return null
        }

        if (null !== result.errorMessage) {
            this.lastResultMessage = result.errorMessage
            this.lastResultSucceed = false

            if (settings.debug || settings.showErrors) {
                NotificationGroupManager
                    .getInstance()
                    .getNotificationGroup("PSA Notification")
                    .createNotification(this.lastResultMessage, NotificationType.ERROR)
                    .notify(project)
            }

            return null
        }

        val stdout = result.stdout

        if (null === stdout) {
            this.lastResultMessage = "No result received"
            this.lastResultSucceed = false

            return null
        }

        val completions = Json.decodeFromString<CompletionsModel>(stdout)

        return ExtendedCompletionsModel.createFromModel(completions, project)
    }

    fun psiElementToModel(
        element: PsiElement,
        processParent: Boolean = true,
        processOptions: Boolean = true,
        processChildOptions: Boolean = true,
        processNext: Boolean = true,
        processPrev: Boolean = true,
        fromOption: Boolean = false,
        processedElements: HashSet<PsiElement>? = null,
        nestingLevel: Int = 0,
    ): PsiElementModel {
        val filePath = if (null === processedElements) element.containingFile.virtualFile.path else null
        val currentProcessedElements = processedElements ?: HashSet()
        val options = mutableMapOf<String, PsiElementModelChild>()
        val elementType = element.elementType.printToString()
        val methods: List<Method>

        if (!processOptions) {
            methods = emptyList()
        } else {
            methods =
                this.elementEligibleMethods.getOrPut(elementType) {
                    element.javaClass.methods.filter { method ->
                        !this.baseMethods.contains(method.name) &&
                            method.name !in this.ignoredMethods &&
                            method.parameterTypes.isEmpty() &&
                            isEligibleReturnType(method.returnType)
                    }
                }
        }

        val elementFqn: String? = null

        var elementText = element.text
        if (elementText.length > MAX_STRING_LENGTH) {
            elementText = elementText.substring(0, MAX_STRING_LENGTH)
        }
        var parentElement: PsiElementModel? = null
        var nextElement: PsiElementModel? = null
        var prevElement: PsiElementModel? = null
        val hashCode: String = System.identityHashCode(element).toString()
        val signature: ArrayList<String> = ArrayList()

        if (processOptions) {
            val signatureMethods = element.javaClass.methods.filter { m -> m.name === "getSignature" }
            if (signatureMethods.isNotEmpty()) {
                val method = signatureMethods.first()
                var signatureValue =
                    try {
                        method.invoke(element) as String
                    } catch (_: InvocationTargetException) {
                        ""
                    }
                signatureValue = signatureValue.replace(Regex("#."), "")
                signature.addAll(signatureValue.split("|"))
            }
        }

        if (currentProcessedElements.contains(element)) {
            return PsiElementModel(
                hashCode,
                elementType,
                options,
                "",
                elementFqn,
                signature,
                elementText,
                null,
                null,
                null,
                null,
            )
        }

        if (!fromOption) {
            currentProcessedElements.add(element)
        }

        if (nestingLevel > this.getSettings().maxNestingLevel) {
            return PsiElementModel(
                hashCode,
                elementType,
                options,
                "",
                elementFqn,
                signature,
                elementText,
                null,
                null,
                null,
                null,
            )
        }

        for (method in methods) {
            try {
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
                } else if (result is PsiElement && processChildOptions) {
                    options[optionName] =
                        PsiElementModelChild(
                            this.psiElementToModel(
                                result,
                                processParent = false,
                                processOptions = true,
                                processChildOptions = false,
                                processNext = false,
                                processPrev = false,
                                fromOption = true,
                                processedElements = currentProcessedElements,
                                nestingLevel = nestingLevel + 1,
                            ),
                            null,
                        )
                } else if (result is Array<*> && result.isArrayOf<PsiElement>() && processChildOptions) {
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
                                fromOption = true,
                                processedElements = currentProcessedElements,
                                nestingLevel = nestingLevel + 1,
                            )
                    }
                    options[optionName] = PsiElementModelChild(null, null, arr)
                }
            } catch (_: Throwable) {
                continue
            }
        }

        if (processParent) {
            val parent = element.parent

            if (null !== parent && element.parent !is PsiFile) {
                parentElement =
                    this.psiElementToModel(
                        parent,
                        processParent = true,
                        processOptions = processOptions,
                        processChildOptions = processChildOptions,
                        processNext = true,
                        processPrev = true,
                        fromOption = fromOption,
                        processedElements = currentProcessedElements,
                        nestingLevel = nestingLevel + 1,
                    )
            }
        }
        if (processNext) {
            val nextSibling = element.nextSibling

            if (null !== nextSibling) {
                nextElement =
                    this.psiElementToModel(
                        nextSibling,
                        processParent = false,
                        processOptions = processOptions,
                        processChildOptions = processChildOptions,
                        processNext = true,
                        processPrev = false,
                        fromOption = fromOption,
                        processedElements = currentProcessedElements,
                        nestingLevel = nestingLevel + 1,
                    )
            }
        }
        if (processPrev) {
            val prevSibling = element.prevSibling

            if (null !== prevSibling) {
                prevElement =
                    this.psiElementToModel(
                        prevSibling,
                        processParent = false,
                        processOptions = processOptions,
                        processChildOptions = processChildOptions,
                        processNext = false,
                        processPrev = true,
                        fromOption = fromOption,
                        processedElements = currentProcessedElements,
                        nestingLevel = nestingLevel + 1,
                    )
            }
        }

        return PsiElementModel(
            hashCode,
            elementType,
            options,
            "",
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

    fun getStaticCompletionConfigs(): MutableList<ExtendedStaticCompletionModel> =
        this.project
            .service<PsaStaticCompletionsConfig>()
            .getExtendedStaticCompletionConfigs(project)

    fun setStaticCompletionConfigs(configs: MutableList<StaticCompletionModel>?) {
        this.project
            .service<PsaStaticCompletionsConfig>()
            .updateStaticCompletionConfigs(configs)
    }

    private fun collectInterfaces(clazz: Class<*>): Set<Class<*>> =
        interfaceCache.getOrPut(clazz) {
            val res = HashSet<Class<*>>()
            for (iface in clazz.interfaces) {
                res.add(iface)
                res.addAll(collectInterfaces(iface))
            }
            res
        }

    private fun isEligibleReturnType(returnType: Class<*>): Boolean {
        if (returnType.isAssignableFrom(String::class.java) ||
            returnType.isAssignableFrom(Number::class.java) ||
            returnType.isAssignableFrom(PsiElement::class.java)
        ) {
            return true
        }

        if (collectInterfaces(returnType).any { it.isAssignableFrom(PsiElement::class.java) }) return true

        val component = returnType.componentType
        if (component != null &&
            collectInterfaces(component).any { it.isAssignableFrom(PsiElement::class.java) }
        ) {
            return true
        }

        return false
    }
}
