package com.github.sam0delkin.intellijpsa.language.javascript.methodArguments

import com.github.sam0delkin.intellijpsa.language.javascript.settings.JsPsaSettings
import com.github.sam0delkin.intellijpsa.settings.Settings
import com.intellij.codeInsight.completion.CompletionContributor
import com.intellij.codeInsight.completion.CompletionParameters
import com.intellij.codeInsight.completion.CompletionProvider
import com.intellij.codeInsight.completion.CompletionResultSet
import com.intellij.codeInsight.completion.CompletionType
import com.intellij.codeInsight.completion.PrioritizedLookupElement
import com.intellij.codeInsight.lookup.LookupElementBuilder
import com.intellij.openapi.components.service
import com.intellij.patterns.PlatformPatterns
import com.intellij.util.ProcessingContext

private const val HIGH_PRIORITY = 100.0

class JsMethodNameCompletionContributor : CompletionContributor() {
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
                    val position = parameters.position
                    val project = position.project
                    val settings = project.service<Settings>()
                    val jsSettings = project.service<JsPsaSettings>()
                    val providers = jsSettings.methodArgumentProviders

                    if (!settings.pluginEnabled || !jsSettings.enabled || providers.isNullOrEmpty()) {
                        return
                    }

                    val provider = JsMethodArgumentHelper.findProviderForMethodNamePosition(position, providers) ?: return

                    val names = JsMethodArgumentHelper.methodNames(project, provider.`class`)
                    if (names.isEmpty()) return

                    names.forEach {
                        val element = LookupElementBuilder.create(it).withTypeText(provider.`class`)
                        resultSet.addElement(PrioritizedLookupElement.withPriority(element, HIGH_PRIORITY))
                    }

                    resultSet.stopHere()
                }
            },
        )
    }
}
