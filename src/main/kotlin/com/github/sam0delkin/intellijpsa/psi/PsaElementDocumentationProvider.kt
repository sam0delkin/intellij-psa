package com.github.sam0delkin.intellijpsa.psi

import com.intellij.lang.LanguageDocumentation
import com.intellij.lang.documentation.AbstractDocumentationProvider
import com.intellij.psi.PsiElement

class PsaElementDocumentationProvider : AbstractDocumentationProvider() {
    override fun getQuickNavigateInfo(
        element: PsiElement,
        originalElement: PsiElement?,
    ): String? {
        if (element !is PsaElement) return null
        val declaration = element.getDeclarationParent() ?: return null
        return LanguageDocumentation.INSTANCE
            .allForLanguage(declaration.language)
            .mapNotNull { it.getQuickNavigateInfo(declaration, originalElement) }
            .firstOrNull()
    }

    override fun generateDoc(
        element: PsiElement,
        originalElement: PsiElement?,
    ): String? {
        if (element !is PsaElement) return null
        val declaration = element.getDeclarationParent() ?: return null
        return LanguageDocumentation.INSTANCE
            .allForLanguage(declaration.language)
            .mapNotNull { it.generateDoc(declaration, originalElement) }
            .firstOrNull()
    }
}
