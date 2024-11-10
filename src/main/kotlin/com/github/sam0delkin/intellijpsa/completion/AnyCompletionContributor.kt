package com.github.sam0delkin.intellijpsa.completion

import com.github.sam0delkin.intellijpsa.icons.Icons
import com.github.sam0delkin.intellijpsa.index.IndexedPsiElementModel
import com.github.sam0delkin.intellijpsa.services.CompletionService
import com.github.sam0delkin.intellijpsa.services.RequestType
import com.github.sam0delkin.intellijpsa.settings.Settings
import com.intellij.codeInsight.completion.CompletionContributor
import com.intellij.codeInsight.completion.CompletionParameters
import com.intellij.codeInsight.completion.CompletionProvider
import com.intellij.codeInsight.completion.CompletionResultSet
import com.intellij.codeInsight.completion.CompletionType
import com.intellij.codeInsight.completion.PrioritizedLookupElement
import com.intellij.codeInsight.lookup.LookupElementBuilder
import com.intellij.codeInsight.navigation.actions.GotoDeclarationHandler
import com.intellij.notification.NotificationGroupManager
import com.intellij.notification.NotificationType
import com.intellij.openapi.components.service
import com.intellij.openapi.editor.Editor
import com.intellij.openapi.project.Project
import com.intellij.openapi.project.guessProjectDir
import com.intellij.openapi.vfs.VirtualFileManager
import com.intellij.patterns.PlatformPatterns
import com.intellij.psi.PsiElement
import com.intellij.psi.PsiManager
import com.intellij.psi.util.elementType
import com.intellij.util.ProcessingContext
import com.jetbrains.rd.util.string.printToString
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.boolean
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.long
import org.apache.commons.lang3.StringUtils

class AnyCompletionContributor {
    class Completion : CompletionContributor() {
        init {
            extend(
                CompletionType.BASIC,
                PlatformPatterns.psiElement(),
                object : CompletionProvider<CompletionParameters>() {
                    override fun addCompletions(
                        parameters: CompletionParameters,
                        context: ProcessingContext,
                        resultSet: CompletionResultSet,
                    ) {
                        if (null === parameters.originalPosition) {
                            return
                        }

                        val project = parameters.position.project
                        val completionService = project.service<CompletionService>()
                        val settings = completionService.getSettings()
                        val language = parameters.originalFile.language
                        var languageString = language.id
                        if (language.baseLanguage !== null && !settings.isLanguageSupported(languageString)) {
                            languageString = language.baseLanguage!!.id
                        }

                        val model = completionService.psiElementToModel(parameters.originalPosition!!)

                        val json =
                            completionService
                                .getCompletions(
                                    settings,
                                    arrayOf(
                                        IndexedPsiElementModel(
                                            model,
                                            parameters.originalPosition!!.textRange.printToString(),
                                        ),
                                    ),
                                    parameters.originalPosition!!.containingFile.virtualFile,
                                    RequestType.Completion,
                                    languageString,
                                    parameters.offset,
                                )?.jsonObject

                        if (null === json) {
                            return
                        }

                        if (null !== json.get("completions")) {
                            for (i in json.get("completions") as JsonArray) {
                                var priority = 0.0
                                var element =
                                    LookupElementBuilder.create(
                                        i.jsonObject
                                            .get("text")
                                            ?.jsonPrimitive!!
                                            .content,
                                    )
                                element = element.withIcon(Icons.PluginIcon)

                                if (i.jsonObject
                                        .containsKey("bold") &&
                                    i.jsonObject
                                        .get("bold")
                                        ?.jsonPrimitive!!
                                        .boolean
                                ) {
                                    element = element.bold()
                                    priority = 100.0
                                }

                                if (i.jsonObject
                                        .containsKey("presentable_text")
                                ) {
                                    element =
                                        element.withPresentableText(
                                            i.jsonObject
                                                .get("presentable_text")
                                                ?.jsonPrimitive!!
                                                .content,
                                        )
                                }

                                if (i.jsonObject
                                        .containsKey("tail_text")
                                ) {
                                    element =
                                        element.withTailText(
                                            i.jsonObject
                                                .get("tail_text")
                                                ?.jsonPrimitive!!
                                                .content,
                                        )
                                }

                                element =
                                    element.withTypeText(
                                        i.jsonObject
                                            .get("type")
                                            ?.jsonPrimitive!!
                                            .content,
                                    )

                                if (i.jsonObject.containsKey("priority")) {
                                    priority =
                                        i.jsonObject
                                            .get("priority")
                                            ?.jsonPrimitive!!
                                            .long
                                            .toDouble()
                                }

                                resultSet.addElement(PrioritizedLookupElement.withPriority(element, priority))
                            }
                        }

                        if (null !== json.get("notifications")) {
                            for (i in json.get("notifications") as JsonArray) {
                                var notificationType = NotificationType.INFORMATION
                                when (
                                    i.jsonObject
                                        .get("type")
                                        ?.jsonPrimitive!!
                                        .content
                                ) {
                                    "info" -> notificationType = NotificationType.INFORMATION
                                    "warning" -> notificationType = NotificationType.WARNING
                                    "error" -> notificationType = NotificationType.ERROR
                                }

                                NotificationGroupManager
                                    .getInstance()
                                    .getNotificationGroup("PSA Notification")
                                    .createNotification(
                                        i.jsonObject
                                            .get("text")
                                            ?.jsonPrimitive!!
                                            .content,
                                        notificationType,
                                    ).notify(parameters.originalFile.project)
                            }
                        }
                    }
                },
            )
        }
    }

