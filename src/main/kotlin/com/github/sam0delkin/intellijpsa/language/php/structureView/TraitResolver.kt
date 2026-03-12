package com.github.sam0delkin.intellijpsa.language.php.structureView

import com.jetbrains.php.lang.psi.elements.PhpClass

object TraitResolver {
    fun collectTraits(phpClass: PhpClass): Set<PhpClass> {
        val result = mutableSetOf<PhpClass>()
        collect(phpClass, result)
        return result
    }

    private fun collect(
        phpClass: PhpClass,
        result: MutableSet<PhpClass>,
    ) {
        for (trait in phpClass.traits) {
            if (result.add(trait)) {
                collect(trait, result)
            }
        }
    }
}
