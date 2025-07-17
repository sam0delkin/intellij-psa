package com.github.sam0delkin.intellijpsa.language.php.typeProvider

import com.github.sam0delkin.intellijpsa.language.php.services.PhpPsaManager
import com.github.sam0delkin.intellijpsa.psi.helper.PsiElementModelHelper
import com.github.sam0delkin.intellijpsa.services.PsaManager
import com.intellij.openapi.components.service
import com.intellij.openapi.project.Project
import com.intellij.openapi.util.Key
import com.intellij.psi.PsiElement
import com.intellij.psi.SmartPointerManager
import com.intellij.psi.SmartPsiElementPointer
import com.intellij.psi.util.nextLeafs
import com.jetbrains.php.lang.PhpLanguage
import com.jetbrains.php.lang.psi.elements.PhpNamedElement
import com.jetbrains.php.lang.psi.resolve.types.PhpType
import com.jetbrains.php.lang.psi.resolve.types.PhpTypeProvider4

val PSA_TYPE_PROVIDER_ANNOTATOR_KEY = Key<SmartPsiElementPointer<PsiElement>>("com.github.sam0delkin.intellijpsa.php.PsaTypeProvider")
val PSA_TYPE_KEY = Key<String>("com.github.sam0delkin.intellijpsa.php.PsaType")

class PsaTypeProvider : PhpTypeProvider4 {
    override fun getKey(): Char = '∞'

    override fun getType(element: PsiElement?): PhpType? {
        if (null == element) {
            return null
        }

        val project = element.project
        val psaManager = project.service<PsaManager>()
        val phpPsaManager = project.service<PhpPsaManager>()
        val settings = psaManager.getSettings()
        val phpSettings = phpPsaManager.getSettings()

        if (
            !settings.pluginEnabled ||
            settings.scriptPath.isNullOrEmpty() ||
            !phpSettings.enabled ||
            !phpSettings.supportsTypeProviders
        ) {
            return null
        }

        element.nextLeafs.first().putUserData(PSA_TYPE_PROVIDER_ANNOTATOR_KEY, SmartPointerManager.createPointer(element))

        var type: String? = null
        try {
            phpSettings.typeProviders?.forEach {
                if (it.language != PhpLanguage.INSTANCE.id) {
                    return@forEach
                }

                if (!PsiElementModelHelper.matches(element.node, it.pattern!!)) {
                    return@forEach
                }

//                val model = psaManager.psiElementToModel(element)
//
//                if (!PsiElementModelHelper.matches(model, it.pattern!!)) {
//                    return null
//                }

                type = it.type

                return PhpType().add(type)
            }
        } catch (_: Throwable) {
            return null
        } finally {
            element.putUserData(PSA_TYPE_KEY, type)
        }

        return null
    }

    override fun complete(
        p0: String?,
        p1: Project?,
    ): PhpType? = null

    override fun getBySignature(
        p0: String?,
        p1: MutableSet<String>?,
        p2: Int,
        p3: Project?,
    ): MutableCollection<out PhpNamedElement> = mutableListOf()
}
