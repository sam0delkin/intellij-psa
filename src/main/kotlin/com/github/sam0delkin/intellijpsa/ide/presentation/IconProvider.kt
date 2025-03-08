package com.github.sam0delkin.intellijpsa.ide.presentation

import com.github.sam0delkin.intellijpsa.icons.Icons
import com.github.sam0delkin.intellijpsa.psi.PsaElement
import com.intellij.ide.IconProvider
import com.intellij.psi.PsiElement
import javax.swing.Icon

class IconProvider : IconProvider() {
    override fun getIcon(
        element: PsiElement,
        flags: Int,
    ): Icon? {
        if (element is PsaElement) {
            return Icons.PluginIcon
        }

        return null
    }
}
