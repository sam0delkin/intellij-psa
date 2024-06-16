package com.github.sam0delkin.intellijpsa.services

import com.github.sam0delkin.intellijpsa.completion.PsiElementModel
import com.github.sam0delkin.intellijpsa.completion.PsiElementModelChild
import com.github.sam0delkin.intellijpsa.settings.Settings
import com.intellij.execution.configurations.GeneralCommandLine
import com.intellij.execution.process.ProcessOutput
import com.intellij.execution.util.ExecUtil
import com.intellij.notification.NotificationGroupManager
import com.intellij.notification.NotificationType
import com.intellij.openapi.application.ex.ApplicationUtil
import com.intellij.openapi.components.service
import com.intellij.openapi.progress.ProgressManager
import com.intellij.openapi.project.Project
import com.intellij.openapi.project.guessProjectDir
import com.intellij.psi.PsiElement
import com.intellij.psi.PsiFile
import com.intellij.psi.util.elementType
import com.jetbrains.rd.util.string.printToString
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.jsonObject
import java.io.BufferedWriter
import java.io.File
import java.io.FileWriter
import kotlin.reflect.full.memberFunctions

enum class RequestType {
    Completion, GoTo, Info
}

enum class Language {
    PHP, JS, TS
}

class ProjectService(project: Project) {
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

    init {
        this.project = project
    }

    fun getSettings(): Settings {
        return this.project.service<Settings>()
    }

    fun getInfo(settings: Settings, project: Project, path: String): JsonObject? {
        val commandLine: GeneralCommandLine?
        var result: ProcessOutput? = null


        commandLine = GeneralCommandLine(path)
        commandLine.environment.put("PSA_TYPE", RequestType.Info.toString())
        commandLine.environment.put("PSA_DEBUG", if (settings.debug) "1" else "0")
        commandLine.setWorkDirectory(project.guessProjectDir()?.path)

        result = ExecUtil.execAndGetOutput(
            commandLine,
            5000
        )


        if (0 != result.exitCode) {
           throw Exception(result.stdout + "\n" + result.stderr)
        }

        return Json.parseToJsonElement(result.stdout).jsonObject
    }

    fun getCompletions(
        settings: Settings,
        element: PsiElement?,
        file: PsiFile?,
        requestType: RequestType,
        language: String
    ): JsonObject? {
        if (!settings.pluginEnabled) {
            return null
        }

        if (null === element) {
            return null
        }

        if (!settings.isLanguageSupported(language)) {
            return null
        }
        if (
            file?.name?.indexOf("example") != 0
            && file?.containingDirectory.toString().indexOf(settings.getScriptDir()!!) >= 0
            ) {
            return null
        }

        val data = Json.encodeToString(psiElementToModel(element))
        val filePath = this.writeToFile("psa_tmp", data)
        val commandLine: GeneralCommandLine?
        var result: ProcessOutput? = null
        val indicator = ProgressManager.getGlobalProgressIndicator()


        commandLine = GeneralCommandLine(settings.scriptPath)
        commandLine.environment.put("PSA_CONTEXT", filePath)
        commandLine.environment.put("PSA_TYPE", requestType.toString())
        commandLine.environment.put("PSA_LANGUAGE", language.toString())
        commandLine.environment.put("PSA_DEBUG", if (settings.debug) "1" else "0")
        commandLine.setWorkDirectory(element.project.guessProjectDir()?.path)

        ApplicationUtil.runWithCheckCanceled({
            result = ExecUtil.execAndGetOutput(
                commandLine
            )
        }, indicator)

        try {
            val fileVal = File(filePath)
            if (fileVal.exists() && fileVal.isFile) {
                fileVal.delete()
            }
        } catch (_: Throwable) {
        }


        if (null === result) {
            return null
        }

        if (0 != result!!.exitCode) {
            if (settings.debug) {
                NotificationGroupManager.getInstance()
                    .getNotificationGroup("PSA Notification")
                    .createNotification(result!!.stdout + "\n" + result!!.stderr, NotificationType.ERROR)
                    .notify(element.project)
            }

            return null
        }

        return Json.parseToJsonElement(result!!.stdout).jsonObject
    }

    private fun psiElementToModel(
        element: PsiElement,
        processParent: Boolean = true,
        processOptions: Boolean = true,
        processChildOptions: Boolean = true,
        processedElements: ArrayList<PsiElement>? = null
    ): PsiElementModel {
        val currentProcessedElements = if (null !== processedElements) processedElements else ArrayList()
        val options = mutableMapOf<String, PsiElementModelChild>()
        val methods = element.javaClass.methods.filter { method ->
            !this.baseMethods.contains(method.name)
                    && !this.ignoredMethods.contains(method.name)
                    && method.parameterTypes.isEmpty()
        }

        val elementType = element.elementType.printToString()
        var elementName: String? = null
        var elementFqn: String? = null
        val nameMethod = element.javaClass.methods.filter { el -> el.name === "name" }

        if (nameMethod.isNotEmpty()) {
            elementName = nameMethod[0].invoke(element) as String
        }

        val fqnMethod = element.javaClass.methods.filter { el -> el.name === "name" }

        if (fqnMethod.isNotEmpty()) {
            elementFqn = fqnMethod[0].invoke(element) as String
        }

        var elementText = element.text
        if (elementText.length > 250) {
            elementText = elementText.substring(0, 250)
        }
        var parentElement: PsiElementModel? = null
        var nextElement: PsiElementModel? = null
        var prevElement: PsiElementModel? = null

        if (currentProcessedElements.contains(element)) {
            return PsiElementModel(
                elementType,
                options,
                elementName,
                elementFqn,
                elementText,
                null,
                null,
                null
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
                            currentProcessedElements
                        ), null
                    )
                } else if (result is Array<*> && result.isArrayOf<PsiElement>() && processOptions && processChildOptions) {
                    val arr: Array<PsiElementModel?> = arrayOfNulls(result.size)

                    for ((index, item) in (result as Array<PsiElement>).withIndex()) {
                        arr[index] = this.psiElementToModel(
                            item,
                            false,
                            true,
                            false,
                            currentProcessedElements
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
                this.psiElementToModel(element.parent, true, true, true, currentProcessedElements)
        }
        if (element.nextSibling !== null && processParent) {
            nextElement = this.psiElementToModel(
                element.nextSibling,
                false,
                true,
                true,
                currentProcessedElements
            )
        }
        if (element.prevSibling !== null && processParent) {
            prevElement = this.psiElementToModel(
                element.prevSibling,
                false,
                true,
                true,
                currentProcessedElements
            )
        }

        return PsiElementModel(
            elementType,
            options,
            elementName,
            elementFqn,
            elementText,
            parentElement,
            nextElement,
            prevElement
        )
    }

    fun writeToFile(pFilename: String, sb: String): String {
        val tempDir = File(System.getProperty("java.io.tmpdir"))
        val tempFile: File = File.createTempFile(pFilename, ".tmp", tempDir)
        val fileWriter = FileWriter(tempFile, true)
        val bw = BufferedWriter(fileWriter)
        bw.write(sb)
        bw.close()

        return tempFile.absolutePath
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