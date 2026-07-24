package com.github.sam0delkin.intellijpsa.psi

import com.intellij.navigation.NavigationItem
import com.intellij.psi.ElementDescriptionLocation
import com.intellij.psi.ElementDescriptionProvider
import com.intellij.psi.PsiElement
import com.intellij.usageView.UsageViewLongNameLocation
import com.intellij.usageView.UsageViewNodeTextLocation
import com.intellij.usageView.UsageViewTypeLocation

class PsaElementDescriptionProvider : ElementDescriptionProvider {
    override fun getElementDescription(
        element: PsiElement,
        location: ElementDescriptionLocation,
    ): String? {
        if (element !is PsaElement) return null
        return when (location) {
            UsageViewTypeLocation.INSTANCE -> "PSA"
            UsageViewLongNameLocation.INSTANCE, UsageViewNodeTextLocation.INSTANCE ->
                (element as? NavigationItem)?.presentation?.presentableText
            else -> null
        }
    }
}
