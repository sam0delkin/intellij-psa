package com.github.sam0delkin.intellijpsa.lineMarkerProvider

import com.github.sam0delkin.intellijpsa.icons.Icons
import com.github.sam0delkin.intellijpsa.model.ExtendedCompletionsModel
import com.github.sam0delkin.intellijpsa.model.ExtendedStaticCompletionModel
import com.github.sam0delkin.intellijpsa.psi.PsaElement
import com.github.sam0delkin.intellijpsa.psi.reference.PsaReference
import com.github.sam0delkin.intellijpsa.services.PsaManager
import com.github.sam0delkin.intellijpsa.util.UndoUtils
import com.intellij.codeInsight.daemon.GutterIconNavigationHandler
import com.intellij.codeInsight.daemon.LineMarkerInfo
import com.intellij.codeInsight.daemon.LineMarkerProvider
import com.intellij.codeInsight.hints.presentation.MouseButton
import com.intellij.codeInsight.navigation.NavigationGutterIconBuilder
import com.intellij.openapi.actionSystem.AnAction
import com.intellij.openapi.actionSystem.AnActionEvent
import com.intellij.openapi.actionSystem.DefaultActionGroup
import com.intellij.openapi.actionSystem.impl.SimpleDataContext
import com.intellij.openapi.command.WriteCommandAction
import com.intellij.openapi.components.service
import com.intellij.openapi.editor.Document
import com.intellij.openapi.editor.RangeMarker
import com.intellij.openapi.fileEditor.FileDocumentManager
import com.intellij.openapi.project.Project
import com.intellij.openapi.ui.DialogBuilder
import com.intellij.openapi.ui.popup.JBPopupFactory
import com.intellij.psi.PsiDocumentManager
import com.intellij.psi.PsiElement
import com.intellij.psi.PsiReference
import com.intellij.psi.PsiReferenceService
import com.intellij.psi.impl.source.resolve.reference.ReferenceProvidersRegistry
import com.intellij.psi.util.elementType
import com.intellij.ui.awt.RelativePoint
import com.intellij.ui.components.JBTextField
import com.intellij.ui.dsl.builder.Cell
import com.intellij.ui.dsl.builder.panel
import com.intellij.util.indexing.FileBasedIndex
import com.jetbrains.rd.util.string.printToString
import java.awt.event.MouseEvent

