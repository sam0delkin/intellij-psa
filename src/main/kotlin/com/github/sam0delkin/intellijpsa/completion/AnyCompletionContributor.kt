package com.github.sam0delkin.intellijpsa.completion

import com.github.sam0delkin.intellijpsa.icons.Icons
import com.github.sam0delkin.intellijpsa.services.ProjectService
import com.github.sam0delkin.intellijpsa.services.RequestType
import com.intellij.codeInsight.completion.*
import com.intellij.codeInsight.lookup.LookupElementBuilder
import com.intellij.codeInsight.navigation.actions.GotoDeclarationHandler
import com.intellij.notification.NotificationGroupManager
import com.intellij.notification.NotificationType
import com.intellij.openapi.components.service
import com.intellij.openapi.editor.Editor
import com.intellij.openapi.project.guessProjectDir
import com.intellij.openapi.vfs.VirtualFileManager
import com.intellij.patterns.PlatformPatterns
import com.intellij.psi.PsiElement
import com.intellij.psi.PsiManager
import com.intellij.psi.util.elementType
import com.intellij.util.ProcessingContext
import com.jetbrains.rd.util.string.printToString
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.*
import org.apache.commons.lang3.StringUtils

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
data class PsiElementModel(
    val elementType: String,
    val options: MutableMap<String, PsiElementModelChild>,
    val elementName: String?,
    val elementFqn: String?,
    val text: String?,
    val parent: PsiElementModel?,
    val prev: PsiElementModel?,
    val next: PsiElementModel?
)

class AnyCompletionContributor() {
    class Completion : CompletionContributor() {
        init {
            extend(
                CompletionType.BASIC, PlatformPatterns.psiElement(),
                object : CompletionProvider<CompletionParameters>() {
                    override fun addCompletions(
                        parameters: CompletionParameters,
                        context: ProcessingContext,
                        resultSet: CompletionResultSet
                    ) {
                        val project = parameters.position.project
                        val projectService = project.service<ProjectService>()
                        val settings = projectService.getSettings()
                        val language = parameters.originalFile.language
                        var languageString = language.id
                        if (language.baseLanguage !== null && !settings.isLanguageSupported(languageString)) {
                            languageString = language.baseLanguage!!.id
                        }

                        val json = projectService.getCompletions(
                            settings,
                            parameters.originalPosition,
                            parameters.originalFile,
                            RequestType.Completion,
                            languageString
                        )

                        if (null === json) {
                            return
                        }

                        if (null !== json.get("completions")) {
                            for (i in json.get("completions") as JsonArray) {
                                var priority = 0.0
                                var element =
                                    LookupElementBuilder.create(i.jsonObject.get("text")?.jsonPrimitive!!.content)
                                element = element.withIcon(Icons.PluginIcon)

                                if (i.jsonObject.get("bold")?.jsonPrimitive!!.boolean) {
                                    element = element.bold()
                                    priority = 100.0
                                }

                                element = element.withTypeText(i.jsonObject.get("type")?.jsonPrimitive!!.content)

                                if (i.jsonObject.containsKey("priority")) {
                                    priority = i.jsonObject.get("priority")?.jsonPrimitive!!.long.toDouble()
                                }

                                resultSet.addElement(PrioritizedLookupElement.withPriority(element, priority))
                            }
                        }

                        if (null !== json.get("notifications")) {
                            for (i in json.get("notifications") as JsonArray) {
                                var notificationType = NotificationType.INFORMATION
                                when (i.jsonObject.get("type")?.jsonPrimitive!!.content) {
                                    "info" -> notificationType = NotificationType.INFORMATION
                                    "warning" -> notificationType = NotificationType.WARNING
                                    "error" -> notificationType = NotificationType.ERROR
                                }

                                NotificationGroupManager.getInstance()
                                    .getNotificationGroup("PSA Notification")
                                    .createNotification(
                                        i.jsonObject.get("text")?.jsonPrimitive!!.content,
                                        notificationType
                                    )
                                    .notify(parameters.originalFile.project)
                            }
                        }
                    }
                }
            )
        }
    }

    class GotoDeclaration : GotoDeclarationHandler {
        override fun getGotoDeclarationTargets(
            sourceElement: PsiElement?,
            offset: Int,
            editor: Editor?
        ): Array<PsiElement>? {
            if (sourceElement === null) {
                return null
            }

            val project = sourceElement.project
            val projectService = project.service<ProjectService>()
            val settings = projectService.getSettings()
            val language = sourceElement.containingFile.language
            var languageString = language.id
            if (language.baseLanguage !== null && !settings.isLanguageSupported(languageString)) {
                languageString = language.baseLanguage!!.id
            }

            if (!settings.isElementTypeMatchingFilter(sourceElement.elementType.printToString())) {
                return null
            }

            val json =
                projectService.getCompletions(
                    settings,
                    sourceElement,
                    sourceElement.containingFile,
                    RequestType.GoTo,
                    languageString
                )

            if (null === json) {
                return null
            }

            val psiElements = ArrayList<PsiElement>()
            val fm = VirtualFileManager.getInstance()
            val pm = PsiManager.getInstance(project)

            if (json.containsKey("completions")) {
                for (i in json.get("completions") as JsonArray) {
                    val linkData = i.jsonObject.get("link")?.jsonPrimitive!!.content
                    val link = linkData.split(':')
                    val path = settings.replacePathMappings(link[0])
                    val virtualFile = fm.findFileByUrl(project.guessProjectDir().toString() + path)
                    if (null !== virtualFile) {
                        val psiFile = pm.findFile(virtualFile)
                        if (null !== psiFile) {
                            if (link.count() > 1) {
                                val position =
                                    StringUtils.ordinalIndexOf(psiFile.fileDocument.text, "\n", link[1].toInt())
                                val element = psiFile.findElementAt(position)
                                if (null !== element) {
                                    psiElements.add(element)
                                } else {
                                    psiElements.add(psiFile.firstChild)
                                }
                            } else {
                                psiElements.add(psiFile.firstChild)
                            }
                        }
                    }
                }
            }

            if (json.containsKey("goto_element_filter")) {
                val filter = (json.get("goto_element_filter") as JsonArray).map { i -> i.jsonPrimitive.content }.joinToString(",")
                settings.setElementFilter(filter)
            }

            if (json.containsKey("notifications")) {
                for (i in json.get("notifications") as JsonArray) {
                    var notificationType = NotificationType.INFORMATION
                    when (i.jsonObject.get("type")?.jsonPrimitive!!.content) {
                        "info" -> notificationType = NotificationType.INFORMATION
                        "warning" -> notificationType = NotificationType.WARNING
                        "error" -> notificationType = NotificationType.ERROR
                    }

                    NotificationGroupManager.getInstance()
                        .getNotificationGroup("PSA Notification")
                        .createNotification(i.jsonObject.get("text")?.jsonPrimitive!!.content, notificationType)
                        .notify(sourceElement.containingFile.project)
                }
            }

            return psiElements.toList().toTypedArray()
        }
    }
}