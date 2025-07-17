package com.github.sam0delkin.intellijpsa.psi.helper

import com.github.sam0delkin.intellijpsa.model.psi.PsiElementModel
import com.github.sam0delkin.intellijpsa.model.psi.PsiElementPatternModel
import com.github.sam0delkin.intellijpsa.util.PropertyAccessor
import com.intellij.lang.ASTNode
import com.intellij.lang.LighterASTNode
import com.intellij.lang.LighterASTTokenNode
import com.intellij.lang.TreeBackedLighterAST
import com.jetbrains.rd.util.string.printToString
import org.apache.velocity.VelocityContext
import org.apache.velocity.app.Velocity
import java.io.StringWriter

class PsiElementModelHelper {
    companion object {
        fun matches(
            model: PsiElementModel,
            pattern: PsiElementPatternModel,
            checkOptions: Boolean = true,
        ): Boolean {
            if (null !== pattern.withType && pattern.withType != model.elementType) {
                return false
            }

            if (pattern.withText != null && pattern.withText != model.text) {
                return false
            }

            if (null !== pattern.parent && null === model.parent) {
                return false
            }

            if (null !== pattern.parent && !matches(model.parent!!, pattern.parent!!, checkOptions)) {
                return false
            }

            if (null !== pattern.anyParent && null === model.parent) {
                return false
            }

            if (null !== pattern.anyParent) {
                var currentElement = model.parent

                while (true) {
                    if (null === currentElement) {
                        return false
                    }

                    if (matches(currentElement, pattern.anyParent!!, checkOptions)) {
                        return true
                    }

                    currentElement = currentElement.parent
                }
            }

            if (null !== pattern.prev && null === model.prev) {
                return false
            }

            if (null !== pattern.anyPrev && null === model.prev) {
                return false
            }

            if (null !== pattern.anyPrev) {
                var currentElement = model.prev

                while (true) {
                    if (null === currentElement) {
                        return false
                    }

                    if (matches(currentElement, pattern.anyPrev!!, checkOptions)) {
                        return true
                    }

                    currentElement = currentElement.prev
                }
            }

            if (null !== pattern.prev && !matches(model.prev!!, pattern.prev!!, checkOptions)) {
                return false
            }

            if (null !== pattern.next && null === model.next) {
                return false
            }

            if (null !== pattern.anyNext && null === model.next) {
                return false
            }

            if (null !== pattern.anyNext) {
                var currentElement = model.next

                while (true) {
                    if (null === currentElement) {
                        return false
                    }

                    if (matches(currentElement, pattern.anyNext!!, checkOptions)) {
                        return true
                    }

                    currentElement = currentElement.next
                }
            }

            if (null !== pattern.next && !matches(model.next!!, pattern.next!!)) {
                return false
            }

            if (checkOptions && null !== pattern.withOptions) {
                for (option in pattern.withOptions!!) {
                    val propertyValue = PropertyAccessor.getPropertyValue(model, option.key)

                    if (propertyValue != option.value) {
                        return false
                    }
                }
            }

            return true
        }

        fun matches(
            tree: TreeBackedLighterAST,
            element: LighterASTNode,
            pattern: PsiElementPatternModel,
        ): Boolean {
            if (null !== pattern.withType && pattern.withType != element.tokenType.printToString()) {
                return false
            }

            if (element is LighterASTTokenNode && pattern.withText != null && pattern.withText != element.text) {
                return false
            }

            val parent = tree.getParent(element)

            if (null !== pattern.parent && null === parent) {
                return false
            }

            if (null !== pattern.parent && !matches(tree, parent!!, pattern.parent!!)) {
                return false
            }

            if (null !== pattern.anyParent && null === parent) {
                return false
            }

            if (null !== pattern.anyParent) {
                var currentElement = parent

                while (true) {
                    if (null === currentElement) {
                        return false
                    }

                    if (matches(tree, currentElement, pattern.anyParent!!)) {
                        return true
                    }

                    currentElement = tree.getParent(currentElement)
                }
            }

            if (null !== pattern.anyChild && tree.getChildren(element).isEmpty()) {
                return false
            }

            if (null !== pattern.anyChild) {
                val hasAnyChild =
                    tree.getChildren(element).any {
                        var currentElement = it

                        matches(tree, currentElement, pattern.anyParent!!)
                    }

                if (!hasAnyChild) {
                    return false
                }
            }

            return true
        }

        fun matches(
            element: ASTNode,
            pattern: PsiElementPatternModel,
        ): Boolean {
            if (null !== pattern.withType && pattern.withType != element.elementType.printToString()) {
                return false
            }

            if (pattern.withText != null && pattern.withText != element.text) {
                return false
            }

            val parent = element.treeParent

            if (null !== pattern.parent && null === parent) {
                return false
            }

            if (null !== pattern.parent && !matches(parent!!, pattern.parent!!)) {
                return false
            }

            if (null !== pattern.anyParent && null === parent) {
                return false
            }

            if (null !== pattern.anyParent) {
                var currentElement = parent

                while (true) {
                    if (null === currentElement) {
                        return false
                    }

                    if (!matches(currentElement, pattern.anyParent!!)) {
                        return false
                    }

                    currentElement = currentElement.treeParent
                }
            }

            if (null !== pattern.anyChild && element.getChildren(null).isEmpty()) {
                return false
            }

            if (null !== pattern.anyChild) {
                val hasAnyChild =
                    element.getChildren(null).any {
                        var currentElement = it

                        matches(currentElement, pattern.anyChild!!)
                    }

                if (!hasAnyChild) {
                    return false
                }
            }

            if (null !== pattern.withMatcher) {
                val context = VelocityContext()
                val writer = StringWriter()
                context.put(
                    "element",
                    element.psi,
                )
                try {
                    Velocity.evaluate(context, writer, "", pattern.withMatcher)
                } catch (_: Exception) {
                    return false
                }

                val result = writer.buffer.toString().trim()
                writer.flush()

                return result == "true" || result == "1"
            }

            return true
        }

        fun toPattern(model: PsiElementModel): PsiElementPatternModel =
            PsiElementPatternModel(
                withType = model.elementType,
                withText = model.text,
                parent = if (model.parent != null) toPattern(model.parent!!) else null,
                anyParent = null,
                prev = if (model.prev != null) toPattern(model.prev!!) else null,
                anyPrev = null,
                next = if (model.next != null) toPattern(model.next!!) else null,
                anyNext = null,
                withOptions = PropertyAccessor.convertToPropertyPathMap(model.options, "options"),
            )
    }
}
