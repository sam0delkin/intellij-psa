package com.github.sam0delkin.intellijpsa.settings

import com.github.sam0delkin.intellijpsa.services.CompletionService
import com.github.sam0delkin.intellijpsa.statusBar.PsaStatusBarWidgetFactory
import com.intellij.execution.util.PathMappingsComponent
import com.intellij.icons.AllIcons
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
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.jsonPrimitive
import java.awt.Point
import java.nio.file.Path
import javax.swing.JComponent
import javax.swing.JTextField
import com.intellij.ui.dsl.builder.*
import java.awt.Dimension
import javax.swing.JSpinner

class ProjectSettingsForm(private val project: Project) : Configurable {
    private lateinit var enabled: Cell<JBCheckBox>
    private lateinit var debug: Cell<JBCheckBox>
    private lateinit var scriptPath: Cell<TextFieldWithBrowseButton>
    private lateinit var pathMappings: Cell<PathMappingsComponent>
    private lateinit var supportedLanguages: Cell<JTextField>
    private lateinit var goToElementFilter: Cell<JTextField>
    private lateinit var infoButton: Cell<ActionButton>
    private lateinit var executionTimeout: Cell<JSpinner>
    private var changed: Boolean = false

    fun createComponents(): DialogPanel {
        val self = this
        val panel = panel {
            row("Plugin Enabled") {
                enabled = checkBox("")
            }
            group {
                row("Debug") {
                    debug = checkBox("")
                }.rowComment("Debug mode. Passed as `PSA_DEBUG` into the executable script")
                row("Script Path") {
                    scriptPath = textFieldWithBrowseButton(
                        "Choose PSA Executable FIle",
                        project,
                        FileChooserDescriptorFactory
                            .createSingleFileDescriptor()
                            .withFileFilter { e ->
                                java.nio.file.Files.isExecutable(Path.of(e.path))
                            }
                            .withShowHiddenFiles(true)
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
                    }
                        .gap(RightGap.SMALL)

                    @Suppress("DialogTitleCapitalization")
                    val action = object : DumbAwareAction("Get info from your executable script", "", AllIcons.General.BalloonInformation) {
                        override fun actionPerformed(e: AnActionEvent) {
                            self.getInfo()
                        }
                    }
                    infoButton = actionButton(action)
                }.rowComment("Path to the PSA executable script. Must be an executable file")
                row("Execution Timeout") {
                    executionTimeout = cell(JSpinner())
                }
                row("Path Mappings") {
                    val pathMappingsComponent = PathMappingsComponent()
                    pathMappingsComponent.text = ""
                    pathMappingsComponent.minimumSize = Dimension(200, 0)
                    pathMappings = cell(pathMappingsComponent)
                }.rowComment("Path mappings (for projects that running remotely (within Docker/Vagrant/etc.)). Source mapping should start from `/`\n" +
                        "as project root")
                row("Supported Languages") {
                    supportedLanguages = textField().enabled(false)
                }.rowComment("Programming languages supported by your autocompletion")
                row("GoTo Element Filter") {
                    goToElementFilter = textField().enabled(false)
                }.rowComment("GoTo element filter returned by you autocompletion. Read more in \n" +
                        "<a href=\"https://github.com/sam0delkin/intellij-psa#goto-optimizations\">performance</a> documentation section.")
            }.enabledIf(enabled.selected).rowComment("For more info, please check the <a href=\"https://github.com/sam0delkin/intellij-psa#documentation\">documentation</a>.")
        }

        return panel
    }

