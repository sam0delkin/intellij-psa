package com.github.sam0delkin.intellijpsa.psi.reference

import com.github.sam0delkin.intellijpsa.completion.AnyCompletionContributor
import com.github.sam0delkin.intellijpsa.index.INDEX_ID
import com.github.sam0delkin.intellijpsa.psi.PsaElement
import com.github.sam0delkin.intellijpsa.services.PsaManager
import com.github.sam0delkin.intellijpsa.util.PsiUtil
import com.intellij.openapi.components.service
import com.intellij.openapi.project.guessProjectDir
import com.intellij.openapi.vfs.newvfs.persistent.PersistentFS
import com.intellij.patterns.PlatformPatterns
import com.intellij.psi.PsiElement
import com.intellij.psi.PsiReference
import com.intellij.psi.PsiReferenceContributor
import com.intellij.psi.PsiReferenceProvider
import com.intellij.psi.PsiReferenceRegistrar
import com.intellij.psi.impl.source.tree.LeafPsiElement
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

                    val targetElements = mutableListOf(element)
                    val project = element.project
                    val psaManager = project.service<PsaManager>()
                    val settings = psaManager.getSettings()

                    if (element is LeafPsiElement) {
                        targetElements.add(element.parent)

                        if (
                            null != settings.targetElementTypes &&
                            !settings.targetElementTypes!!.contains(element.elementType.printToString()) &&
                            !settings.targetElementTypes!!.contains(element.parent.elementType.printToString())
                        ) {
                            return emptyArray()
                        }
                    } else {
                        if (
                            null != settings.targetElementTypes &&
                            !settings.targetElementTypes!!.contains(element.elementType.printToString())
                        ) {
                            return emptyArray()
                        }
                    }

                    if (!settings.pluginEnabled) {
                        return emptyArray()
                    }

                    if (!settings.resolveReferences) {
                        return emptyArray()
                    }

                    if (!settings.supportsStaticCompletions || null === psaManager.staticCompletionConfigs) {
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
                        val indexKeys =
                            index.getAllKeys(
                                INDEX_ID,
                                project,
                            )

                        for (fileId in indexKeys) {
                            val virtualFile = PersistentFS.getInstance().findFileById(fileId) ?: continue
                            if (virtualFile.url.indexOf(projectDir.url) != 0) {
                                continue
                            }

                            val fileData = index.getFileData(INDEX_ID, virtualFile, project)

                            fileData.values.map {
                                it?.map {
                                    val indexEl = it
                                    val sourceEls = it.value.split(",")
                                    sourceEls.map(
                                        {
                                            val targetEl = PsiUtil.processLink("file://" + indexEl.key, null, project, false)
                                            val sourceEl = PsiUtil.processLink(it, null, project)
                                            if (null == targetEl ||
                                                null == sourceEl ||
                                                sourceEl.getOriginalPsiElement().containingFile != element.containingFile
                                            ) {
                                                @Suppress("LABEL_NAME_CLASH")
                                                return@map
                                            }

                                            val targets =
                                                goToDeclarationHandler.getGotoDeclarationTargets(
                                                    targetEl.getOriginalPsiElement(),
                                                    -1,
                                                    null,
                                                )

                                            if (null == targets) {
                                                @Suppress("LABEL_NAME_CLASH")
                                                return@map
                                            }

                                            targets.map {
                                                var el = it
                                                if (el is PsaElement) {
                                                    el = el.getOriginalPsiElement()
                                                }
                                                targetElements.map { targetElement ->
                                                    if (
                                                        targetElement == sourceEl.getOriginalPsiElement() &&
                                                        el == sourceEl.getOriginalPsiElement() &&
                                                        !elements.contains(indexEl.key)
                                                    ) {
                                                        elements.add(indexEl.key)
                                                        list.add(PsaReference(targetElement, targetEl))
                                                    }
                                                }
                                            }
                                        },
                                    )
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
