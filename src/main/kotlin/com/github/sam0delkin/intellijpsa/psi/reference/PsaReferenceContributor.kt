package com.github.sam0delkin.intellijpsa.psi.reference

import com.github.sam0delkin.intellijpsa.completion.AnyCompletionContributor
import com.github.sam0delkin.intellijpsa.index.INDEX_ID
import com.github.sam0delkin.intellijpsa.psi.PsaElement
import com.github.sam0delkin.intellijpsa.services.PsaManager
import com.github.sam0delkin.intellijpsa.util.PsiUtils
import com.intellij.openapi.components.service
import com.intellij.openapi.project.guessProjectDir
import com.intellij.patterns.PlatformPatterns
import com.intellij.psi.PsiElement
import com.intellij.psi.PsiReference
import com.intellij.psi.PsiReferenceContributor
import com.intellij.psi.PsiReferenceProvider
import com.intellij.psi.PsiReferenceRegistrar
import com.intellij.psi.search.GlobalSearchScope
import com.intellij.psi.util.elementType
import com.intellij.util.ProcessingContext
import com.intellij.util.indexing.FileBasedIndex
import com.jetbrains.rd.util.string.printToString

class PsaReferenceContributor : PsiReferenceContributor() {
    override fun registerReferenceProviders(registrar: PsiReferenceRegistrar) {
        registrar.registerReferenceProvider(
            PlatformPatterns.or(
                PlatformPatterns.psiElement(),
                PlatformPatterns.psiFile(),
                PlatformPatterns.psiElement().withParent(PlatformPatterns.psiElement()),
            ),
            object : PsiReferenceProvider() {
                override fun getReferencesByElement(
                    element: PsiElement,
                    context: ProcessingContext,
                ): Array<PsiReference> {
                    if (null == element.containingFile) {
                        return emptyArray()
                    }

                    val project = element.project
                    val psaManager = project.service<PsaManager>()
                    val settings = psaManager.getSettings()

                    if (
                        null != settings.targetElementTypes &&
                        !settings.targetElementTypes!!.contains(element.elementType.printToString())
                    ) {
                        return emptyArray()
                    }

                    if (!settings.pluginEnabled) {
                        return emptyArray()
                    }

                    if (!settings.resolveReferences) {
                        return emptyArray()
                    }

                    if (!settings.supportsStaticCompletions || null === psaManager.getStaticCompletionConfigs()) {
                        return emptyArray()
                    }

                    if (null == settings.goToFilter) {
                        return emptyArray()
                    }

                    val projectDir = project.guessProjectDir()
                    if (null === projectDir) {
                        return emptyArray()
                    }

                    val language = element.containingFile.language
                    var languageString = language.id

                    if (language.baseLanguage !== null && !settings.isLanguageSupported(languageString)) {
                        languageString = language.baseLanguage!!.id
                    }

                    if (!settings.isLanguageSupported(languageString)) {
                        return emptyArray()
                    }

                    val list: ArrayList<PsiReference> = ArrayList()
                    val elements: ArrayList<String> = ArrayList()
                    val index = FileBasedIndex.getInstance()
                    val goToDeclarationHandler = project.service<AnyCompletionContributor.GotoDeclaration>()

                    try {
                        val elementUrl = element.containingFile.virtualFile.url + ":" + element.textOffset
                        val indexKeys =
                            index.getValues(
                                INDEX_ID,
                                elementUrl,
                                GlobalSearchScope.projectScope(project),
                            )

                        for (key in indexKeys) {
                            for (entry in key.entries) {
                                for (keyEl in entry.value) {
                                    val targetEl =
                                        PsiUtils.processLink("file://$keyEl", null, project, false)
                                            ?: continue

                                    val targets =
                                        goToDeclarationHandler.getGotoDeclarationTargets(
                                            targetEl.getOriginalPsiElement(),
                                            -1,
                                            null,
                                        )

                                    if (null == targets) {
                                        continue
                                    }

                                    val filteredTargets =
                                        targets.filter {
                                            if (it is PsaElement) {
                                                return@filter element == it.getOriginalPsiElement()
                                            }

                                            return@filter it == element
                                        }

                                    if (
                                        filteredTargets.isNotEmpty() &&
                                        !elements.contains(keyEl)
                                    ) {
                                        elements.add(keyEl)
                                        list.add(PsaReference(element, targetEl, entry.key))
                                    }
                                }
                            }
                        }
                    } catch (_: IllegalArgumentException) {
                        return emptyArray()
                    }

                    return list.toTypedArray()
                }
            },
        )
    }
}
