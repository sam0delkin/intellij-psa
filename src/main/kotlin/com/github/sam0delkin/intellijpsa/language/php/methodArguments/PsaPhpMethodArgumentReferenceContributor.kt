package com.github.sam0delkin.intellijpsa.language.php.methodArguments

import com.github.sam0delkin.intellijpsa.language.php.settings.PhpPsaSettings
import com.github.sam0delkin.intellijpsa.settings.Settings
import com.intellij.openapi.components.service
import com.intellij.patterns.PlatformPatterns
import com.intellij.psi.PsiElement
import com.intellij.psi.PsiReference
import com.intellij.psi.PsiReferenceContributor
import com.intellij.psi.PsiReferenceProvider
import com.intellij.psi.PsiReferenceRegistrar
import com.intellij.util.ProcessingContext
import com.jetbrains.php.lang.PhpLanguage
import com.jetbrains.php.lang.psi.elements.ConstantReference
import com.jetbrains.php.lang.psi.elements.StringLiteralExpression

class PsaPhpMethodArgumentReferenceContributor : PsiReferenceContributor() {
    override fun registerReferenceProviders(registrar: PsiReferenceRegistrar) {
        val provider =
            object : PsiReferenceProvider() {
                override fun getReferencesByElement(
                    element: PsiElement,
                    context: ProcessingContext,
                ): Array<PsiReference> {
                    val settings = element.project.service<Settings>()
                    val phpSettings = element.project.service<PhpPsaSettings>()
                    val providers = phpSettings.methodArgumentProviders

                    if (!settings.pluginEnabled || !phpSettings.enabled || providers.isNullOrEmpty()) {
                        return emptyArray()
                    }

                    val match =
                        PsaPhpMethodArgumentHelper.findProviderForMethodNameElement(element, providers)
                            ?: return emptyArray()

                    return arrayOf(PsaPhpMethodReference(element, match.method))
                }
            }

        registrar.registerReferenceProvider(
            PlatformPatterns.psiElement(StringLiteralExpression::class.java).withLanguage(PhpLanguage.INSTANCE),
            provider,
        )

        registrar.registerReferenceProvider(
            PlatformPatterns.psiElement(ConstantReference::class.java).withLanguage(PhpLanguage.INSTANCE),
            provider,
        )
    }
}
