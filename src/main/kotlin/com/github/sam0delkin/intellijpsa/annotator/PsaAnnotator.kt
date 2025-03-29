package com.github.sam0delkin.intellijpsa.annotator

import com.github.sam0delkin.intellijpsa.completion.AnyCompletionContributor
import com.github.sam0delkin.intellijpsa.completion.RETURN_ALL_STATIC_COMPLETIONS
import com.github.sam0delkin.intellijpsa.index.INDEX_ID
import com.github.sam0delkin.intellijpsa.services.PsaManager
import com.github.sam0delkin.intellijpsa.util.PsiUtil
import com.intellij.lang.annotation.AnnotationHolder
import com.intellij.lang.annotation.Annotator
import com.intellij.lang.annotation.HighlightSeverity
import com.intellij.openapi.components.service
import com.intellij.openapi.util.Key
import com.intellij.psi.PsiElement
import com.intellij.psi.util.elementType
import com.intellij.util.indexing.FileBasedIndex
import com.jetbrains.rd.util.string.printToString

val ANNOTATOR_COMPLETION_TITLE = Key<String>("PsaAnnotator")

@Suppress("DialogTitleCapitalization")
class PsaAnnotator : Annotator {
    override fun annotate(
        element: PsiElement,
        holder: AnnotationHolder,
    ) {
        val psaManager = element.project.service<PsaManager>()
        val settings = psaManager.getSettings()
        val index = FileBasedIndex.getInstance()
        val goToDeclarationHandler = element.project.service<AnyCompletionContributor.GotoDeclaration>()

        if (
            !settings.pluginEnabled ||
            !settings.resolveReferences ||
            !settings.supportsStaticCompletions ||
            !settings.annotateUndefinedElements ||
            psaManager.staticCompletionConfigs == null ||
            psaManager.staticCompletionConfigs!!.isEmpty()
        ) {
            return
        }

        if (!settings.isElementTypeMatchingFilter(element.elementType.printToString())) {
            return
        }

        val fileData = index.getFileData(INDEX_ID, element.containingFile.virtualFile, element.project)
        var processed = false

        fileData.values.map {
            it?.map {
                val sourceEl = PsiUtil.processLink("file://" + it.key, null, element.project, false)
                if (sourceEl != null && sourceEl.getOriginalPsiElement().textOffset == element.textOffset) {
                    processed = true
                    val targets =
                        goToDeclarationHandler.getGotoDeclarationTargets(
                            element,
                            -1,
                            null,
                        )
                    if (null !== targets && targets.isNotEmpty()) {
                        holder
                            .newAnnotation(HighlightSeverity.INFORMATION, "PSA Reference")
                            .range(element)
                            .create()
                    } else {
                        val completionTitle = element.getUserData(ANNOTATOR_COMPLETION_TITLE)
                        if (null != completionTitle) {
                            holder
                                .newAnnotation(
                                    HighlightSeverity.WEAK_WARNING,
                                    "Undefined PSA Reference for completion: \"$completionTitle\"",
                                ).range(element)
                                .create()
                        } else {
                            holder
                                .newAnnotation(HighlightSeverity.WEAK_WARNING, "Undefined PSA Reference")
                                .range(element)
                                .create()
                        }
                    }
                }
            }
        }

        if (!processed) {
            val completions =
                goToDeclarationHandler.getGotoDeclarationTargets(
                    element,
                    RETURN_ALL_STATIC_COMPLETIONS,
                    null,
                )
            val targets =
                goToDeclarationHandler.getGotoDeclarationTargets(
                    element,
                    -1,
                    null,
                )
            if (null !== targets && targets.isNotEmpty()) {
                holder
                    .newAnnotation(HighlightSeverity.INFORMATION, "PSA Reference")
                    .range(element)
                    .create()
            } else if (!completions.isNullOrEmpty()) {
                val completionTitle = element.getUserData(ANNOTATOR_COMPLETION_TITLE)
                if (null != completionTitle) {
                    holder
                        .newAnnotation(HighlightSeverity.WEAK_WARNING, "Undefined PSA Reference for completion: \"$completionTitle\"")
                        .range(element)
                        .create()
                } else {
                    holder
                        .newAnnotation(HighlightSeverity.WEAK_WARNING, "Undefined PSA Reference")
                        .range(element)
                        .create()
                }
            }
        }
    }
}