const val PSA_REFERENCE_RENAME_COMMAND_NAME = "PSA Reference Rename"

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
        val staticCompletionConfigs = psaManager.getStaticCompletionConfigs()
        val liveLookupEnabled = settings.isServerModeActive()
        val staticLookupEnabled =
            settings.supportsStaticCompletions && null != settings.targetElementTypes && staticCompletionConfigs.isNotEmpty()

        if (
            !settings.pluginEnabled ||
            !settings.resolveReferences ||
            !settings.annotateUndefinedElements ||
            (!staticLookupEnabled && !liveLookupEnabled)
        ) {
            return
        }

        elements.map { element ->
            val elementTypeString = element.elementType.printToString()
            val staticTypeMatch = null != settings.targetElementTypes && settings.targetElementTypes!!.contains(elementTypeString)
            val liveTypeMatch = liveLookupEnabled && settings.isElementTypeMatchingFilter(elementTypeString)
            if (!staticTypeMatch && !liveTypeMatch) {
                return@map
            }

            val references = ReferenceProvidersRegistry.getReferencesFromProviders(element, PsiReferenceService.Hints.NO_HINTS)
            if (references.isNotEmpty()) {
                val firstReference = references.first()
                var referenceTitle: String? = null
                var staticCompletion: ExtendedStaticCompletionModel? = null

                if (firstReference is PsaReference && null != firstReference.staticCompletionName) {
                    staticCompletion =
                        staticCompletionConfigs.firstOrNull {
                            it.name == firstReference.staticCompletionName
                        }
                    referenceTitle = staticCompletion?.title
                }

                if (null == staticCompletion) {
                    if (!liveLookupEnabled) {
                        return@map
                    }

                    // Only trust references PSA's own live lookup actually produced - `references` above
                    // aggregates every PsiReferenceContributor registered for this element, not just PSA's.
                    val livePsaReferences =
                        references.filterIsInstance<PsaReference>().filter { null == it.staticCompletionName }

                    if (livePsaReferences.isEmpty()) {
                        return@map
                    }

                    val liveResolvedReferences = livePsaReferences.map { it.resolve() }
                    val liveEl = NavigationGutterIconBuilder.create(Icons.PluginIcon)
                    liveEl.setTargets(liveResolvedReferences)
                    @Suppress("DialogTitleCapitalization")
                    liveEl.setTooltipText("PSA Reference")
                    result.add(liveEl.createLineMarkerInfo(element))
                    return@map
                }

                val el = NavigationGutterIconBuilder.create(Icons.PluginIcon)
                val resolvedReferences = references.map { it.resolve() }
                el.setTargets(resolvedReferences)
                if (null != referenceTitle) {
                    el.setTooltipText("PSA Reference \"$referenceTitle\" (Shift + Click for more actions)")
                } else {
                    el.setTooltipText("PSA Reference (Shift + Click for more actions)")
                }
                val lineMarker = el.createLineMarkerInfo(element)
                val navigationHandler = lineMarker.navigationHandler

                result.add(
                    el.createLineMarkerInfo(
                        element,
                        PsaReferenceNavigationHandler(
                            element,
                            references.toList(),
                            staticCompletion,
                            staticCompletionConfigs,
                            navigationHandler,
                        ),
                    ),
                )
            }
        }
    }

    /**
     * Gutter handler for static-completion-backed references: a plain left click navigates, while
     * Shift + Click (or any other mouse button) opens the "PSA Reference Actions" popup.
     */
    class PsaReferenceNavigationHandler(
        private val element: PsiElement,
        private val references: List<PsiReference>,
        private val staticCompletion: ExtendedStaticCompletionModel,
        private val staticCompletionConfigs: List<ExtendedStaticCompletionModel>,
        private val navigationHandler: GutterIconNavigationHandler<PsiElement>,
    ) : GutterIconNavigationHandler<PsiElement> {
        override fun navigate(
            e: MouseEvent?,
            elt: PsiElement?,
        ) {
            if (null == e || MouseButton.fromEvent(e) == MouseButton.Left && !e.isShiftDown) {
                if (null != elt?.navigationElement && elt.navigationElement!!.isValid) {
                    return navigationHandler.navigate(e, elt)
                }
            }

            val actionGroup = createActionGroup()
            if (null != e) {
                JBPopupFactory
                    .getInstance()
                    .createActionGroupPopup(
                        " PSA Reference Actions ",
                        actionGroup,
                        SimpleDataContext.builder().build(),
                        JBPopupFactory.ActionSelectionAid.SPEEDSEARCH,
                        false,
                    ).show(RelativePoint(e))
            }
        }

        fun createActionGroup(): DefaultActionGroup {
            val actionGroup = DefaultActionGroup("PSA", false)
            actionGroup.add(
                object : AnAction("Rename..") {
                    override fun actionPerformed(e: AnActionEvent) {
                        val newText = newNameProvider(references.first().element.text) ?: return
                        rename(newText)
                    }
                },
            )

            return actionGroup
        }

        fun rename(newText: String) {
            val project = element.project
            val psaManager = project.service<PsaManager>()
            val settings = psaManager.getSettings()
            val documentManager = PsiDocumentManager.getInstance(project)
            val oldText = references.first().element.text
            val declarationFile = element.containingFile.virtualFile

            UndoUtils.executeWithUndo(
                project,
                {
                    val targets = collectRenameTargets(documentManager) ?: return@executeWithUndo

                    // Range markers keep tracking their text while other ranges in the same document
                    // are replaced, so nothing here reads PSI invalidated by an earlier edit.
                    WriteCommandAction.writeCommandAction(project).run<Throwable> {
                        targets.forEach { (document, marker) ->
                            document.replaceString(marker.startOffset, marker.endOffset, newText)
                            marker.dispose()
                        }
                        targets.map { it.first }.distinct().forEach { documentManager.commitDocument(it) }
                    }

                    renameStaticCompletionEntries(project, oldText, newText)
                    psaManager.setStaticCompletionConfigs(
                        staticCompletionConfigs
                            .map { el ->
                                el.toStaticCompletionModel()
                            }.toMutableList(),
                    )
                    psaManager.updateStaticCompletionsHash(settings)
                    targets
                        .mapNotNull { FileDocumentManager.getInstance().getFile(it.first) }
                        .plus(declarationFile)
                        .distinct()
                        .forEach { file -> FileBasedIndex.getInstance().requestReindex(file) }
                },
                {
                    psaManager.updateStaticCompletions(settings, project)
                },
                PSA_REFERENCE_RENAME_COMMAND_NAME,
            )
        }

        /**
         * Text ranges to replace: every resolved reference target, plus the declaration itself when
         * it has the same element type as the first target. A range is listed once even if several
         * references point at it (a declaration that is also its own usage). Null when a reference
         * no longer resolves.
         */
        private fun collectRenameTargets(documentManager: PsiDocumentManager): List<Pair<Document, RangeMarker>>? {
            val resolved = references.map { it.resolve() ?: return null }
            val elements = mutableListOf<PsiElement>()
            if (resolved.first().elementType == element.elementType) {
                elements.add(element)
            }
            resolved.mapTo(elements) { if (it is PsaElement) it.getOriginalPsiElement() else it }

            return elements
                .mapNotNull { el ->
                    val file = el.containingFile ?: return@mapNotNull null
                    val document = documentManager.getDocument(file) ?: return@mapNotNull null

                    document to el.textRange
                }.distinct()
                .map { (document, range) -> document to document.createRangeMarker(range) }
        }

        private fun renameStaticCompletionEntries(
            project: Project,
            oldText: String,
            newText: String,
        ) {
            staticCompletion
                .extendedCompletions!!
                .extendedCompletions!!
                .forEach { completion ->
                    if (completion.text == oldText) {
                        completion.text = newText
                    }
                }
            staticCompletion.extendedCompletions =
                ExtendedCompletionsModel.createFromModel(
                    staticCompletion.extendedCompletions!!,
                    project,
                )
            staticCompletion
                .extendedCompletions!!
                .completions!!
                .forEach { completion ->
                    if (completion.text == oldText) {
                        completion.text = newText
                    }
                }
            staticCompletion.completions!!.completions!!.forEach { completion ->
                if (oldText == completion.text) {
                    completion.text = newText
                }
            }
        }
    }

    companion object {
        /**
         * Asks for the new reference name, returning null when cancelled. Replaceable so tests can
         * drive the Rename action without a modal dialog.
         */
        var newNameProvider: (String) -> String? = { currentName -> showRenameDialog(currentName) }

        private fun showRenameDialog(currentName: String): String? {
            var newNameField: Cell<JBTextField>? = null
            val dialogBuilder =
                DialogBuilder().centerPanel(
                    panel {
                        row("New Name") {
                            val newName = textField()
                            newName.component.text = currentName
                            newNameField = newName
                        }
                    },
                )

            if (!dialogBuilder.showAndGet()) {
                return null
            }

            return newNameField!!.component.text
        }
    }
}
