package com.github.sam0delkin.intellijpsa.language.javascript.methodArguments

import com.github.sam0delkin.intellijpsa.language.javascript.settings.JsPsaSettings
import com.github.sam0delkin.intellijpsa.settings.Settings
import com.intellij.lang.javascript.psi.JSLiteralExpression
import com.intellij.openapi.components.service
import com.intellij.patterns.PlatformPatterns
import com.intellij.psi.PsiElement
import com.intellij.psi.PsiReference
import com.intellij.psi.PsiReferenceContributor
import com.intellij.psi.PsiReferenceProvider
import com.intellij.psi.PsiReferenceRegistrar
import com.intellij.util.ProcessingContext

class JsMethodArgumentReferenceContributor : PsiReferenceContributor() {
    override fun registerReferenceProviders(registrar: PsiReferenceRegistrar) {
        registrar.registerReferenceProvider(
            PlatformPatterns.psiElement(JSLiteralExpression::class.java),
            object : PsiReferenceProvider() {
                override fun getReferencesByElement(
                    element: PsiElement,
                    context: ProcessingContext,
                ): Array<PsiReference> {
                    val settings = element.project.service<Settings>()
                    val jsSettings = element.project.service<JsPsaSettings>()
                    val providers = jsSettings.methodArgumentProviders

                    if (!settings.pluginEnabled || !jsSettings.enabled || providers.isNullOrEmpty()) {
                        return emptyArray()
                    }

                    val match =
                        JsMethodArgumentHelper.findProviderForMethodNameElement(element, providers)
                            ?: return emptyArray()

                    return arrayOf(JsMethodReference(element, match.function))
                }
            },
        )
    }
}
