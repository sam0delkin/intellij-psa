@file:Suppress("PLUGIN_IS_NOT_ENABLED")

package com.github.sam0delkin.intellijpsa.services

import com.github.sam0delkin.intellijpsa.index.IndexedPsiElementModel
import com.github.sam0delkin.intellijpsa.index.PsaIndex
import com.github.sam0delkin.intellijpsa.settings.Settings
import com.github.sam0delkin.intellijpsa.settings.SingleFileCodeTemplate
import com.github.sam0delkin.intellijpsa.settings.TemplateFormField
import com.github.sam0delkin.intellijpsa.settings.TemplateFormFieldType
import com.github.sam0delkin.intellijpsa.util.FileUtils
import com.intellij.execution.configurations.GeneralCommandLine
import com.intellij.execution.process.ProcessOutput
import com.intellij.execution.util.ExecUtil
import com.intellij.notification.NotificationGroupManager
import com.intellij.notification.NotificationType
import com.intellij.openapi.application.ex.ApplicationUtil
import com.intellij.openapi.components.service
import com.intellij.openapi.progress.EmptyProgressIndicator
import com.intellij.openapi.progress.ProgressIndicatorProvider
import com.intellij.openapi.project.Project
import com.intellij.openapi.project.guessProjectDir
import com.intellij.openapi.vfs.VirtualFile
import com.intellij.psi.*
import com.intellij.psi.util.elementType
import com.jetbrains.rd.util.string.printToString
import kotlinx.coroutines.runBlocking
import kotlinx.serialization.Contextual
import kotlinx.serialization.Serializable
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.*
import java.io.File
import java.lang.reflect.InvocationTargetException
import java.lang.reflect.Method
import java.util.*
import kotlin.NoSuchElementException
import kotlin.collections.ArrayList
import kotlin.collections.HashMap
import kotlin.collections.HashSet
import kotlin.reflect.full.memberFunctions
import com.github.sam0delkin.intellijpsa.exception.Exception.*

@Serializable
data class GenerateFileFromTemplateData(
    val actionPath: String,
    val templateName: String,
    val originatorFieldName: String?,
    val formFields: Map<String, String>
)

@Serializable
data class PsiElementModelChild(
    val model: PsiElementModel? = null,
    var string: String? = null,
    var array: Array<PsiElementModel?>? = null
) {
    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (javaClass != other?.javaClass) return false

        other as PsiElementModelChild

        if (model != other.model) return false
        if (string != other.string) return false
        if (array != null) {
            if (other.array == null) return false
            if (!array.contentEquals(other.array)) return false
        } else if (other.array != null) return false

        return true
    }

    override fun hashCode(): Int {
        var result = model?.hashCode() ?: 0
        result = 31 * result + (string?.hashCode() ?: 0)
        result = 31 * result + (array?.contentHashCode() ?: 0)
        return result
    }
}

@Serializable
data class PsiElementModelTextRange(var startOffset: Int, var endOffset: Int)

@Serializable()
data class PsiElementModel(
    val id: String,
    var elementType: String,
    var options: MutableMap<String, PsiElementModelChild>,
    var elementName: String?,
    var elementFqn: String?,
    var elementSignature: Array<String>?,
    var text: String?,
    var parent: PsiElementModel?,
    var prev: PsiElementModel?,
    var next: PsiElementModel?,
    @Contextual
    var textRange: PsiElementModelTextRange?,
    var filePath: String? = null
)

enum class RequestType {
    Completion, BatchCompletion, GoTo, BatchGoTo, Info, GenerateFileFromTemplate
}

private const val MAX_STRING_LENGTH = 1000

class CompletionService(project: Project) {
    private val project: Project
    private val baseMethods = PsiElement::class.memberFunctions.map { el -> el.name }
    private val ignoredMethods = arrayOf(
        "clone",
        "getPsi",
        "getPrevPsiSibling",
        "getNextPsiSibling",
        "getFirstPsiChild",
        "getTreePrev",
        "getTreeNext",
        "copyElement"
    )
    private val elementIgnoredMethods = HashMap<String, List<Method>>()
    var lastResultSucceed: Boolean = true
    var lastResultMessage: String = ""

    init {
        this.project = project
    }

    fun getSettings(): Settings {
        return this.project.service()
    }

