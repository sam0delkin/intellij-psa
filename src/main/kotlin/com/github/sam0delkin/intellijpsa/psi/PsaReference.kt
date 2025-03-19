package com.github.sam0delkin.intellijpsa.psi

import com.intellij.psi.PsiElement
import com.intellij.psi.PsiPolyVariantReferenceBase
import com.intellij.psi.ResolveResult

class PsaReference(
    element: PsiElement,
) : PsiPolyVariantReferenceBase<PsiElement>(element) {
    override fun multiResolve(incompleteCode: Boolean): Array<ResolveResult> {
        TODO("Not yet implemented")
    }
}
