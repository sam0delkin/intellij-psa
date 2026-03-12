package com.github.sam0delkin.intellijpsa.language.php.structureView

import com.github.sam0delkin.intellijpsa.language.php.settings.PhpPsaSettings
import com.github.sam0delkin.intellijpsa.language.php.structureView.element.TraitGroupTreeElement
import com.intellij.ide.structureView.StructureViewExtension
import com.intellij.ide.structureView.StructureViewTreeElement
import com.intellij.openapi.components.service
import com.intellij.openapi.editor.Editor
import com.intellij.openapi.project.IndexNotReadyException
import com.intellij.psi.PsiElement
import com.jetbrains.php.lang.psi.elements.PhpClass

class TraitMethodStructureViewExtension : StructureViewExtension {
    override fun getType(): Class<out PsiElement?> = PhpClass::class.java

    override fun getChildren(parent: PsiElement?): Array<out StructureViewTreeElement?> {
        val settings = parent?.project?.service<PhpPsaSettings>()
        if (settings?.enabled != true) {
            return emptyArray()
        }

        val phpClass = parent as? PhpClass ?: return emptyArray()

        try {
            val traits = TraitResolver.collectTraits(phpClass)
            if (traits.isEmpty()) return emptyArray()

            val groups =
                traits.map {
                    TraitGroupTreeElement(it, phpClass)
                }

            return groups.toTypedArray()
        } catch (_: IndexNotReadyException) {
            return emptyArray()
        }
    }

    override fun getCurrentEditorElement(
        p0: Editor?,
        p1: PsiElement?,
    ): Any? = null
}
