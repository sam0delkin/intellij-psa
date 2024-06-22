package com.github.sam0delkin.intellijpsa.settings

import com.github.sam0delkin.intellijpsa.services.CompletionService
import com.intellij.execution.util.PathMappingsComponent
import com.intellij.icons.AllIcons
import com.intellij.openapi.actionSystem.AnActionEvent
import com.intellij.openapi.actionSystem.impl.ActionButton
import com.intellij.openapi.components.service
import com.intellij.openapi.fileChooser.FileChooser
import com.intellij.openapi.fileChooser.FileChooserDescriptor
import com.intellij.openapi.fileChooser.FileChooserDescriptorFactory
import com.intellij.openapi.options.Configurable
import com.intellij.openapi.options.ConfigurationException
import com.intellij.openapi.project.DumbAwareAction
import com.intellij.openapi.project.Project
import com.intellij.openapi.project.guessProjectDir
import com.intellij.openapi.ui.DialogPanel
import com.intellij.openapi.ui.TextBrowseFolderListener
import com.intellij.openapi.ui.TextFieldWithBrowseButton
import com.intellij.openapi.vfs.VfsUtil
import com.intellij.ui.components.JBCheckBox
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.jsonPrimitive
import org.jetbrains.annotations.Nls
import java.awt.Point
import java.awt.event.ActionEvent
import java.nio.file.Path
import javax.swing.JComponent
import javax.swing.JTextField
import com.intellij.ui.dsl.builder.*

class ProjectSettingsForm(private val project: Project) : Configurable {
    private lateinit var enabled: Cell<JBCheckBox>
    private lateinit var debug: Cell<JBCheckBox>
    private lateinit var scriptPath: Cell<TextFieldWithBrowseButton>
    private lateinit var pathMappings: Cell<PathMappingsComponent>
    private lateinit var supportedLanguages: Cell<JTextField>
    private lateinit var goToElementFilter: Cell<JTextField>
    private lateinit var infoButton: Cell<ActionButton>

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
                    scriptPath = textFieldWithBrowseButton()
                        .gap(RightGap.SMALL)
                    scriptPath.component.addBrowseFolderListener(
                        createBrowseFolderListener(
                            scriptPath.component.textField,
                            FileChooserDescriptorFactory
                                .createSingleFileDescriptor()
                                .withShowHiddenFiles(true)
                                .withFileFilter { e ->
                                    java.nio.file.Files.isExecutable(Path.of(e.path))
                                }
                        )
                    )
                    val action = object : DumbAwareAction("Get info from your executable script", "", AllIcons.General.BalloonInformation) {
                        override fun actionPerformed(e: AnActionEvent) {
                            val service = project.service<CompletionService>()
                            try {
                                self.goToElementFilter.component.setText("")
                                self.supportedLanguages.component.setText("")
                                val info = service.getInfo(settings, project, self.scriptPath.component.text)
                                var filter = ""
                                var languages = ""
                                if (info.containsKey("goto_element_filter")) {
                                    filter = (info.get("goto_element_filter") as JsonArray).map { i -> i.jsonPrimitive.content }
                                        .joinToString(",")
                                    self.goToElementFilter.component.setText(filter)
                                }
                                if (info.containsKey("supported_languages")) {
                                    languages = (info.get("supported_languages") as JsonArray).map { i -> i.jsonPrimitive.content }
                                        .joinToString(",")
                                    self.supportedLanguages.component.setText(languages)
                                }

                                val tooltip = com.intellij.ui.GotItTooltip(
                                    "PSA",
                                    "Successfully retrieved info: <br />Supported Languages: $languages<br />GoTo Element Filter: $filter"
                                )
                                    .withIcon(AllIcons.General.BalloonInformation)
                                    .withButtonLabel("OK")
                                tooltip.createAndShow(self.infoButton.component) { c, _ -> Point(c.width, c.height / 2) }
                            } catch (e: Exception) {
                                val tooltip = com.intellij.ui.GotItTooltip(
                                    "PSA",
                                    "Error during retrieve info: <br />" + e.message + "<br /><br /> For help, please " +
                                            "check the <a href=\"https://github.com/sam0delkin/intellij-psa#documentation\">documentation</a>"
                                ).withIcon(AllIcons.General.BalloonError)
                                    .withButtonLabel("OK")
                                tooltip.createAndShow(self.infoButton.component) { c, _ -> Point(c.width, c.height / 2) }
                            }
                        }
                    }
                    infoButton = actionButton(action)
                }.rowComment("Path to the PSA executable script. Must be an executable file")
                row("Path Mappings") {
                    val pathMappingsComponent = PathMappingsComponent()
                    pathMappingsComponent.text = ""
                    pathMappings = cell(pathMappingsComponent)
                }.rowComment("Path mappings (for projects that running remotely (within Docker/Vagrant/etc.)). Source mapping should start from `/`\n" +
                        "as project root")
                row("Supported Languages") {
                    supportedLanguages = textField().enabled(false)
                }.rowComment("Programming languages supported by your autocompletion")
                row("GoTO Element Filter") {
                    goToElementFilter = textField().enabled(false)
                }.rowComment("GoTo element filter returned by you autocompletion. Read more in \n" +
                        "<a href=\"https://github.com/sam0delkin/intellij-psa#goto-optimizations\">performance</a> documentation section.")
            }.enabledIf(enabled.selected).rowComment("For more info, please check the <a href=\"https://github.com/sam0delkin/intellij-psa#documentation\">documentation</a>.")
        }

        return panel
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

                )
    }

    override fun reset() {
        updateUIFromSettings()
    }

    @Throws(ConfigurationException::class)
    override fun apply() {
        settings.pluginEnabled = enabled.component.isSelected
        settings.debug = debug.component.isSelected
        settings.scriptPath = scriptPath.component.text.trim()
        settings.pathMappings = pathMappings.component.mappingSettings.pathMappings.toTypedArray()
        settings.supportedLanguages = supportedLanguages.component.text
        settings.goToFilter = goToElementFilter.component.text
    }

    private fun updateUIFromSettings() {
        enabled.component.setSelected(settings.pluginEnabled)
        debug.component.setSelected(settings.debug)
        scriptPath.component.setText(settings.scriptPath)
        settings.pathMappings?.map { el -> pathMappings.component.mappingSettings.add(el) }
        supportedLanguages.component.setText(settings.supportedLanguages)
        goToElementFilter.component.setText(settings.goToFilter)
    }

    private val settings: Settings
        get() = project.service<CompletionService>().getSettings()

    private fun createBrowseFolderListener(
        textField: JTextField,
        fileChooserDescriptor: FileChooserDescriptor
    ): TextBrowseFolderListener {
        val currentProject = project
        return object : TextBrowseFolderListener(fileChooserDescriptor) {
            override fun actionPerformed(e: ActionEvent) {
                val projectDirectory = currentProject.guessProjectDir()
                val selectedFile = FileChooser.chooseFile(
                    fileChooserDescriptor,
                    currentProject,
                    VfsUtil.findRelativeFile(textField.text, projectDirectory)
                )
                    ?: return  // Ignore but keep the previous path
                assert(projectDirectory != null)
                var path = VfsUtil.getRelativePath(selectedFile, projectDirectory!!, '/')
                if (null == path) {
                    path = selectedFile.path
                }
                textField.text = path
                updateInfoButtonEnabled()
            }
        }
    }

    override fun getDisplayName(): @Nls String {
        return "Project Specific Autocomplete"
    }
}