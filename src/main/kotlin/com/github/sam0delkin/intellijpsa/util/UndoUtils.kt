package com.github.sam0delkin.intellijpsa.util

import com.intellij.openapi.command.CommandProcessor
import com.intellij.openapi.command.undo.DocumentReference
import com.intellij.openapi.command.undo.UndoManager
import com.intellij.openapi.command.undo.UndoableAction
import com.intellij.openapi.project.Project

class UndoUtils {
    companion object {
        fun executeWithUndo(
            project: Project,
            operation: () -> Unit,
            undoOperation: () -> Unit,
            commandName: String,
            groupId: String? = null,
        ) {
            CommandProcessor.getInstance().executeCommand(project, {
                val action =
                    object : UndoableAction {
                        override fun undo() {
                            undoOperation()
                        }

                        override fun redo() {
                            operation()
                        }

                        override fun getAffectedDocuments(): Array<DocumentReference>? = null

                        override fun isGlobal(): Boolean = false
                    }

                operation()

                UndoManager.getInstance(project).undoableActionPerformed(action)
            }, commandName, groupId)
        }
    }
}