    private fun getInfo() {
        val service = project.service<CompletionService>()
        try {
            this.goToElementFilter.component.setText("")
            this.supportedLanguages.component.setText("")
            val info = service.getInfo(settings, project, this.scriptPath.component.text)
            var filter: List<String> = listOf()
            var languages: List<String> = listOf()
            var templateCount = 0

            if (info.containsKey("goto_element_filter")) {
                filter = (info.get("goto_element_filter") as JsonArray).map { i -> i.jsonPrimitive.content }
                this.goToElementFilter.component.setText(filter.joinToString(","))
            }

            if (info.containsKey("supported_languages")) {
                languages = (info.get("supported_languages") as JsonArray).map { i -> i.jsonPrimitive.content }
                this.supportedLanguages.component.setText(languages.joinToString(","))
            }

            if (info.containsKey("templates")) {
                val templates = info.get("templates") as JsonArray
                templateCount = templates.size
                this.changed = true
            }

            service.updateInfo(settings, info)
            val languagesString = "<ul>" + languages.map { i -> "<li>- $i</li>" }.joinToString("") + "<ul>"
            val filterString = "<ul>" + filter.map { i -> "<li>- $i</li>" }.joinToString("") + "<ul>"

            val tooltip = com.intellij.ui.GotItTooltip(
                "PSA",
                "Successfully retrieved info: <br />" +
                        "Supported Languages: $languagesString<br />" +
                        "GoTo Element Filter: $filterString<br />" +
                        "Template Count: ${templateCount}"
            )
                .withIcon(AllIcons.General.BalloonInformation)
                .withButtonLabel("OK")
            tooltip.createAndShow(this.infoButton.component) { c, _ -> Point(c.width, c.height / 2) }
        } catch (e: Exception) {
            val tooltip = com.intellij.ui.GotItTooltip(
                "PSA",
                "Error during retrieve info: <br />" + e.message + "<br /><br /> For help, please " +
                        "check the <a href=\"https://github.com/sam0delkin/intellij-psa#documentation\">documentation</a>"
            ).withIcon(AllIcons.General.BalloonError)
                .withButtonLabel("OK")
            tooltip.createAndShow(this.infoButton.component) { c, _ -> Point(c.width, c.height / 2) }
        }
    }

    override fun createComponent(): JComponent {
        return this.createComponents()
    }

    fun updateInfoButtonEnabled() {
        this.infoButton.component.setEnabled(this.enabled.component.isSelected && this.scriptPath.component.text !== "")
    }

    override fun isModified(): Boolean {
        return (
                enabled.component.isSelected != settings.pluginEnabled
                        || debug.component.isSelected != settings.debug

                        || scriptPath.component.text != settings.scriptPath
                        || pathMappings.component.mappingSettings.pathMappings.map { el -> el.localRoot + " ->" + el.remoteRoot }
                    .joinToString(",") != settings.pathMappings?.map { el -> el.localRoot + " ->" + el.remoteRoot }
                    ?.joinToString(",")
                        || supportedLanguages.component.text != settings.supportedLanguages
                        || goToElementFilter.component.text != settings.goToFilter
                        || executionTimeout.component.value != settings.executionTimeout
                        || changed

                )
    }

    override fun reset() {
        updateUIFromSettings()
    }

    @Throws(ConfigurationException::class)
    override fun apply() {
        val completionService = project.service<CompletionService>()
        settings.pluginEnabled = enabled.component.isSelected
        settings.debug = debug.component.isSelected
        settings.scriptPath = scriptPath.component.text.trim()
        settings.pathMappings = pathMappings.component.mappingSettings.pathMappings.toTypedArray()
        settings.supportedLanguages = supportedLanguages.component.text
        settings.goToFilter = goToElementFilter.component.text
        settings.executionTimeout = executionTimeout.component.value as Int
        changed = false
        if (!settings.pluginEnabled && completionService.lastResultSucceed) {
            completionService.lastResultSucceed = false
            completionService.lastResultMessage = ""
        } else if (settings.pluginEnabled && completionService.lastResultSucceed) {
            completionService.lastResultSucceed = true
            completionService.lastResultMessage = ""
        }

        val psaStatusBarWidgetFactory = PsaStatusBarWidgetFactory()
        if (null === project.service<StatusBarWidgetsManager>().findWidgetFactory(PsaStatusBarWidgetFactory.WIDGET_ID)) {
            project.service<StatusBarWidgetsManager>().updateWidget(psaStatusBarWidgetFactory)
        }
        project.service<StatusBarWidgetsManager>().updateAllWidgets()
    }

    private fun updateUIFromSettings() {
        enabled.component.setSelected(settings.pluginEnabled)
        debug.component.setSelected(settings.debug)
        scriptPath.component.setText(settings.scriptPath)
        settings.pathMappings?.map { el -> pathMappings.component.mappingSettings.add(el) }
        pathMappings.component.setMappingSettings(pathMappings.component.mappingSettings)
        supportedLanguages.component.setText(settings.supportedLanguages)
        goToElementFilter.component.setText(settings.goToFilter)
        executionTimeout.component.value = settings.executionTimeout
        changed = false
    }

    private val settings: Settings
        get() = project.service<CompletionService>().getSettings()

    override fun getDisplayName(): String {
        return "Project Specific Autocomplete"
    }
}