package com.github.sam0delkin.intellijpsa.psi

import com.github.sam0delkin.intellijpsa.services.PsaManager
import com.github.sam0delkin.intellijpsa.util.PsiUtil
import com.intellij.openapi.components.service
import com.intellij.patterns.PlatformPatterns
import com.intellij.psi.PsiElement
import com.intellij.psi.PsiReference
import com.intellij.psi.PsiReferenceContributor
import com.intellij.psi.PsiReferenceProvider
import com.intellij.psi.PsiReferenceRegistrar
import com.intellij.util.ProcessingContext

class PsaReferenceContributor : PsiReferenceContributor() {
    override fun registerReferenceProviders(registrar: PsiReferenceRegistrar) {
        registrar.registerReferenceProvider(
            PlatformPatterns.psiElement(),
            object : PsiReferenceProvider() {
                override fun getReferencesByElement(
                    element: PsiElement,
                    context: ProcessingContext,
                ): Array<PsiReference> {
                    val project = element.project
                    val psaManager = project.service<PsaManager>()
                    val settings = psaManager.getSettings()

                    if (!settings.pluginEnabled) {
                        return emptyArray()
                    }

                    if (!settings.supportsStaticCompletions || null === settings.staticCompletionConfigs) {
                        return emptyArray()
                    }

                    val list: ArrayList<PsiReference> = ArrayList()

                    for (i in settings.staticCompletionConfigs!!) {
                        if (null === i.patterns) {
                            continue
                        }

                        for (j in i.patterns) {
                            if (i.completions != null) {
                                for (k in i.completions.completions!!) {
                                    val linkData = k.link ?: ""
                                    var text = k.text ?: linkData
                                    if (k.presentableText != null) {
                                        text = k.presentableText!!
                                    }
                                    val psiReference = PsiUtil.processLink(linkData, text, project)

//                                    list.addAll(psiReferences.map { it.reference }.filter { null !== it }.toCollection())
                                }
                            }
                        }
                    }

                    return list.toTypedArray()
                }
            },
        )
    }
}
