package com.github.sam0delkin.intellijpsa.psi.reference

import com.intellij.openapi.util.TextRange
import com.intellij.psi.PsiElement
import com.intellij.psi.PsiReferenceBase

class PsaReference(
    originalElement: PsiElement,
    private val reference: PsiElement,
) : PsiReferenceBase<PsiElement>(originalElement) {
    override fun resolve(): PsiElement = this.reference

    override fun handleElementRename(newElementName: String): PsiElement {
        try {
            return super.handleElementRename(newElementName)
        } catch (_: Exception) {
            return this.element
        }
    }

    override fun calculateDefaultRangeInElement(): TextRange {
        try {
            return super.calculateDefaultRangeInElement()
        } catch (_: Exception) {
            return TextRange(0, element.textLength)
        }
    }

    override fun isReferenceTo(element: PsiElement): Boolean = true
}
