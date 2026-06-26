package com.github.sam0delkin.intellijpsa.language.php.methodArguments

import com.intellij.openapi.util.TextRange
import com.intellij.psi.ElementManipulators
import com.intellij.psi.PsiElement
import com.intellij.psi.PsiReferenceBase
import com.jetbrains.php.lang.psi.elements.ConstantReference
import com.jetbrains.php.lang.psi.elements.Method
import com.jetbrains.php.lang.psi.elements.StringLiteralExpression

class PsaPhpMethodReference(
    element: PsiElement,
    private val method: Method,
) : PsiReferenceBase<PsiElement>(element) {
    override fun resolve(): PsiElement = method

    override fun isReferenceTo(element: PsiElement): Boolean = element == method

    override fun handleElementRename(newElementName: String): PsiElement {
        val manipulator = ElementManipulators.getManipulator(this.element) ?: return this.element
        return manipulator.handleContentChange(this.element, rangeInElement, newElementName) ?: this.element
    }

    override fun calculateDefaultRangeInElement(): TextRange {
        val el = element
        if (el is StringLiteralExpression) {
            val valueRange = el.valueRange
            if (valueRange != null) return valueRange
        }
        if (el is ConstantReference) {
            return TextRange(0, element.textLength)
        }
        return try {
            super.calculateDefaultRangeInElement()
        } catch (_: Exception) {
            TextRange(0, element.textLength)
        }
    }
}