    fun getInfo(settings: Settings, project: Project, path: String, debug: Boolean? = null): JsonObject {
        val commandLine: GeneralCommandLine?
        var result: ProcessOutput? = null
        val innerDebug = if (null !== debug) debug else settings.debug


        commandLine = GeneralCommandLine(path)
        commandLine.environment.put("PSA_TYPE", RequestType.Info.toString())
        commandLine.environment.put("PSA_DEBUG", if (innerDebug) "1" else "0")
        commandLine.setWorkDirectory(project.guessProjectDir()?.path)
        val indicator = ProgressIndicatorProvider.getGlobalProgressIndicator() ?: EmptyProgressIndicator()

        ApplicationUtil.runWithCheckCanceled({
            result = runBlocking {
                ExecUtil.execAndGetOutput(
                    commandLine, settings.executionTimeout
                )
            }
        }, indicator)

        if (null === result) {
            throw Exception("Failed to get info")
        }


        if (0 != result!!.exitCode) {
            throw Exception(result!!.stdout + "\n" + result!!.stderr)
        }

        return Json.parseToJsonElement(result!!.stdout).jsonObject
    }

    fun updateInfo(settings: Settings, info: JsonObject) {
        val filter: List<String>
        val languages: List<String>

        try {
            if (info.containsKey("supported_languages")) {
                languages = (info.get("supported_languages") as JsonArray).map { i -> i.jsonPrimitive.content }
                settings.supportedLanguages = languages.joinToString(",")
            } else {
                throw Exception("`supported_languages` not found in JSON")
            }

            if (info.containsKey("goto_element_filter")) {
                filter = (info.get("goto_element_filter") as JsonArray).map { i -> i.jsonPrimitive.content }
                settings.goToFilter = filter.joinToString(",")
            }
            settings.supportsBatch =
                info.containsKey("supports_batch") && (info.get("supports_batch") as JsonElement).jsonPrimitive.boolean

            if (info.containsKey("templates")) {
                if (info.get("templates") !is JsonArray) {
                    throw Exception("`templates` must be an array")
                }

                val templates = info.get("templates") as JsonArray
                settings.singleFileCodeTemplates = ArrayList()
                for (template in templates) {
                    if (template !is JsonObject) {
                        throw Exception("`templates` must be an array of objects")
                    }
                    if (template.jsonObject.containsKey("type")) {
                        if (template.jsonObject["type"]!!.jsonPrimitive.content != "single_file") {
                            continue
                        }
                    } else {
                        throw Exception("`templates` must be an array of objects with `type` field")
                    }

                    val templateObject = SingleFileCodeTemplate()
                    if (template.jsonObject.containsKey("path_regex")) {
                        templateObject.pathRegex = template.jsonObject["path_regex"]!!.jsonPrimitive.content
                    }

                    if (template.jsonObject.containsKey("name")) {
                        templateObject.name = template.jsonObject["name"]!!.jsonPrimitive.content
                    } else {
                        throw Exception("`templates` must be an array of objects with `name` field")
                    }

                    if (template.jsonObject.containsKey("title")) {
                        templateObject.title = template.jsonObject["title"]!!.jsonPrimitive.content
                    } else {
                        throw Exception("`templates` must be an array of objects with `title` field")
                    }

                    if (template.jsonObject.containsKey("fields")) {
                        templateObject.formFields = ArrayList()
                        val fields = template.jsonObject["fields"]!!.jsonArray

                        for (field in fields) {
                            val fieldObject = TemplateFormField()
                            fieldObject.options = ArrayList()
                            if (field.jsonObject.containsKey("name")) {
                                fieldObject.name = field.jsonObject["name"]!!.jsonPrimitive.content
                            } else {
                                throw Exception("`fields` must be an object with `name` field")
                            }
                            if (field.jsonObject.containsKey("title")) {
                                fieldObject.title = field.jsonObject["title"]!!.jsonPrimitive.content
                            } else {
                                throw Exception("`fields` must be an object with `title` field")
                            }
                            if (field.jsonObject.containsKey("type")) {
                                fieldObject.type =
                                    TemplateFormFieldType.valueOf(field.jsonObject["type"]!!.jsonPrimitive.content)
                            } else {
                                throw Exception("`fields` must be an object with `type` field")
                            }
                            if (field.jsonObject.containsKey("options")) {
                                val options = field.jsonObject["options"]!!.jsonArray
                                for (option in options) {
                                    fieldObject.options!!.add(option.jsonPrimitive.content)
                                }
                            }
                            templateObject.formFields!!.add(fieldObject)
                        }
                    } else {
                        throw Exception("`templates` must be an array of objects with `fields` field")
                    }

                    settings.singleFileCodeTemplates!!.add(templateObject)
                }
            }
        } catch (e: Exception) {
            throw Exception("Failed to update info: " + e.message)
        }
    }

