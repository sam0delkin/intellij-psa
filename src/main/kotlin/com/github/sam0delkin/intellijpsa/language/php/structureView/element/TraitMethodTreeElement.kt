package com.github.sam0delkin.intellijpsa.language.php.structureView.element

import com.intellij.ide.structureView.StructureViewTreeElement
import com.intellij.ide.util.treeView.smartTree.SortableTreeElement
import com.intellij.ide.util.treeView.smartTree.TreeElement
import com.intellij.navigation.ItemPresentation
import com.jetbrains.php.lang.psi.elements.Method

class TraitMethodTreeElement(
    private val method: Method,
) : StructureViewTreeElement,
    SortableTreeElement {
    override fun getValue(): Any = method

    override fun getChildren(): Array<out TreeElement?> = emptyArray()

    override fun getPresentation(): ItemPresentation = method.presentation!!

    override fun navigate(requestFocus: Boolean) {
        method.navigate(requestFocus)
    }

    override fun canNavigate() = method.canNavigate()

    override fun canNavigateToSource() = method.canNavigateToSource()

    override fun getAlphaSortKey(): String = method.name
}
