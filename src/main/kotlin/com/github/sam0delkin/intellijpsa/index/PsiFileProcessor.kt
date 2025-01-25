package com.github.sam0delkin.intellijpsa.index

import com.github.sam0delkin.intellijpsa.services.CompletionService
import com.intellij.psi.PsiElement
import com.intellij.psi.search.PsiElementProcessor
import com.intellij.psi.util.elementType
import com.jetbrains.rd.util.string.printToString

class PsiFileProcessor(
    private val completionService: CompletionService,
    private val notIndexedElements: ArrayList<String>,
) : PsiElementProcessor<PsiElement> {
    override fun execute(currentElement: PsiElement): Boolean {
        val settings = this.completionService.getSettings()
        if (!settings.elementTypes.contains(currentElement.elementType.printToString())) {
            return true
        }

        val fileUrl = currentElement.containingFile.virtualFile?.url

        if (null === fileUrl) {
            return true
        }

        val key = fileUrl + "::" + currentElement.textRange.startOffset

        if (!notIndexedElements.contains(key)) {
            notIndexedElements.add(key)
        }

        return true
    }
}
