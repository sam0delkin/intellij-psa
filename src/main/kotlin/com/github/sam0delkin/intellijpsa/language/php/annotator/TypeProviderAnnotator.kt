package com.github.sam0delkin.intellijpsa.language.php.annotator

import com.github.sam0delkin.intellijpsa.language.php.settings.PhpPsaSettings
import com.github.sam0delkin.intellijpsa.language.php.typeProvider.PSA_TYPE_PROVIDER_ANNOTATOR_KEY
import com.github.sam0delkin.intellijpsa.services.PsaManager
import com.intellij.lang.annotation.AnnotationHolder
import com.intellij.lang.annotation.Annotator
import com.intellij.lang.annotation.HighlightSeverity
import com.intellij.openapi.components.service
import com.intellij.psi.PsiElement
import com.intellij.psi.util.elementType
import com.jetbrains.rd.util.string.printToString

class TypeProviderAnnotator : Annotator {
    override fun annotate(
        element: PsiElement,
        annotator: AnnotationHolder,
    ) {
        val project = element.project
        val psaManager = project.service<PsaManager>()
        val settings = psaManager.getSettings()
        val phpSettings = project.service<PhpPsaSettings>()

        if (
            !settings.pluginEnabled ||
            settings.scriptPath.isNullOrEmpty() ||
            !phpSettings.enabled ||
            !phpSettings.supportsTypeProviders ||
            !phpSettings.debugTypeProvider
        ) {
            return
        }

        val pointer = element.getUserData(PSA_TYPE_PROVIDER_ANNOTATOR_KEY)

        if (null != pointer) {
            val targetElement = pointer.element
            if (null != targetElement) {
                annotator
                    .newAnnotation(HighlightSeverity.WARNING, "PSA Type Provider: ${targetElement.elementType.printToString()}")
                    .range(element)
                    .create()
            }
        }
    }
}
