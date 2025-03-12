@file:Suppress("ktlint:standard:no-wildcard-imports")

package com.github.sam0delkin.intellijpsa.settings

import com.github.sam0delkin.intellijpsa.services.PsaManager
import com.github.sam0delkin.intellijpsa.status.widget.PsaStatusBarWidgetFactory
import com.github.sam0delkin.intellijpsa.ui.components.Utils
import com.intellij.execution.util.PathMappingsComponent
import com.intellij.icons.AllIcons
import com.intellij.lang.Language
import com.intellij.openapi.actionSystem.AnActionEvent
import com.intellij.openapi.actionSystem.impl.ActionButton
import com.intellij.openapi.components.service
import com.intellij.openapi.fileChooser.FileChooserDescriptorFactory
import com.intellij.openapi.options.Configurable
import com.intellij.openapi.options.ConfigurationException
import com.intellij.openapi.project.DumbAwareAction
import com.intellij.openapi.project.Project
import com.intellij.openapi.project.guessProjectDir
import com.intellij.openapi.ui.DialogPanel
import com.intellij.openapi.ui.TextFieldWithBrowseButton
import com.intellij.openapi.vfs.VfsUtil
import com.intellij.openapi.wm.impl.status.widget.StatusBarWidgetsManager
import com.intellij.ui.components.JBCheckBox
import com.intellij.ui.dsl.builder.*
import java.awt.Dimension
import java.awt.Point
import java.nio.file.Path
import javax.swing.JComponent
import javax.swing.JSpinner
import javax.swing.JTextField
import javax.swing.SpinnerNumberModel

