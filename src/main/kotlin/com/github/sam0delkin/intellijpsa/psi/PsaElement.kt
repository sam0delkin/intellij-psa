package com.github.sam0delkin.intellijpsa.psi

import com.github.sam0delkin.intellijpsa.icons.Icons
import com.intellij.navigation.ItemPresentation
import com.intellij.psi.PsiElement
import com.intellij.psi.impl.FakePsiElement
import javax.swing.Icon

class PsaElement(
    private val element: PsiElement,
    private val text: String,
) : FakePsiElement() {
    override fun getParent(): PsiElement = element.parent

    override fun getNavigationElement(): PsiElement = element

    override fun getPresentation(): ItemPresentation =
        object : ItemPresentation {
            override fun getPresentableText(): String = text

            override fun getLocationString(): String = text

            override fun getIcon(unused: Boolean): Icon = Icons.PluginIcon
        }

    override fun toString(): String = "$text (${element.containingFile.name})"
}
