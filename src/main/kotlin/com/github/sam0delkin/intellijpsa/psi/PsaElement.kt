package com.github.sam0delkin.intellijpsa.psi

import com.github.sam0delkin.intellijpsa.icons.Icons
import com.github.sam0delkin.intellijpsa.util.PsiUtil
import com.intellij.lang.ASTNode
import com.intellij.navigation.ItemPresentation
import com.intellij.psi.PsiElement
import com.intellij.psi.impl.FakePsiElement
import javax.swing.Icon

class PsaElement(
    private val element: PsiElement,
    private val text: String,
) : FakePsiElement() {
    override fun getParent(): PsiElement = element.parent

    override fun isValid(): Boolean = null != element.parent && super.isValid()

    override fun getNavigationElement(): PsiElement = element

    override fun getPresentation(): ItemPresentation =
        object : ItemPresentation {
            override fun getPresentableText(): String = PsiUtil.normalizeElementText(text)

            override fun getLocationString(): String {
                val lineNumber: Int =
                    element.containingFile!!
                        .getText()
                        .substring(0, element.textOffset)
                        .split("\n")
                        .size

                return element.containingFile.name + ":" + lineNumber.toString()
            }

            override fun getIcon(unused: Boolean): Icon = Icons.PluginIcon
        }

    override fun toString(): String = "$text (${element.containingFile.name})"

    fun getOriginalPsiElement(): PsiElement = element

    override fun getOriginalElement(): PsiElement = element

    override fun getNode(): ASTNode? = element.node
}
