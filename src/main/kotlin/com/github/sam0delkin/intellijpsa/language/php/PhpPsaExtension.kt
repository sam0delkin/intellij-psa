package com.github.sam0delkin.intellijpsa.language.php

import com.github.sam0delkin.intellijpsa.extension.extensionPoints.PsaExtension
import com.github.sam0delkin.intellijpsa.language.php.eventSubscriber.PsaXdebugManagerListener
import com.github.sam0delkin.intellijpsa.language.php.model.PhpInfoModel
import com.github.sam0delkin.intellijpsa.language.php.services.PhpPsaManager
import com.github.sam0delkin.intellijpsa.language.php.settings.PhpPsaSettings
import com.github.sam0delkin.intellijpsa.settings.Settings
import com.intellij.openapi.Disposable
import com.intellij.openapi.actionSystem.ActionGroup
import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.components.service
import com.intellij.openapi.project.Project
import com.intellij.ui.components.JBCheckBox
import com.intellij.ui.dsl.builder.Cell
import com.intellij.ui.dsl.builder.Panel
import com.intellij.ui.dsl.builder.selected
import com.intellij.util.messages.MessageBusConnection
import com.intellij.xdebugger.XDebuggerManager
import kotlinx.serialization.json.Json

class PhpPsaExtension :
    PsaExtension,
    Disposable {
    private lateinit var enabled: Cell<JBCheckBox>
    private lateinit var debugTypeProvider: Cell<JBCheckBox>
    private var connection: MessageBusConnection? = null

    override fun configure(
        panel: Panel,
        project: Project,
    ) {
        panel.group("PHP") {
            row("Enabled") {
                enabled = checkBox("")
            }.rowComment("Enable PHP extension")
            row("Debug Type Provider") {
                debugTypeProvider = checkBox("")
            }.rowComment(
                "Debug mode for TypeProvider. Possible elements will be highlighted",
            ).enabledIf(enabled.selected)
        }
    }

    override fun isModified(project: Project): Boolean {
        val settings = project.service<PhpPsaSettings>()

        return settings.enabled != enabled.component.isSelected ||
            settings.debugTypeProvider != debugTypeProvider.component.isSelected
    }

    override fun reset(project: Project) {
        val settings = project.service<PhpPsaSettings>()

        enabled.component.isSelected = settings.enabled
        debugTypeProvider.component.isSelected = settings.debugTypeProvider
    }

    override fun apply(project: Project) {
        val settings = project.service<PhpPsaSettings>()

        settings.enabled = enabled.component.isSelected
        settings.debugTypeProvider = debugTypeProvider.component.isSelected
    }

    override fun updateInfo(
        project: Project,
        info: String,
    ) {
        val psaManager = project.service<PhpPsaManager>()
        val settings = project.service<Settings>()
        val phpSettings = psaManager.getSettings()

        try {
            val json =
                Json {
                    ignoreUnknownKeys = true
                }
            val phpInfo = json.decodeFromString<PhpInfoModel>(info)

            if (!phpSettings.enabled) {
                return
            }

            phpSettings.supportsTypeProviders = phpInfo.supportsTypeProviders ?: false
            phpSettings.toStringValueFormatter = phpInfo.toStringValueFormatter

            if (phpSettings.supportsTypeProviders) {
                ApplicationManager.getApplication().invokeLater {
                    psaManager.updateTypeProviders(settings, phpSettings, project)
                }
            }

            if (
                !phpSettings.enabled ||
                null === phpSettings.toStringValueFormatter
            ) {
                return
            }

            connection?.disconnect()
            connection = project.messageBus.connect()
            connection?.subscribe(
                XDebuggerManager.TOPIC,
                PsaXdebugManagerListener(project),
            )
        } catch (_: Throwable) {
            return
        }
    }

    override fun modifyStatusBar(
        project: Project,
        actionGroup: ActionGroup,
    ) {
    }

    override fun dispose() {
        this.connection?.disconnect()
    }
}
