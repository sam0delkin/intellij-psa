package com.github.sam0delkin.intellijpsa.ui.components

import com.intellij.openapi.actionSystem.ActionToolbar
import com.intellij.openapi.actionSystem.AnAction
import com.intellij.openapi.actionSystem.impl.ActionButton
import com.intellij.openapi.fileChooser.FileChooserDescriptor
import com.intellij.openapi.project.Project
import com.intellij.openapi.ui.TextFieldWithBrowseButton
import com.intellij.openapi.vfs.VirtualFile
import com.intellij.ui.dsl.builder.COLUMNS_SHORT
import com.intellij.ui.dsl.builder.Cell
import com.intellij.ui.dsl.builder.Row
import com.intellij.ui.dsl.builder.columns
import com.intellij.ui.components.textFieldWithBrowseButton as baseTextFieldWithBrowseButton

class Utils {
    companion object {
        fun actionButton(
            action: AnAction,
            actionPlace: String,
            row: Row,
        ): Cell<ActionButton> {
            val component =
                ActionButton(action, action.templatePresentation.clone(), actionPlace, ActionToolbar.DEFAULT_MINIMUM_BUTTON_SIZE)
            return row.cell(component)
        }

        fun textFieldWithBrowseButton(
            row: Row,
            browseDialogTitle: String?,
            project: Project?,
            fileChooserDescriptor: FileChooserDescriptor,
            fileChosen: ((chosenFile: VirtualFile) -> String)?,
        ): Cell<TextFieldWithBrowseButton> {
            val result =
                row.cell(
                    baseTextFieldWithBrowseButton(
                        project,
                        browseDialogTitle,
                        fileChooserDescriptor,
                        fileChosen,
                    ),
                )
            result.columns(COLUMNS_SHORT)
            return result
        }
    }
}
