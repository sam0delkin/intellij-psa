package com.github.sam0delkin.intellijpsa.psi

import com.github.sam0delkin.intellijpsa.model.PsiElementModel
import com.github.sam0delkin.intellijpsa.model.PsiElementPatternModel
import com.github.sam0delkin.intellijpsa.util.PropertyAccessor

class PsiElementModelHelper {
    companion object {
        fun matches(
            model: PsiElementModel,
            pattern: PsiElementPatternModel,
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

            if (null !== pattern.parent && !matches(model.parent!!, pattern.parent!!)) {
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

                    if (matches(currentElement, pattern.anyParent!!)) {
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

                    if (matches(currentElement, pattern.anyPrev!!)) {
                        return true
                    }

                    currentElement = currentElement.prev
                }
            }

            if (null !== pattern.prev && !matches(model.prev!!, pattern.prev!!)) {
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

                    if (matches(currentElement, pattern.anyNext!!)) {
                        return true
                    }

                    currentElement = currentElement.next
                }
            }

            if (null !== pattern.next && !matches(model.next!!, pattern.next!!)) {
                return false
            }

            if (null !== pattern.withOptions) {
                for (option in pattern.withOptions!!) {
                    val propertyValue = PropertyAccessor.getPropertyValue(model, option.key)

                    if (propertyValue != option.value) {
                        return false
                    }
                }
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
