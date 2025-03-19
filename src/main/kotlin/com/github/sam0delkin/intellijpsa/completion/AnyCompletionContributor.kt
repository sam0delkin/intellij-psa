package com.github.sam0delkin.intellijpsa.completion

import com.github.sam0delkin.intellijpsa.model.CompletionsModel
import com.github.sam0delkin.intellijpsa.model.ExtendedCompletionsModel
import com.github.sam0delkin.intellijpsa.model.IndexedPsiElementModel
import com.github.sam0delkin.intellijpsa.psi.PsiElementModelHelper
import com.github.sam0delkin.intellijpsa.services.PsaManager
import com.github.sam0delkin.intellijpsa.services.RequestType
import com.intellij.codeInsight.completion.CompletionContributor
import com.intellij.codeInsight.completion.CompletionParameters
import com.intellij.codeInsight.completion.CompletionProvider
import com.intellij.codeInsight.completion.CompletionResultSet
import com.intellij.codeInsight.completion.CompletionType
import com.intellij.codeInsight.navigation.actions.GotoDeclarationHandler
import com.intellij.notification.NotificationGroupManager
import com.intellij.notification.NotificationType
import com.intellij.openapi.components.service
import com.intellij.openapi.editor.Editor
import com.intellij.openapi.project.Project
import com.intellij.patterns.PlatformPatterns
import com.intellij.psi.PsiElement
import com.intellij.psi.util.elementType
import com.intellij.util.ProcessingContext
import com.jetbrains.rd.util.string.printToString
import org.apache.velocity.VelocityContext
import org.apache.velocity.app.Velocity
import org.apache.velocity.util.introspection.UberspectImpl
import java.io.StringWriter
import java.util.Properties

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
                        val psaManager = project.service<PsaManager>()
                        val settings = psaManager.getSettings()
                        val language = parameters.originalFile.language
                        var languageString = language.id
                        if (language.baseLanguage !== null && !settings.isLanguageSupported(languageString)) {
                            languageString = language.baseLanguage!!.id
                        }

                        if (!settings.isLanguageSupported(languageString)) {
                            return
                        }

                        val model = psaManager.psiElementToModel(parameters.originalPosition!!)
                        var json: ExtendedCompletionsModel? = null

                        if (null !== settings.staticCompletionConfigs) {
                            for (i in settings.staticCompletionConfigs!!) {
                                if (null === i.patterns) {
                                    continue
                                }

                                for (j in i.patterns) {
                                    if (PsiElementModelHelper.matches(model, j)) {
                                        if (null === json) {
                                            json = ExtendedCompletionsModel.createFromModel(i.completions!!)
                                        } else {
                                            json.completions = json.completions!! + ArrayList(i.completions!!.completions!!)
                                        }
                                    }
                                }
                            }
                        }

                        if (null === json) {
                            json =
                                psaManager
                                    .getCompletions(
                                        settings,
                                        arrayOf(
                                            IndexedPsiElementModel(
                                                model,
                                                parameters.originalPosition!!.textRange.printToString(),
                                            ),
                                        ),
                                        RequestType.Completion,
                                        languageString,
                                        parameters.offset,
                                    )
                        }

                        if (null === json) {
                            return
                        }

                        if (null !== json.extendedCompletions) {
                            for (i in json.extendedCompletions!!) {
                                resultSet.addElement(i.toCompletionLookupElement())
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
            val psaManager = project.service<PsaManager>()
            val settings = psaManager.getSettings()
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
            val model = psaManager.psiElementToModel(sourceElement)

            var json: CompletionsModel? = null

            if (null !== settings.staticCompletionConfigs) {
                for (config in settings.staticCompletionConfigs!!) {
                    if (null === config.patterns) {
                        continue
                    }

                    for (pattern in config.patterns) {
                        if (PsiElementModelHelper.matches(model, pattern)) {
                            json = CompletionsModel()
                            json.completions = ArrayList(config.completions!!.completions!!)
                            var notificationShown = false
                            if (config.matcher != null) {
                                val properties = Properties()
                                properties.setProperty(
                                    "introspector.uberspect.class",
                                    UberspectImpl::class.java.name,
                                )
                                Velocity.init(properties)
                                json.completions =
                                    ArrayList(json.completions!!).filter {
                                        val writer = StringWriter()
                                        val context = VelocityContext()
                                        context.put("completion", it)
                                        context.put("model", model)

                                        try {
                                            Velocity.evaluate(context, writer, "", config.matcher)
                                        } catch (e: Exception) {
                                            if (!notificationShown) {
                                                NotificationGroupManager
                                                    .getInstance()
                                                    .getNotificationGroup("PSA Notification")
                                                    .createNotification(
                                                        "Error evaluating static completion matcher",
                                                        e.message!!,
                                                        NotificationType.ERROR,
                                                    ).notify(project)

                                                notificationShown = true
                                            }

                                            return@filter false
                                        }

                                        val result = writer.buffer.toString()

                                        result == "true" || result == "1"
                                    }
                            } else {
                                json.completions = ArrayList(json.completions!!).filter { it.text == sourceElement.text }
                                if (json.completions?.size != 1) {
                                    json = config.completions
                                }
                            }

                            break
                        }
                    }

                    if (null !== json) {
                        break
                    }
                }
            }

            if (null === json) {
                json =
                    psaManager
                        .getCompletions(
                            settings,
                            arrayOf(
                                IndexedPsiElementModel(
                                    model,
                                    sourceElement.textRange.printToString(),
                                ),
                            ),
                            RequestType.GoTo,
                            languageString,
                            offset,
                        )
            }

            if (null === json) {
                return null
            }

            if (json.completions != null) {
                val completions = ExtendedCompletionsModel.createFromModel(json)
                for (completionModel in completions.extendedCompletions!!) {
                    completionModel.toGoToElementList(project)?.let { psiElements.add(it) }
                }
            }

            processNotifications(json, project)

            return psiElements.toTypedArray()
        }
    }
}

private fun processNotifications(
    json: CompletionsModel,
    project: Project,
) {
    if (null !== json.notifications) {
        for (i in json.notifications!!) {
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
