package com.github.sam0delkin.intellijpsa.index

import com.github.sam0delkin.intellijpsa.services.CompletionService
import com.github.sam0delkin.intellijpsa.util.PsiUtils
import com.intellij.psi.PsiElement
import com.intellij.psi.search.PsiElementProcessor
import com.intellij.psi.util.elementType
import com.intellij.refactoring.suggested.startOffset
import com.jetbrains.rd.util.string.printToString

class PsiFileProcessor(
    private val completionService: CompletionService,
    private val notIndexedElements: ArrayList<String>,
) :
    PsiElementProcessor<PsiElement> {
    override fun execute(currentElement: PsiElement): Boolean {
        val settings = this.completionService.getSettings()
        if (!settings.elementTypes.contains(currentElement.elementType.printToString())) {
            return true
        }

        val path = PsiUtils.getPsiElementLabel(currentElement)
        if (!settings.elementPaths.contains(path)) {
            return true
        }

        val fileUrl = currentElement.containingFile.virtualFile.url
        val key = fileUrl + "::" + currentElement.startOffset

        if (!notIndexedElements.contains(key)) {
            notIndexedElements.add(key)
        }

        return true
    }
}