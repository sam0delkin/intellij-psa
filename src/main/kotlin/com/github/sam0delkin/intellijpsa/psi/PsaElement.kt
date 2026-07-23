package com.github.sam0delkin.intellijpsa.psi

import com.github.sam0delkin.intellijpsa.icons.Icons
import com.github.sam0delkin.intellijpsa.util.PsiUtils
import com.intellij.lang.ASTNode
import com.intellij.navigation.ItemPresentation
import com.intellij.navigation.NavigationItem
import com.intellij.psi.PsiElement
import com.intellij.psi.PsiFile
import com.intellij.psi.PsiNamedElement
import com.intellij.psi.impl.FakePsiElement
import javax.swing.Icon

class PsaElement(
    private val element: PsiElement,
    private val text: String,
) : FakePsiElement() {
    override fun getParent(): PsiElement = element.parent

    override fun isValid(): Boolean = null != element.parent && super.isValid()

    override fun getNavigationElement(): PsiElement = findDeclarationParent() ?: element

    override fun getName(): String =
        (findDeclarationParent() as? NavigationItem)?.presentation?.presentableText
            ?: PsiUtils.normalizeElementText(text)

    override fun getPresentation(): ItemPresentation {
        val native = (findDeclarationParent() as? NavigationItem)?.presentation
        return object : ItemPresentation {
            override fun getPresentableText(): String = native?.presentableText ?: PsiUtils.normalizeElementText(text)

            override fun getLocationString(): String? =
                native?.locationString ?: run {
                    val lineNumber =
                        element.containingFile!!
                            .getText()
                            .substring(0, element.textOffset)
                            .split("\n")
                            .size
                    element.containingFile.name + ":" + lineNumber
                }

            override fun getIcon(unused: Boolean): Icon = Icons.PluginIcon
        }
    }

    override fun toString(): String {
        val label =
            (findDeclarationParent() as? NavigationItem)?.presentation?.presentableText
                ?: PsiUtils.normalizeElementText(text)
        return "$label (${element.containingFile.name})"
    }

    fun getOriginalPsiElement(): PsiElement = element

    override fun getOriginalElement(): PsiElement = element

    override fun getNode(): ASTNode? = element.node

    internal fun getDeclarationParent(): PsiElement? = findDeclarationParent()

    private fun findDeclarationParent(): PsiElement? {
        var current: PsiElement? = element
        while (current != null && current !is PsiFile) {
            if (current is PsiNamedElement &&
                current.name != null &&
                (current as? NavigationItem)?.presentation?.presentableText != null
            ) {
                return current
            }
            current = current.parent
        }
        return null
    }
}
