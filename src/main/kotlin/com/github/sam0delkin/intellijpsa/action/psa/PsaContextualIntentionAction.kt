package com.github.sam0delkin.intellijpsa.action.psa

import com.github.sam0delkin.intellijpsa.model.EditorActionSource
import com.github.sam0delkin.intellijpsa.model.EditorActionTarget
import com.github.sam0delkin.intellijpsa.model.action.EditorActionInputModel
import com.github.sam0delkin.intellijpsa.services.PsaManager
import com.github.sam0delkin.intellijpsa.settings.PersistedEditorAction
import com.github.sam0delkin.intellijpsa.settings.Settings
import com.intellij.codeInsight.intention.IntentionAction
import com.intellij.notification.NotificationGroupManager
import com.intellij.notification.NotificationType
import com.intellij.openapi.actionSystem.AnAction
import com.intellij.openapi.actionSystem.AnActionEvent
import com.intellij.openapi.actionSystem.CommonDataKeys
import com.intellij.openapi.actionSystem.DefaultActionGroup
import com.intellij.openapi.actionSystem.impl.SimpleDataContext
import com.intellij.openapi.command.WriteCommandAction
import com.intellij.openapi.components.service
import com.intellij.openapi.editor.Editor
import com.intellij.openapi.project.Project
import com.intellij.openapi.project.guessProjectDir
import com.intellij.openapi.ui.popup.JBPopupFactory
import com.intellij.openapi.vfs.VfsUtil
import com.intellij.psi.PsiFile
import java.awt.Toolkit
import java.awt.datatransfer.DataFlavor
import java.awt.datatransfer.StringSelection
import kotlin.collections.isNotEmpty
import kotlin.concurrent.thread

class PsaContextualIntentionAction : IntentionAction {
    override fun getText() = "PSA Actions"

    override fun getFamilyName() = "PSA"

    override fun startInWriteAction() = false

    override fun isAvailable(
        project: Project,
        editor: Editor?,
        file: PsiFile?,
    ): Boolean {
        if (file?.virtualFile?.parent == null) return false
        return getContextualActions(project, file).isNotEmpty()
    }

    override fun invoke(
        project: Project,
        editor: Editor?,
        file: PsiFile?,
    ) {
        if (editor == null || file?.virtualFile == null) return
        val psaManager = project.service<PsaManager>()
        val settings = psaManager.getSettings()
        val projectDir = project.guessProjectDir() ?: return
        val relPath = VfsUtil.getRelativePath(file.virtualFile, projectDir, '/').toString()

        val actionsByGroup = LinkedHashMap<String?, ArrayList<AnAction>>()
        for (model in getContextualActions(project, file)) {
            val anAction = buildAnAction(model, psaManager, settings, project, editor, relPath)
            actionsByGroup.getOrPut(model.groupName) { arrayListOf() }.add(anAction)
        }

        val group = DefaultActionGroup()
        for ((groupName, groupActions) in actionsByGroup) {
            if (groupName == null) {
                groupActions.forEach { group.add(it) }
            } else {
                val subGroup =
                    object : DefaultActionGroup(groupName, true) {
                        override fun getChildren(e: AnActionEvent?): Array<AnAction> = groupActions.toTypedArray()
                    }
                group.add(subGroup)
            }
        }

        val dataContext =
            SimpleDataContext
                .builder()
                .add(CommonDataKeys.PROJECT, project)
                .add(CommonDataKeys.EDITOR, editor)
                .build()

        JBPopupFactory
            .getInstance()
            .createActionGroupPopup(
                "PSA Actions",
                group,
                dataContext,
                JBPopupFactory.ActionSelectionAid.NUMBERING,
                true,
            ).showInBestPositionFor(editor)
    }

    private fun getContextualActions(
        project: Project,
        file: PsiFile,
    ): List<PersistedEditorAction> {
        val projectDir = project.guessProjectDir() ?: return emptyList()
        val parentDir = file.virtualFile?.parent ?: return emptyList()
        val relDir = VfsUtil.getRelativePath(parentDir, projectDir, '/') ?: return emptyList()
        val dirPath = "/$relDir/"

        val psaManager = project.service<PsaManager>()
        val settings = psaManager.getSettings()
        if (settings.editorActions.isNullOrEmpty()) return emptyList()

        return settings.editorActions!!.filter {
            it.contextual && (it.pathRegex == null || dirPath.matches(Regex(it.pathRegex!!)))
        }
    }

    private fun buildAnAction(
        action: PersistedEditorAction,
        psaManager: PsaManager,
        settings: Settings,
        project: Project,
        editor: Editor,
        filePath: String,
    ): AnAction =
        object : AnAction(action.title) {
            override fun actionPerformed(e: AnActionEvent) {
                val clipboard = Toolkit.getDefaultToolkit().systemClipboard
                val selectedText = editor.selectionModel.selectedText

                thread {
                    var result: String? = null

                    if (action.source == EditorActionSource.Editor) {
                        result =
                            psaManager.performAction(
                                settings,
                                project,
                                EditorActionInputModel(action.name, filePath, selectedText),
                            )
                    } else if (action.source == EditorActionSource.Clipboard) {
                        result =
                            psaManager.performAction(
                                settings,
                                project,
                                EditorActionInputModel(
                                    action.name,
                                    filePath,
                                    clipboard.getData(DataFlavor.stringFlavor).toString(),
                                ),
                            )
                    }

                    if (result == null) return@thread

                    if (action.target == EditorActionTarget.Clipboard) {
                        clipboard.setContents(StringSelection(result), null)
                        NotificationGroupManager
                            .getInstance()
                            .getNotificationGroup("PSA Notification")
                            .createNotification(
                                "Action \"${action.title}\" result successfully copied to the clipboard",
                                NotificationType.INFORMATION,
                            ).notify(project)
                        return@thread
                    }

                    if (action.target == EditorActionTarget.Editor) {
                        WriteCommandAction.writeCommandAction(project).run<Throwable> {
                            val startOffset = editor.selectionModel.selectionStart
                            val endOffset = editor.selectionModel.selectionEnd
                            editor.document.replaceString(startOffset, endOffset, result)
                            editor.caretModel.moveToOffset(startOffset + result.length)
                        }
                    }
                }
            }
        }
}
