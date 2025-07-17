package com.github.sam0delkin.intellijpsa.extension.extensionPoints

import com.intellij.openapi.actionSystem.ActionGroup
import com.intellij.openapi.project.Project
import com.intellij.ui.dsl.builder.Panel

interface PsaExtension {
    /**
     * Configure settings section. Add any elements to the panel.
     */
    fun configure(
        panel: Panel,
        project: Project,
    )

    /**
     * Check if any extension fields are modified
     */
    fun isModified(project: Project): Boolean

    /**
     * Reset extension settings to default values
     */
    fun reset(project: Project)

    /**
     * Save extension settings
     */
    fun apply(project: Project)

    /**
     * Will be called each time when PSA plugin will update info from the PSA script.
     * `info` - is the raw JSON retrieved from the user PSA script.
     */
    fun updateInfo(
        project: Project,
        info: String,
    )

    /**
     * Modify actions of the status bar icon
     */
    fun modifyStatusBar(
        project: Project,
        actionGroup: ActionGroup,
    )
}