    fun generateTemplateCode(
        settings: Settings,
        project: Project,
        actionPath: String,
        templateName: String,
        originatorFieldName: String?,
        formFields: Map<String, String>
    ): JsonObject? {
        val commandLine: GeneralCommandLine?
        var result: ProcessOutput? = null


        try {
            val data = Json.encodeToString(
                GenerateFileFromTemplateData(
                    actionPath,
                    templateName,
                    originatorFieldName,
                    formFields
                )
            )
            val filePath = FileUtils.writeToTmpFile("psa_tmp", data)
            commandLine = GeneralCommandLine(settings.scriptPath)
            commandLine.environment.put("PSA_CONTEXT", filePath)
            commandLine.environment.put("PSA_TYPE", RequestType.GenerateFileFromTemplate.toString())
            commandLine.environment.put("PSA_DEBUG", if (settings.debug) "1" else "0")
            commandLine.setWorkDirectory(project.guessProjectDir()?.path)
            val indicator = ProgressIndicatorProvider.getGlobalProgressIndicator() ?: EmptyProgressIndicator()

            ApplicationUtil.runWithCheckCanceled({
                result = runBlocking {
                    ExecUtil.execAndGetOutput(
                        commandLine, settings.executionTimeout
                    )
                }
            }, indicator)

            if (null === result) {
                return null
            }


            if (0 != result!!.exitCode) {
                throw Exception(result!!.stdout + "\n" + result!!.stderr)
            }

            this.lastResultSucceed = true
            this.lastResultMessage = ""

            return Json.parseToJsonElement(result!!.stdout).jsonObject
        } catch (e: Exception) {
            this.lastResultSucceed = false
            this.lastResultMessage = e.message ?: "Unexpected Error"
            if (settings.debug) {
                NotificationGroupManager.getInstance()
                    .getNotificationGroup("PSA Notification")
                    .createNotification(
                        "Template generation returned not valid JSON<br/>" + e.message,
                        NotificationType.ERROR
                    )
                    .notify(project)
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
                firstModel.model.filePath?.indexOf("example") != 0
                && file.parent.indexOf(settings.getScriptDir()!!) >= 0
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
        val indicator = ProgressIndicatorProvider.getGlobalProgressIndicator() ?: EmptyProgressIndicator()


        commandLine = GeneralCommandLine(settings.scriptPath)
        commandLine.environment.put("PSA_CONTEXT", filePath)
        commandLine.environment.put("PSA_TYPE", requestType.toString())
        commandLine.environment.put("PSA_LANGUAGE", language)
        commandLine.environment.put("PSA_OFFSET", if (null !== editorOffset) editorOffset.toString() else "")
        commandLine.environment.put("PSA_DEBUG", if (debugValue) "1" else "0")
        commandLine.setWorkDirectory(project.guessProjectDir()?.path)

        if (fromIndex) {
            result = ExecUtil.execAndGetOutput(
                commandLine,
                settings.executionTimeout
            )
        } else {
            ApplicationUtil.runWithCheckCanceled({
                result = runBlocking {
                    ExecUtil.execAndGetOutput(
                        commandLine,
                        settings.executionTimeout
                    )
                }
            }, indicator)
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
        file: VirtualFile?,
        requestType: RequestType,
        language: String,
        editorOffset: Int? = null,
        debug: Boolean? = null,
        useIndex: Boolean = true,
        fromIndex: Boolean = false,
    ): JsonElement? {
        val index = project.service<PsaIndex>()
        var indexingEnabled = settings.indexingEnabled
        var indexReady = true
        val values = if (fromIndex) {
            listOf()
        } else {
            var indexValue: String? = null
            if (model.size == 1) {
                try {
                    indexValue = index.get(PsaIndex.generateKeyByRequestType(requestType, model[0]))
                } catch (e: IndexNotReadyException) {
                    indexReady = false
                } catch (e: IndexingDisabledException) {
                    indexingEnabled = false
                }
                if (null === indexValue && indexingEnabled && indexReady) {
                    return null
                }
            }
            if (null !== indexValue) listOf(indexValue) else listOf()
        }

        if (values.isNotEmpty() && useIndex) {
            return Json.parseToJsonElement(values[0]) as JsonObject
        }

        val result = getCompletionsOutput(
            settings,
            model,
            requestType,
            language,
            editorOffset,
            debug,
            fromIndex
        )

        this.lastResultSucceed = true
        this.lastResultMessage = ""


        if (null === result) {
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
                NotificationGroupManager.getInstance()
                    .getNotificationGroup("PSA Notification")
                    .createNotification(this.lastResultMessage, NotificationType.ERROR)
                    .notify(project)
            }

            return null
        }

        val jsonResult = Json.parseToJsonElement(result.stdout)
        if (jsonResult is JsonObject && jsonResult.containsKey("completions") && null !== jsonResult.get("completions")) {
            val completions = jsonResult.get("completions") as JsonArray
            if (!completions.isEmpty()) {
                if (!fromIndex && useIndex) {
                    val firstModel = model[0]
                    if (!settings.elementPaths.contains(firstModel.label)) {
                        settings.elementPaths[firstModel.label] = true
                    }
                    if (!settings.elementTypes.contains(firstModel.model.elementType)) {
                        settings.elementTypes[firstModel.model.elementType] = true
                    }

                    if (null !== file && indexReady) {
                        index.reindexFile(file)
                    }
                }
            }
        }

        return jsonResult
    }

    fun psiElementToModel(
        element: PsiElement,
        processParent: Boolean = true,
        processOptions: Boolean = true,
        processChildOptions: Boolean = true,
        processNext: Boolean = true,
        processPrev: Boolean = true,
        processedElements: ArrayList<PsiElement>? = null
    ): PsiElementModel {
        val filePath = if (null === processedElements) element.containingFile.virtualFile.path else null
        val currentProcessedElements = if (null !== processedElements) processedElements else ArrayList()
        val options = mutableMapOf<String, PsiElementModelChild>()
        val elementType = element.elementType.printToString()
        val methods: List<Method>

        if (this.elementIgnoredMethods[elementType] !== null) {
            methods = this.elementIgnoredMethods[elementType]!!
        } else {
            methods = element.javaClass.methods.filter { method ->
                !this.baseMethods.contains(method.name)
                        && !this.ignoredMethods.contains(method.name)
                        && method.parameterTypes.isEmpty()
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
            var signatureValue = try { method.invoke(element) as String} catch (e: InvocationTargetException) { "" }
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
                signature.toTypedArray(),
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
                if (method.returnType.componentType !== null) this.getAllExtendedOrImplementedInterfacesRecursively(
                    method.returnType.componentType
                ) else HashSet()
            try {
                if (
                    !method.returnType.isAssignableFrom(String::class.java)
                    && !method.returnType.isAssignableFrom(Number::class.java)
                    && !interfaces.any { e -> e.isAssignableFrom(PsiElement::class.java) }
                    && !componentTypeInterfaces.any { e -> e.isAssignableFrom(PsiElement::class.java) }
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
                    options[optionName] = PsiElementModelChild(
                        this.psiElementToModel(
                            result,
                            false,
                            true,
                            false,
                            false,
                            false,
                            currentProcessedElements
                        ), null
                    )
                } else if (result is Array<*> && result.isArrayOf<PsiElement>() && processOptions && processChildOptions) {
                    val arr: Array<PsiElementModel?> = arrayOfNulls(result.size)

                    for ((index, item) in (result).withIndex()) {
                        arr[index] = this.psiElementToModel(
                            item as PsiElement,
                            false,
                            true,
                            false,
                            false,
                            false,
                            currentProcessedElements
                        )
                    }
                    options[optionName] = PsiElementModelChild(null, null, arr)
                }
            } catch (e: InvocationTargetException) {
                if (e.targetException is IndexNotReadyException) {
                    throw e.targetException
                }
            } catch (e: IndexNotReadyException) {
                throw e
            } catch (e: Throwable) {
                continue
            }
        }

        if (element.parent !== null && processParent) {
            parentElement =
                this.psiElementToModel(
                    element.parent,
                    true,
                    true,
                    true,
                    true,
                    true,
                    currentProcessedElements
                )
        }
        if (processNext && element.nextSibling !== null) {
            nextElement = this.psiElementToModel(
                element.nextSibling,
                false,
                true,
                true,
                true,
                false,
                currentProcessedElements
            )
        }
        if (processPrev && element.prevSibling !== null) {
            prevElement = this.psiElementToModel(
                element.prevSibling,
                false,
                true,
                true,
                false,
                true,
                currentProcessedElements
            )
        }

        return PsiElementModel(
            hashCode,
            elementType,
            options,
            elementName,
            elementFqn,
            signature.toTypedArray(),
            elementText,
            parentElement,
            prevElement,
            nextElement,
            if (null !== element.textRange) PsiElementModelTextRange(
                element.textRange.startOffset,
                element.textRange.endOffset
            ) else null,
            filePath,
        )
    }

    private fun getAllExtendedOrImplementedInterfacesRecursively(clazz: Class<*>): Set<Class<*>> {
        val res: MutableSet<Class<*>> = HashSet()
        val interfaces = clazz.interfaces
        if (interfaces.size > 0) {
            res.addAll(interfaces)
            for (interfaze in interfaces) {
                res.addAll(getAllExtendedOrImplementedInterfacesRecursively(interfaze))
            }
        }
        return res
    }
}