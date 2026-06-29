package com.github.sam0delkin.intellijpsa.language.javascript.methodArguments

import com.intellij.lang.javascript.psi.JSFunction
import com.intellij.openapi.util.TextRange
import com.intellij.psi.ElementManipulators
import com.intellij.psi.PsiElement
import com.intellij.psi.PsiReferenceBase

class JsMethodReference(
    element: PsiElement,
    private val function: JSFunction,
) : PsiReferenceBase<PsiElement>(element) {
    override fun resolve(): PsiElement = function

    override fun isReferenceTo(element: PsiElement): Boolean = element == function || element == function.nameIdentifier

    override fun handleElementRename(newElementName: String): PsiElement {
        val manipulator = ElementManipulators.getManipulator(this.element) ?: return this.element
        return manipulator.handleContentChange(this.element, rangeInElement, newElementName) ?: this.element
    }

    override fun calculateDefaultRangeInElement(): TextRange {
        val text = element.text
        if (text.length >= 2 && (text.first() == '\'' || text.first() == '"' || text.first() == '`')) {
            return TextRange(1, text.length - 1)
        }
        return try {
            super.calculateDefaultRangeInElement()
        } catch (_: Exception) {
            TextRange(0, element.textLength)
        }
    }
}
