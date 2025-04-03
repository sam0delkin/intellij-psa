package com.github.sam0delkin.intellijpsa.lineMarkerProvider

import com.github.sam0delkin.intellijpsa.completion.AnyCompletionContributor
import com.github.sam0delkin.intellijpsa.icons.Icons
import com.github.sam0delkin.intellijpsa.index.INDEX_ID
import com.github.sam0delkin.intellijpsa.services.PsaManager
import com.github.sam0delkin.intellijpsa.util.PsiUtil
import com.intellij.codeInsight.daemon.LineMarkerInfo
import com.intellij.codeInsight.daemon.LineMarkerProvider
import com.intellij.codeInsight.navigation.NavigationGutterIconBuilder
import com.intellij.openapi.components.service
import com.intellij.psi.PsiElement
import com.intellij.psi.PsiReferenceService
import com.intellij.psi.impl.source.resolve.reference.ReferenceProvidersRegistry
import com.intellij.psi.util.elementType
import com.intellij.util.indexing.FileBasedIndex
import com.jetbrains.rd.util.string.printToString

class PsaLineMarkerProvider : LineMarkerProvider {
    override fun getLineMarkerInfo(element: PsiElement): LineMarkerInfo<*>? = null

    override fun collectSlowLineMarkers(
        elements: MutableList<out PsiElement>,
        result: MutableCollection<in LineMarkerInfo<*>>,
    ) {
        if (elements.isEmpty()) {
            return
        }

        val firstElement = elements.first()
        val psaManager = firstElement.project.service<PsaManager>()
        val settings = psaManager.getSettings()
        val index = FileBasedIndex.getInstance()
        val goToDeclarationHandler = firstElement.project.service<AnyCompletionContributor.GotoDeclaration>()

        if (
            !settings.pluginEnabled ||
            !settings.resolveReferences ||
            null == settings.targetElementTypes ||
            !settings.supportsStaticCompletions ||
            !settings.annotateUndefinedElements ||
            psaManager.staticCompletionConfigs == null ||
            psaManager.staticCompletionConfigs!!.isEmpty()
        ) {
            return
        }

        val fileData = index.getFileData(INDEX_ID, firstElement.containingFile.virtualFile, firstElement.project)

        elements.map { element ->
            var processed = false
            if (!settings.targetElementTypes!!.contains(element.elementType.printToString())) {
                return@map
            }

            fileData.values.map {
                it?.map {
                    val sourceEl = PsiUtil.processLink("file://$it", null, element.project, false)
                    if (sourceEl != null && sourceEl.getOriginalPsiElement().textOffset == element.textOffset) {
                        val targets =
                            goToDeclarationHandler.getGotoDeclarationTargets(
                                element,
                                -1,
                                null,
                            )
                        if (null !== targets && targets.isNotEmpty()) {
                            processed = true
                        }
                    }
                }
            }

            if (!processed) {
                val references = ReferenceProvidersRegistry.getReferencesFromProviders(element, PsiReferenceService.Hints.NO_HINTS)
                if (references.isNotEmpty()) {
                    val el = NavigationGutterIconBuilder.create(Icons.PluginIcon)
                    val resolvedReferences = references.map { it.resolve() }

                    el.setTargets(resolvedReferences)
                    @Suppress("DialogTitleCapitalization")
                    el.setTooltipText("PSA Reference")
                    result.add(el.createLineMarkerInfo(element))
                }
            }
        }
    }
}
