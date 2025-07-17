package com.github.sam0delkin.intellijpsa.lineMarkerProvider

import com.github.sam0delkin.intellijpsa.completion.AnyCompletionContributor
import com.github.sam0delkin.intellijpsa.icons.Icons
import com.github.sam0delkin.intellijpsa.index.INDEX_ID
import com.github.sam0delkin.intellijpsa.model.ExtendedCompletionsModel
import com.github.sam0delkin.intellijpsa.model.ExtendedStaticCompletionModel
import com.github.sam0delkin.intellijpsa.psi.PsaElement
import com.github.sam0delkin.intellijpsa.psi.reference.PsaReference
import com.github.sam0delkin.intellijpsa.services.PsaManager
import com.github.sam0delkin.intellijpsa.util.PsiUtils
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
import com.intellij.openapi.ui.DialogBuilder
import com.intellij.openapi.ui.popup.JBPopupFactory
import com.intellij.openapi.vfs.VirtualFile
import com.intellij.psi.PsiDocumentManager
import com.intellij.psi.PsiElement
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
        val index = FileBasedIndex.getInstance()
        val goToDeclarationHandler = firstElement.project.service<AnyCompletionContributor.GotoDeclaration>()
        val staticCompletionConfigs = psaManager.getStaticCompletionConfigs()

        if (
            !settings.pluginEnabled ||
            !settings.resolveReferences ||
            null == settings.targetElementTypes ||
            !settings.supportsStaticCompletions ||
            !settings.annotateUndefinedElements ||
            staticCompletionConfigs.isNullOrEmpty()
        ) {
            return
        }

        val fileData = index.getFileData(INDEX_ID, firstElement.containingFile.virtualFile, firstElement.project)

        elements.map { element ->
            var processed = false
            val project = element.project
            if (!settings.targetElementTypes!!.contains(element.elementType.printToString())) {
                return@map
            }

            fileData.values.map {
                it?.entries!!.map { entry ->
                    {
                        entry.value.map { value ->
                            {
                                val sourceEl = PsiUtils.processLink("file://$value", null, element.project, false)
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
                    }
                }
            }

            if (!processed) {
                val references = ReferenceProvidersRegistry.getReferencesFromProviders(element, PsiReferenceService.Hints.NO_HINTS)
                if (references.isNotEmpty()) {
                    val el = NavigationGutterIconBuilder.create(Icons.PluginIcon)
                    val resolvedReferences = references.map { it.resolve() }
                    val firstReference = references.first()
                    var referenceTitle: String? = null
                    var staticCompletion: ExtendedStaticCompletionModel? = null

                    if (firstReference is PsaReference && null != firstReference.staticCompletionName) {
                        staticCompletion =
                            staticCompletionConfigs.first {
                                it.name == firstReference.staticCompletionName
                            }
                        referenceTitle = staticCompletion.title
                    }

                    if (null == staticCompletion) {
                        return@map
                    }

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
                            object : GutterIconNavigationHandler<PsiElement> {
                                override fun navigate(
                                    e: MouseEvent?,
                                    elt: PsiElement?,
                                ) {
                                    if (null == e || MouseButton.fromEvent(e) == MouseButton.Left && !e.isShiftDown) {
                                        if (null != elt?.navigationElement && elt.navigationElement!!.isValid) {
                                            return navigationHandler.navigate(e, elt)
                                        }
                                    }

                                    val actionGroup = DefaultActionGroup("PSA", false)
                                    actionGroup.add(
                                        object : AnAction("Rename..") {
                                            override fun actionPerformed(e: AnActionEvent) {
                                                var newNameField: Cell<JBTextField>? = null
                                                val dialogBuilder =
                                                    DialogBuilder().centerPanel(
                                                        panel {
                                                            row("New Name") {
                                                                val newName = textField()
                                                                val reference = references.first()

                                                                newName.component.text = reference.element.text
                                                                newNameField = newName
                                                            }
                                                        },
                                                    )

                                                if (dialogBuilder.showAndGet()) {
                                                    val oldText = references.first().element.text
                                                    val newText = newNameField!!.component.text
                                                    val originalElementContainingFile = element.containingFile
                                                    val virtualFiles: MutableList<VirtualFile> = mutableListOf()

                                                    UndoUtils.executeWithUndo(
                                                        project,
                                                        {
                                                            references.map {
                                                                val originalElementStartOffset = element.textRange.startOffset
                                                                var referenceElement: PsiElement? = it.resolve() ?: return@executeWithUndo

                                                                if (references.first() === it &&
                                                                    referenceElement.elementType == element.elementType

                                                                ) {
                                                                    WriteCommandAction.writeCommandAction(project).run<Throwable> {
                                                                        val elementDocument =
                                                                            PsiDocumentManager.getInstance(project).getDocument(
                                                                                element.containingFile,
                                                                            )

                                                                        if (null == elementDocument) {
                                                                            return@run
                                                                        }

                                                                        elementDocument.replaceString(
                                                                            element.textRange.startOffset,
                                                                            element.textRange.endOffset,
                                                                            newText,
                                                                        )

                                                                        PsiDocumentManager
                                                                            .getInstance(project)
                                                                            .commitDocument(elementDocument)
                                                                    }
                                                                }

                                                                if (referenceElement is PsaElement) {
                                                                    referenceElement = referenceElement.getOriginalPsiElement()
                                                                }

                                                                if (null == referenceElement!!.containingFile) {
                                                                    return@executeWithUndo
                                                                }

                                                                val document =
                                                                    PsiDocumentManager.getInstance(project).getDocument(
                                                                        referenceElement.containingFile,
                                                                    )

                                                                if (null == document) {
                                                                    return@executeWithUndo
                                                                }

                                                                val referenceElementContainingFile = referenceElement.containingFile
                                                                val referenceElementStartOffset = referenceElement.textRange.startOffset

                                                                WriteCommandAction.writeCommandAction(project).run<Throwable> {
                                                                    document.replaceString(
                                                                        referenceElement!!.textRange.startOffset,
                                                                        referenceElement!!.textRange.endOffset,
                                                                        newText,
                                                                    )

                                                                    PsiDocumentManager.getInstance(project).commitDocument(document)
                                                                }

                                                                referenceElement =
                                                                    referenceElementContainingFile!!.findElementAt(
                                                                        referenceElementStartOffset,
                                                                    )

                                                                if (null == referenceElement) {
                                                                    return@executeWithUndo
                                                                }

                                                                virtualFiles.add(referenceElement.containingFile.virtualFile)

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
                                                                psaManager.setStaticCompletionConfigs(
                                                                    staticCompletionConfigs
                                                                        .map { el ->
                                                                            el.toStaticCompletionModel()
                                                                        }.toMutableList(),
                                                                )
                                                                psaManager.updateStaticCompletionsHash(settings)
                                                                val originalElement =
                                                                    originalElementContainingFile!!.findElementAt(
                                                                        originalElementStartOffset,
                                                                    )
                                                                virtualFiles.add(originalElement!!.containingFile.virtualFile)
                                                                virtualFiles.map { file ->
                                                                    FileBasedIndex.getInstance().requestReindex(file)
                                                                }
                                                            }
                                                        },
                                                        {
                                                            psaManager.updateStaticCompletions(settings, project)
                                                        },
                                                        PSA_REFERENCE_RENAME_COMMAND_NAME,
                                                    )
                                                }
                                            }
                                        },
                                    )
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
                            },
                        ),
                    )
                }
            }
        }
    }
}
