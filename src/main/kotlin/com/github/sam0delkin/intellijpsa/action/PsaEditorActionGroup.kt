@file:Suppress("DialogTitleCapitalization", "ktlint:standard:no-wildcard-imports")

package com.github.sam0delkin.intellijpsa.action

import com.github.sam0delkin.intellijpsa.action.psa.GeneratePatternModelAction
import com.github.sam0delkin.intellijpsa.icons.Icons.PluginIcon
import com.github.sam0delkin.intellijpsa.model.EditorActionSource
import com.github.sam0delkin.intellijpsa.model.EditorActionTarget
import com.github.sam0delkin.intellijpsa.model.action.EditorActionInputModel
import com.github.sam0delkin.intellijpsa.services.PsaManager
import com.intellij.ide.IdeView
import com.intellij.notification.NotificationGroupManager
import com.intellij.notification.NotificationType
import com.intellij.openapi.actionSystem.*
import com.intellij.openapi.command.WriteCommandAction
import com.intellij.openapi.components.service
import com.intellij.openapi.editor.Editor
import com.intellij.openapi.fileEditor.FileDocumentManager
import com.intellij.openapi.fileEditor.FileEditorManager
import com.intellij.openapi.project.guessProjectDir
import com.intellij.openapi.vfs.VfsUtil
import com.intellij.psi.PsiDirectory
import java.awt.Toolkit
import java.awt.datatransfer.DataFlavor
import java.awt.datatransfer.StringSelection
import kotlin.concurrent.thread

@Suppress("KotlinConstantConditions")
class PsaEditorActionGroup :
    ActionGroup(
        "PSA Actions",
        "Project Specific Autocomplete actions",
        PluginIcon,
    ) {
    override fun getChildren(e: AnActionEvent?): Array<AnAction> {
        val actions = HashMap<String?, ArrayList<AnAction>>()
        actions.set(null, arrayListOf(GeneratePatternModelAction()))

        val psaManager = e?.project?.service<PsaManager>() ?: return this.getActions(actions)
        val settings = psaManager.getSettings()
        val dataContext: DataContext = e.dataContext
        val view: IdeView = LangDataKeys.IDE_VIEW.getData(dataContext) ?: return arrayOf()

        val directories: Array<PsiDirectory> = view.directories
        if (directories.isEmpty()) {
            return arrayOf()
        }
        val directoryPath =
            '/' +
                VfsUtil
                    .getRelativePath(
                        directories[0].virtualFile,
                        e.project!!.guessProjectDir()!!,
                        '/',
                    ).toString() + '/'

        if (null !== settings.editorActions && settings.editorActions!!.isNotEmpty()) {
            actions.get(null)!!.add(Separator("PSA Actions"))

            for (action in settings.editorActions!!) {
                if (null !== action.pathRegex && !directoryPath.matches(Regex(action.pathRegex))) {
                    continue
                }

                val newAction =
                    object : AnAction(action.title) {
                        override fun actionPerformed(e: AnActionEvent) {
                            val clipboard = Toolkit.getDefaultToolkit().systemClipboard
                            val editor: Editor = FileEditorManager.getInstance(e.project!!).selectedTextEditor ?: return
                            val file = FileDocumentManager.getInstance().getFile(editor.document)
                            val selectedText = editor.selectionModel.selectedText
                            val path = VfsUtil.getRelativePath(file!!, e.project!!.guessProjectDir()!!, '/').toString()

                            thread {
                                var result: String? = null

                                if (action.source == EditorActionSource.Editor) {
                                    result =
                                        psaManager.performAction(
                                            settings,
                                            e.project!!,
                                            EditorActionInputModel(
                                                action.name,
                                                path,
                                                selectedText,
                                            ),
                                        )
                                } else if (action.source === EditorActionSource.Clipboard) {
                                    result =
                                        psaManager.performAction(
                                            settings,
                                            e.project!!,
                                            EditorActionInputModel(
                                                action.name,
                                                path,
                                                clipboard.getData(DataFlavor.stringFlavor).toString(),
                                            ),
                                        )
                                }

                                if (null === result) {
                                    return@thread
                                }

                                if (action.target == EditorActionTarget.Clipboard) {
                                    clipboard.setContents(StringSelection(result), null)

                                    NotificationGroupManager
                                        .getInstance()
                                        .getNotificationGroup("PSA Notification")
                                        .createNotification(
                                            "Action \"${action.title}\" result successfully copied to the clipboard",
                                            NotificationType.INFORMATION,
                                        ).notify(e.project)

                                    return@thread
                                }

                                if (action.target == EditorActionTarget.Editor) {
                                    WriteCommandAction.writeCommandAction(e.project).run<Throwable> {
                                        val document = editor.document
                                        val startOffset = editor.selectionModel.selectionStart
                                        val endOffset = editor.selectionModel.selectionEnd

                                        document.replaceString(startOffset, endOffset, result)
                                        editor.caretModel.moveToOffset(startOffset + result.length)
                                    }
                                }
                            }
                        }
                    }

                if (null === action.groupName) {
                    if (!actions.containsKey(null)) {
                        actions.set(null, arrayListOf())
                    }

                    actions.get(null)!!.add(newAction)
                } else {
                    if (!actions.containsKey(action.groupName)) {
                        actions.set(action.groupName, arrayListOf())
                    }

                    actions.get(action.groupName)!!.add(newAction)
                }
            }
        }

        return this.getActions(actions)
    }

    private fun getActions(actions: HashMap<String?, ArrayList<AnAction>>): Array<AnAction> {
        val result = ArrayList<AnAction>()

        for (actionGroupName in actions.keys) {
            if (null === actionGroupName) {
                for (action in actions[actionGroupName]!!) {
                    if (action is ActionGroup) {
                        continue
                    }

                    result.add(action)
                }
            } else {
                val actionGroup =
                    object : DefaultActionGroup(actionGroupName, true) {
                        override fun getChildren(e: AnActionEvent?): Array<AnAction> = actions[actionGroupName]!!.toTypedArray()
                    }

                result.add(actionGroup)
            }
        }

        return result.toTypedArray()
    }
}
