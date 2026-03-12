package com.github.sam0delkin.intellijpsa.language.php.structureView.element

import com.intellij.ide.structureView.StructureViewTreeElement
import com.intellij.ide.util.treeView.smartTree.TreeElement
import com.intellij.navigation.ItemPresentation
import com.jetbrains.php.lang.psi.elements.PhpClass

class TraitGroupTreeElement(
    private val trait: PhpClass,
    private val ownerClass: PhpClass,
) : StructureViewTreeElement {
    override fun getValue(): Any = trait

    override fun getChildren(): Array<out TreeElement?> =
        trait.methods
            .map { TraitMethodTreeElement(it) }
            .toTypedArray()

    override fun getPresentation(): ItemPresentation =
        object : ItemPresentation {
            override fun getPresentableText() = trait.name

            override fun getLocationString() = "trait"

            override fun getIcon(unused: Boolean) = trait.getIcon(0)
        }

    override fun navigate(requestFocus: Boolean) {
        trait.navigate(requestFocus)
    }

    override fun canNavigate() = trait.canNavigate()

    override fun canNavigateToSource() = trait.canNavigateToSource()
}
