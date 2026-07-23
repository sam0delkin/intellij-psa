package com.github.sam0delkin.intellijpsa.language.php.methodArguments

import com.intellij.openapi.util.TextRange
import com.intellij.psi.PsiElement
import com.intellij.psi.PsiReferenceBase
import com.jetbrains.php.lang.psi.elements.Parameter

class PsaPhpDynamicArgumentReference(
    element: PsiElement,
    private val parameter: Parameter,
) : PsiReferenceBase<PsiElement>(element, TextRange(0, element.textLength)) {
    override fun resolve(): PsiElement = parameter

    override fun isReferenceTo(element: PsiElement): Boolean = element.manager.areElementsEquivalent(element, parameter)
}
