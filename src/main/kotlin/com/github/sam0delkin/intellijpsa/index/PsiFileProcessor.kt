package com.github.sam0delkin.intellijpsa.index

import com.github.sam0delkin.intellijpsa.services.CompletionService
import com.github.sam0delkin.intellijpsa.util.PsiUtils
import com.intellij.psi.PsiElement
import com.intellij.psi.search.PsiElementProcessor
import com.intellij.psi.util.elementType
import com.intellij.refactoring.suggested.startOffset
import com.jetbrains.rd.util.string.printToString
import java.io.File

class PsiFileProcessor(
    private val completionService: CompletionService,
    private val notIndexedElements: ArrayList<String>,
    private val completionResultFilePaths: HashMap<String, String>,
    private val goToResultFilePaths: HashMap<String, String>,
    private val resultMap: HashMap<String, String>
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

        var goToResult: String?
        var completionResult: String?
        try {
            val goToKey = PsaIndex.generateGoToKey(currentElement)
            goToResult = this.goToResultFilePaths.get(goToKey)

            if (null !== goToResult) {
                val file = File(goToResult)
                goToResult = file.readText()

                resultMap.set(goToKey, goToResult)

                file.delete()
                this.goToResultFilePaths.remove(goToKey)
            }
        } catch (_: Exception) {
            return true
        }

        try {
            val completionsKey = PsaIndex.generateCompletionsKey(currentElement)
            completionResult = this.completionResultFilePaths.get(completionsKey)

            if (null !== completionResult) {
                val file = File(completionResult)
                completionResult = file.readText()

                resultMap.set(completionsKey, completionResult)

                file.delete()
                this.completionResultFilePaths.remove(completionsKey)
            }
        } catch (_: Exception) {
            return true
        }

        if (null !== goToResult || null !== completionResult) {
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