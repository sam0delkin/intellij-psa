package com.github.sam0delkin.intellijpsa.completion

import com.github.sam0delkin.intellijpsa.icons.Icons
import com.github.sam0delkin.intellijpsa.index.IndexedPsiElementModel
import com.github.sam0delkin.intellijpsa.model.CompletionsModel
import com.github.sam0delkin.intellijpsa.psi.PsaElement
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

                        if (!settings.isLanguageSupported(languageString)) {
                            return
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
                                )

                        if (null === json) {
                            return
                        }

                        if (null !== json.completions) {
                            for (i in json.completions) {
                                var priority = 0.0
                                var element = LookupElementBuilder.create(i.text ?: "")
                                element = element.withIcon(Icons.PluginIcon)

                                if (true == i.bold) {
                                    element = element.bold()
                                    priority = 100.0
                                }

                                if (null !== i.presentableText) {
                                    element = element.withPresentableText(i.presentableText)
                                }

                                if (i.tailText != null) {
                                    element = element.withTailText(i.tailText)
                                }

                                if (i.type != null) {
                                    element = element.withTypeText(i.type)
                                }

                                if (i.priority != null) {
                                    priority = i.priority
                                }

                                resultSet.addElement(PrioritizedLookupElement.withPriority(element, priority))
                            }
                        }

                        processNotifications(json, project)
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

            if (!settings.isLanguageSupported(languageString)) {
                return null
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
                    )

            if (null === json) {
                return null
            }

            if (json.completions != null) {
                for (i in json.completions) {
                    val linkData = i.link ?: ""
                    var text = i.text ?: linkData
                    if (i.presentableText != null) {
                        text = i.presentableText
                    }
                    processLink(linkData, text, settings, project, psiElements)
                }
            }

            processNotifications(json, project)

            return psiElements.toTypedArray()
        }

        private fun processLink(
            linkData: String,
            text: String,
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
                            psiElements.add(PsaElement(element, text))
                        } else {
                            psiElements.add(PsaElement(psiFile.firstChild, psiFile.name))
                        }
                    } else {
                        psiElements.add(PsaElement(psiFile.firstChild, psiFile.name))
                    }
                }
            }
        }
    }
}

private fun processNotifications(
    json: CompletionsModel,
    project: Project,
) {
    if (null !== json.notifications) {
        for (i in json.notifications) {
            var notificationType = NotificationType.INFORMATION

            when (i.type) {
                "info" -> notificationType = NotificationType.INFORMATION
                "warning" -> notificationType = NotificationType.WARNING
                "error" -> notificationType = NotificationType.ERROR
            }

            NotificationGroupManager
                .getInstance()
                .getNotificationGroup("PSA Notification")
                .createNotification(i.text, notificationType)
                .notify(project)
        }
    }
}
