package com.github.sam0delkin.intellijpsa.util

import com.intellij.psi.PsiElement
import com.intellij.psi.util.elementType
import com.jetbrains.rd.util.string.printToString

class PsiUtils {
    companion object {
        private const val parentCount = 2
        val getPsiElementLabel = fun (element: PsiElement): String {
            if ("FILE" === element.elementType.printToString()) {
                return ""
            }

            var label = element.elementType.printToString() + ":" + element.text + ":"
            var parent = element.parent

            for (i in 0..parentCount) {
                if (null === parent) {
                    break
                }
                label += parent.elementType.printToString() + ":"
                parent = parent.parent
            }

            return label
        }

        val getPsiElementPath = fun (element: PsiElement): String {
            var currentElement = element
            var path = currentElement.elementType.printToString() + "."

            while (null !== currentElement.parent) {
                path += currentElement.parent.elementType.printToString() + "."
                currentElement = currentElement.parent
            }

            return path
        }
    }
}