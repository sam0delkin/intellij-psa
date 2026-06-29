package com.github.sam0delkin.intellijpsa.language.javascript

import com.github.sam0delkin.intellijpsa.extension.extensionPoints.PsaExtension
import com.github.sam0delkin.intellijpsa.language.javascript.model.JsInfoModel
import com.github.sam0delkin.intellijpsa.language.javascript.settings.JsPsaSettings
import com.intellij.openapi.actionSystem.ActionGroup
import com.intellij.openapi.components.service
import com.intellij.openapi.project.Project
import com.intellij.ui.components.JBCheckBox
import com.intellij.ui.dsl.builder.Cell
import com.intellij.ui.dsl.builder.Panel
import kotlinx.serialization.json.Json

class JsPsaExtension : PsaExtension {
    private lateinit var enabled: Cell<JBCheckBox>

    override fun configure(
        panel: Panel,
        project: Project,
    ) {
        panel.group("JavaScript") {
            row("Enabled") {
                enabled = checkBox("")
            }.rowComment("Enable JavaScript extension")
        }
    }

    override fun isModified(project: Project): Boolean {
        val settings = project.service<JsPsaSettings>()
        return settings.enabled != enabled.component.isSelected
    }

    override fun reset(project: Project) {
        val settings = project.service<JsPsaSettings>()
        enabled.component.isSelected = settings.enabled
    }

    override fun apply(project: Project) {
        val settings = project.service<JsPsaSettings>()
        settings.enabled = enabled.component.isSelected
    }

    override fun updateInfo(
        project: Project,
        info: String,
    ) {
        val settings = project.service<JsPsaSettings>()

        try {
            val json = Json { ignoreUnknownKeys = true }
            val jsInfo = json.decodeFromString<JsInfoModel>(info)

            if (!settings.enabled) {
                return
            }

            settings.methodArgumentProviders = jsInfo.methodArgumentProviders
        } catch (_: Throwable) {
            return
        }
    }

    override fun modifyStatusBar(
        project: Project,
        actionGroup: ActionGroup,
    ) {
    }
}
