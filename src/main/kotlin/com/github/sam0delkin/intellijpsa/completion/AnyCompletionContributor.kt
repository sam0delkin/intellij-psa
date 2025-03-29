package com.github.sam0delkin.intellijpsa.completion

import com.github.sam0delkin.intellijpsa.annotator.ANNOTATOR_COMPLETION_TITLE
import com.github.sam0delkin.intellijpsa.exception.UpdateStaticCompletionsException
import com.github.sam0delkin.intellijpsa.model.ExtendedCompletionsModel
import com.github.sam0delkin.intellijpsa.model.completion.CompletionsModel
import com.github.sam0delkin.intellijpsa.model.psi.IndexedPsiElementModel
import com.github.sam0delkin.intellijpsa.psi.helper.PsiElementModelHelper
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
import com.intellij.openapi.components.Service
import com.intellij.openapi.components.service
import com.intellij.openapi.editor.Editor
import com.intellij.openapi.project.DumbService
import com.intellij.openapi.project.Project
import com.intellij.patterns.PlatformPatterns
import com.intellij.psi.PsiElement
import com.intellij.psi.PsiReferenceService
import com.intellij.psi.impl.source.resolve.reference.ReferenceProvidersRegistry
import com.intellij.psi.util.elementType
import com.intellij.util.ProcessingContext
import com.jetbrains.rd.util.string.printToString
import org.apache.velocity.VelocityContext
import org.apache.velocity.app.Velocity
import java.io.StringWriter

val RETURN_ALL_STATIC_COMPLETIONS = -2

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

                        if (null !== psaManager.staticCompletionConfigs) {
                            for (i in psaManager.staticCompletionConfigs!!) {
                                if (null == i.patterns) {
                                    continue
                                }

                                for (j in i.patterns!!) {
                                    if (PsiElementModelHelper.matches(model, j)) {
                                        if (null === json) {
                                            json = ExtendedCompletionsModel.createFromModel(i.completions!!, project)
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

    @Service(Service.Level.PROJECT)
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

            if (!settings.pluginEnabled) {
                return null
            }

            if (language.baseLanguage !== null && !settings.isLanguageSupported(languageString)) {
                languageString = language.baseLanguage!!.id
            }

            if (!settings.isLanguageSupported(languageString)) {
                return null
            }

            if (
                !settings.isElementTypeMatchingFilter(sourceElement.elementType.printToString()) &&
                (
                    null == settings.targetElementTypes ||
                        !settings.targetElementTypes!!.contains(sourceElement.elementType.printToString())
                )
            ) {
                return null
            }

            val model = psaManager.psiElementToModel(sourceElement)

            var json: ExtendedCompletionsModel? = null

            if (null !== psaManager.staticCompletionConfigs) {
                for (config in psaManager.staticCompletionConfigs!!) {
                    if (null === config.patterns) {
                        continue
                    }

                    for (pattern in config.patterns!!) {
                        if (PsiElementModelHelper.matches(model, pattern)) {
                            json = ExtendedCompletionsModel()
                            json.extendedCompletions = ArrayList(config.extendedCompletions!!.extendedCompletions!!)
                            sourceElement.putUserData(ANNOTATOR_COMPLETION_TITLE, config.title)

                            var notificationShown = false
                            if (config.matcher != null) {
                                json.extendedCompletions =
                                    ArrayList(json.extendedCompletions!!).filter {
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
                                json.extendedCompletions = ArrayList(json.extendedCompletions!!).filter { it.text == sourceElement.text }
                                if (offset == RETURN_ALL_STATIC_COMPLETIONS || json.extendedCompletions!!.size > 1) {
                                    json = config.extendedCompletions
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

            if (json?.extendedCompletions != null) {
                for (completionModel in json!!.extendedCompletions!!) {
                    try {
                        completionModel.toGoToElement(project)?.let { psiElements.add(it) }
                    } catch (_: UpdateStaticCompletionsException) {
                        psaManager.updateStaticCompletions(settings, project)
                        json = null

                        break
                    }
                }

                return psiElements.toTypedArray()
            }

            if (offset >= 0 && !DumbService.getInstance(project).isDumb) {
                val references = ReferenceProvidersRegistry.getReferencesFromProviders(sourceElement, PsiReferenceService.Hints.NO_HINTS)
                if (references.isNotEmpty()) {
                    for (reference in references) {
                        val resolvedReference = reference.resolve()
                        if (null != resolvedReference) {
                            psiElements.add(resolvedReference)
                        }
                    }
                }

                if (psiElements.isNotEmpty()) {
                    return psiElements.toTypedArray()
                }
            }

            if (null === json && offset >= 0) {
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

            if (json.extendedCompletions != null) {
                for (completionModel in json.extendedCompletions!!) {
                    try {
                        completionModel.toGoToElement(project)?.let { psiElements.add(it) }
                    } catch (_: UpdateStaticCompletionsException) {
                        psaManager.updateStaticCompletions(settings, project)

                        return psiElements.toTypedArray()
                    }
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
