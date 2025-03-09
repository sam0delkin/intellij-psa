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

            if (null !== pattern.prev && null === model.prev) {
                return false
            }

            if (null !== pattern.prev && !matches(model.prev!!, pattern.prev!!)) {
                return false
            }

            if (null !== pattern.next && null === model.next) {
                return false
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
                prev = if (model.prev != null) toPattern(model.prev!!) else null,
                next = if (model.next != null) toPattern(model.next!!) else null,
                withOptions = PropertyAccessor.convertToPropertyPathMap(model.options, "options"),
            )
    }
}
