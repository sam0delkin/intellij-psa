package com.github.sam0delkin.intellijpsa.completion

import com.github.sam0delkin.intellijpsa.icons.Icons
import com.github.sam0delkin.intellijpsa.services.Language
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
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.boolean
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
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

class AbstractCompletionContributor() {
    abstract class Completion : CompletionContributor() {
        abstract fun getLanguage(): Language

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
                        val json = projectService.getCompletions(
                            settings,
                            parameters.originalPosition,
                            parameters.originalFile,
                            RequestType.Completion,
                            getLanguage()
                        )

                        if (null === json) {
                            return
                        }

                        if (null !== json.get("completions")) {
                            for (i in json.get("completions") as JsonArray) {
                                var element =
                                    LookupElementBuilder.create(i.jsonObject.get("text")?.jsonPrimitive!!.content)
                                element = element.withIcon(Icons.PluginIcon)
                                if (i.jsonObject.get("bold")?.jsonPrimitive!!.boolean) {
                                    element = element.bold()
                                }
                                element = element.withTypeText(i.jsonObject.get("type")?.jsonPrimitive!!.content)
                                resultSet.addElement(element)
                            }
                        }

                        if (null !== json.get("notifications")) {
                            for (i in json.get("notifications") as JsonArray) {
                                var notificationType = NotificationType.INFORMATION
                                when (i.jsonObject.get("type")?.jsonPrimitive!!.content) {
                                    "error" -> notificationType = NotificationType.ERROR
                                    "warning" -> notificationType = NotificationType.WARNING
                                }

                                NotificationGroupManager.getInstance()
                                    .getNotificationGroup("PSA Notification")
                                    .createNotification(
                                        i.jsonObject.get("text")?.jsonPrimitive!!.content,
                                        notificationType
                                    )
                                    .notify(parameters.originalFile.project);
                            }
                        }
                    }
                }
            )
        }
    }

    abstract class GotoDeclaration : GotoDeclarationHandler {
        abstract fun getLanguage(): Language
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

            if (!settings.isElementTypeMatchingFilter(getLanguage(), sourceElement.elementType.printToString())) {
                return null
            }

            val json =
                projectService.getCompletions(
                    settings,
                    sourceElement,
                    sourceElement.containingFile,
                    RequestType.GoTo,
                    getLanguage()
                )

            if (null === json) {
                return null
            }

            val psiElements = ArrayList<PsiElement>()
            val fm = VirtualFileManager.getInstance();
            val pm = PsiManager.getInstance(project);

            if (null !== json.get("completions")) {
                for (i in json.get("completions") as JsonArray) {
                    val linkData = i.jsonObject.get("link")?.jsonPrimitive!!.content
                    val link = linkData.split(':');
                    val path = settings.replacePathMappings(this.getLanguage(), link[0])
                    val virtualFile = fm.findFileByUrl(project.guessProjectDir().toString() + path);
                    if (null !== virtualFile) {
                        val psiFile = pm.findFile(virtualFile);
                        if (null !== psiFile) {
                            if (link.count() > 1) {
                                val position =
                                    StringUtils.ordinalIndexOf(psiFile.fileDocument.text, "\n", link[1].toInt())
                                val element = psiFile.findElementAt(position)
                                if (null !== element) {
                                    psiElements.add(element)
                                }
                            } else {
                                psiElements.add(psiFile.firstChild)
                            }
                        }
                    }
                }
            }

            if (null !== json.get("notifications")) {
                for (i in json.get("notifications") as JsonArray) {
                    var notificationType = NotificationType.INFORMATION
                    when (i.jsonObject.get("type")?.jsonPrimitive!!.content) {
                        "error" -> notificationType = NotificationType.ERROR
                        "warning" -> notificationType = NotificationType.WARNING
                    }

                    NotificationGroupManager.getInstance()
                        .getNotificationGroup("PSA Notification")
                        .createNotification(i.jsonObject.get("text")?.jsonPrimitive!!.content, notificationType)
                        .notify(sourceElement.containingFile.project);
                }
            }

            return psiElements.toList().toTypedArray();
        }
    }
}