class ProjectSettingsForm(
    private val project: Project,
) : Configurable {
    private lateinit var enabled: Cell<JBCheckBox>
    private lateinit var debug: Cell<JBCheckBox>
    private lateinit var scriptPath: Cell<TextFieldWithBrowseButton>
    private lateinit var pathMappings: Cell<PathMappingsComponent>
    private lateinit var supportedLanguages: Cell<JTextField>
    private lateinit var goToElementFilter: Cell<JTextField>
    private lateinit var infoButton: Cell<ActionButton>
    private lateinit var supportedLanguagesButton: Cell<ActionButton>
    private lateinit var executionTimeout: Cell<JSpinner>
    private var changed: Boolean = false

    private fun createComponents(): DialogPanel {
        val self = this
        val panel =
            panel {
                row("Plugin Enabled") {
                    enabled = checkBox("")
                }
                group {
                    row("Debug") {
                        debug = checkBox("")
                    }.rowComment("Debug mode. Passed as `PSA_DEBUG` into the executable script")
                    row("Script Path") {
                        scriptPath =
                            Utils
                                .textFieldWithBrowseButton(
                                    this,
                                    "Choose PSA Executable FIle",
                                    project,
                                    FileChooserDescriptorFactory
                                        .createSingleFileDescriptor()
                                        .withFileFilter { e ->
                                            java.nio.file.Files
                                                .isExecutable(Path.of(e.path))
                                        }.withShowHiddenFiles(true),
                                ) { chosenFile ->
                                    run {
                                        val projectDirectory = project.guessProjectDir()
                                        assert(projectDirectory != null)
                                        var path = VfsUtil.getRelativePath(chosenFile, projectDirectory!!, '/')
                                        if (null == path) {
                                            path = chosenFile.path
                                        }
                                        updateInfoButtonEnabled()

                                        path
                                    }
                                }.gap(RightGap.SMALL)

                        @Suppress("DialogTitleCapitalization")
                        val action =
                            object : DumbAwareAction(
                                "Get info from your executable script",
                                "",
                                AllIcons.General.BalloonInformation,
                            ) {
                                override fun actionPerformed(e: AnActionEvent) {
                                    self.getInfo()
                                }
                            }
                        infoButton = Utils.actionButton(action, "bottom", this)
                    }.rowComment("Path to the PSA executable script. Must be an executable file")
                    row("Execution Timeout") {
                        executionTimeout = cell(JSpinner(SpinnerNumberModel(5000, 0, 100000, 1000)))
                    }.rowComment("Maximum execution time for your script (in milliseconds). Default: 5000 milliseconds")
                    row("Path Mappings") {
                        val pathMappingsComponent = PathMappingsComponent()
                        pathMappingsComponent.text = ""
                        pathMappingsComponent.minimumSize = Dimension(200, 0)
                        pathMappings = cell(pathMappingsComponent)
                    }.rowComment(
                        "Path mappings (for projects that running remotely (within Docker/Vagrant/etc.)). " +
                            "Source mapping should start from `/`\n" +
                            "as project root",
                    )
                    row("Supported Languages") {
                        supportedLanguages = textField().enabled(false)
                        @Suppress("DialogTitleCapitalization")
                        val action =
                            object : DumbAwareAction(
                                "Get List of supported languages by your IDE",
                                "",
                                AllIcons.General.BalloonInformation,
                            ) {
                                override fun actionPerformed(e: AnActionEvent) {
                                    val languages = Language.getRegisteredLanguages()
                                    val tooltip =
                                        com.intellij.ui
                                            .GotItTooltip(
                                                "PSA",
                                                "Supported Languages: <br />" +
                                                    "" + languages.joinToString(", ") { it.displayName },
                                            ).withIcon(AllIcons.General.BalloonInformation)
                                            .withButtonLabel("OK")
                                    tooltip.createAndShow(supportedLanguagesButton.component) { c, _ ->
                                        Point(
                                            c.width,
                                            c.height / 2,
                                        )
                                    }
                                }
                            }
                        supportedLanguagesButton = Utils.actionButton(action, "bottom", this)
                    }.rowComment("Programming languages supported by your autocompletion")
                    row("GoTo Element Filter") {
                        goToElementFilter = textField().enabled(false)
                    }.rowComment(
                        "GoTo element filter returned by you autocompletion. Read more in \n" +
                            "<a href=\"https://github.com/sam0delkin/intellij-psa#goto-optimizations\">performance</a> documentation section.",
                    )
                }.enabledIf(enabled.selected)
                    .rowComment(
                        "For more info, please check the <a href=\"https://github.com/sam0delkin/intellij-psa#documentation\">documentation</a>.",
                    )
            }

        return panel
    }

    private fun getInfo() {
        val service = project.service<PsaManager>()
        try {
            this.goToElementFilter.component.text = ""
            this.supportedLanguages.component.text = ""
            val info = service.getInfo(settings, project, this.scriptPath.component.text)
            var filter: List<String> = listOf()
            var templateCount = 0

            if (info.goToElementFilter != null) {
                filter = info.goToElementFilter
                this.goToElementFilter.component.text = info.goToElementFilter.joinToString(",")
            }

            this.supportedLanguages.component.text = info.supportedLanguages.joinToString(",")

            if (info.templates != null) {
                templateCount = info.templates.size
                this.changed = true
            }

            service.updateInfo(settings, info)
            val languagesString = "<ul>" + info.supportedLanguages.joinToString("") { i -> "<li>- $i</li>" } + "<ul>"
            val filterString = "<ul>" + filter.joinToString("") { i -> "<li>- $i</li>" } + "<ul>"

            val tooltip =
                com.intellij.ui
                    .GotItTooltip(
                        "PSA",
                        "Successfully retrieved info: <br />" +
                            "Supported Languages: $languagesString<br />" +
                            "GoTo Element Filter: $filterString<br />" +
                            "Template Count: $templateCount<br />" +
                            "Supports Batch: ${settings.supportsBatch}<br />",
                    ).withIcon(AllIcons.General.BalloonInformation)
                    .withButtonLabel("OK")
            tooltip.createAndShow(this.infoButton.component) { c, _ -> Point(c.width, c.height / 2) }
        } catch (e: Exception) {
            val tooltip =
                com.intellij.ui
                    .GotItTooltip(
                        "PSA",
                        "Error during retrieve info: <br />" + e.message + "<br /><br /> For help, please " +
                            "check the <a href=\"https://github.com/sam0delkin/intellij-psa#documentation\">documentation</a>",
                    ).withIcon(AllIcons.General.BalloonError)
                    .withButtonLabel("OK")
            tooltip.createAndShow(this.infoButton.component) { c, _ -> Point(c.width, c.height / 2) }
        }
    }

    override fun createComponent(): JComponent = this.createComponents()

    private fun updateInfoButtonEnabled() {
        this.infoButton.component.setEnabled(this.enabled.component.isSelected && this.scriptPath.component.text !== "")
    }

    override fun isModified(): Boolean =
        (
            enabled.component.isSelected != settings.pluginEnabled ||
                debug.component.isSelected != settings.debug ||

                scriptPath.component.text != settings.scriptPath ||
                pathMappings.component.mappingSettings.pathMappings
                    .joinToString(",") { el -> el.localRoot + " ->" + el.remoteRoot } !=
                settings.pathMappings?.joinToString(",") { el -> el.localRoot + " ->" + el.remoteRoot } ||
                supportedLanguages.component.text != settings.supportedLanguages ||
                goToElementFilter.component.text != settings.goToFilter ||
                executionTimeout.component.value != settings.executionTimeout ||
                changed

        )

    override fun reset() {
        enabled.component.setSelected(settings.pluginEnabled)
        debug.component.setSelected(settings.debug)
        scriptPath.component.setText(settings.scriptPath)
        settings.pathMappings?.map { el -> pathMappings.component.mappingSettings.add(el) }
        pathMappings.component.setMappingSettings(pathMappings.component.mappingSettings)
        supportedLanguages.component.text = settings.supportedLanguages
        goToElementFilter.component.text = settings.goToFilter
        executionTimeout.component.value = settings.executionTimeout
        changed = false
    }

    @Throws(ConfigurationException::class)
    override fun apply() {
        val psaManager = project.service<PsaManager>()
        settings.pluginEnabled = enabled.component.isSelected
        settings.debug = debug.component.isSelected
        settings.scriptPath = scriptPath.component.text.trim()
        settings.pathMappings =
            pathMappings.component.mappingSettings.pathMappings
                .toTypedArray()
        settings.supportedLanguages = supportedLanguages.component.text
        settings.goToFilter = goToElementFilter.component.text
        settings.executionTimeout = executionTimeout.component.value as Int
        changed = false
        if (!settings.pluginEnabled && psaManager.lastResultSucceed) {
            psaManager.lastResultSucceed = false
            psaManager.lastResultMessage = ""
        } else if (settings.pluginEnabled && psaManager.lastResultSucceed) {
            psaManager.lastResultSucceed = true
            psaManager.lastResultMessage = ""
        }

        val psaStatusBarWidgetFactory = PsaStatusBarWidgetFactory()
        if (null ===
            project
                .service<StatusBarWidgetsManager>()
                .findWidgetFactory(PsaStatusBarWidgetFactory.WIDGET_ID)
        ) {
            project.service<StatusBarWidgetsManager>().updateWidget(psaStatusBarWidgetFactory)
        }
        project.service<StatusBarWidgetsManager>().updateAllWidgets()
    }

    private val settings: Settings
        get() = project.service<PsaManager>().getSettings()

    override fun getDisplayName(): String = "Project Specific Autocomplete"
}