    class GotoDeclaration : GotoDeclarationHandler {
        override fun getGotoDeclarationTargets(
            sourceElement: PsiElement?,
            offset: Int,
            editor: Editor?,
        ): Array<PsiElement>? {
            if (sourceElement === null) {
                return null
            }

            val project = sourceElement.project
            val completionService = project.service<CompletionService>()
            val settings = completionService.getSettings()
            val language = sourceElement.containingFile.language
            var languageString = language.id
            val psiElements = ArrayList<PsiElement>()
            if (language.baseLanguage !== null && !settings.isLanguageSupported(languageString)) {
                languageString = language.baseLanguage!!.id
            }

            if (!settings.isElementTypeMatchingFilter(sourceElement.elementType.printToString())) {
                return null
            }
            val model = completionService.psiElementToModel(sourceElement)

            val json =
                completionService
                    .getCompletions(
                        settings,
                        arrayOf(
                            IndexedPsiElementModel(
                                model,
                                sourceElement.textRange.printToString(),
                            ),
                        ),
                        sourceElement.containingFile.virtualFile,
                        RequestType.GoTo,
                        languageString,
                        offset,
                    )?.jsonObject

            if (null === json) {
                return null
            }

            if (json.containsKey("completions")) {
                for (i in json.get("completions") as JsonArray) {
                    val linkData =
                        i.jsonObject
                            .get("link")
                            ?.jsonPrimitive!!
                            .content
                    processLink(linkData, settings, project, psiElements)
                }
            }

            if (json.containsKey("notifications")) {
                for (i in json.get("notifications") as JsonArray) {
                    var notificationType = NotificationType.INFORMATION
                    when (
                        i.jsonObject
                            .get("type")
                            ?.jsonPrimitive!!
                            .content
                    ) {
                        "info" -> notificationType = NotificationType.INFORMATION
                        "warning" -> notificationType = NotificationType.WARNING
                        "error" -> notificationType = NotificationType.ERROR
                    }

                    NotificationGroupManager
                        .getInstance()
                        .getNotificationGroup("PSA Notification")
                        .createNotification(
                            i.jsonObject
                                .get("text")
                                ?.jsonPrimitive!!
                                .content,
                            notificationType,
                        ).notify(sourceElement.containingFile.project)
                }
            }

            return psiElements.toTypedArray()
        }

        private fun processLink(
            linkData: String,
            settings: Settings,
            project: Project,
            psiElements: ArrayList<PsiElement>,
        ) {
            val fm = VirtualFileManager.getInstance()
            val pm = PsiManager.getInstance(project)
            val link = linkData.split(':')
            val path = settings.replacePathMappings(link[0])
            val virtualFile = fm.findFileByUrl(project.guessProjectDir().toString() + path)
            if (null !== virtualFile) {
                val psiFile = pm.findFile(virtualFile)
                if (null !== psiFile) {
                    if (link.count() > 1) {
                        val position =
                            StringUtils.ordinalIndexOf(psiFile.originalFile.text, "\n", link[1].toInt())
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
}
