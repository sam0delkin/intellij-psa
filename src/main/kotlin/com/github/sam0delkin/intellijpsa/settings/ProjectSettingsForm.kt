package com.github.sam0delkin.intellijpsa.settings

import com.github.sam0delkin.intellijpsa.services.CompletionService
import com.intellij.execution.util.PathMappingsComponent
import com.intellij.icons.AllIcons
import com.intellij.ide.BrowserUtil
import com.intellij.openapi.components.service
import com.intellij.openapi.fileChooser.FileChooser
import com.intellij.openapi.fileChooser.FileChooserDescriptor
import com.intellij.openapi.fileChooser.FileChooserDescriptorFactory
import com.intellij.openapi.options.Configurable
import com.intellij.openapi.options.ConfigurationException
import com.intellij.openapi.project.Project
import com.intellij.openapi.project.guessProjectDir
import com.intellij.openapi.ui.TextBrowseFolderListener
import com.intellij.openapi.ui.TextFieldWithBrowseButton
import com.intellij.openapi.vfs.VfsUtil
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.jsonPrimitive
import org.jetbrains.annotations.Nls
import java.awt.Point
import java.awt.event.ActionEvent
import java.nio.file.Path
import javax.swing.JButton
import javax.swing.JCheckBox
import javax.swing.JComponent
import javax.swing.JPanel
import javax.swing.JTextField

class ProjectSettingsForm(private val project: Project) : Configurable {
    private lateinit var panel1: JPanel
    private lateinit var enabled: JCheckBox
    private lateinit var debug: JCheckBox
    private lateinit var scriptPath: TextFieldWithBrowseButton
    private lateinit var pathMappings: PathMappingsComponent
    private lateinit var supportedLanguages: JTextField
    private lateinit var goToElementFilter: JTextField
    private lateinit var infoButton: JButton

    override fun createComponent(): JComponent {
        this.enabled.addItemListener { e ->
            if ((e.source as JCheckBox).isSelected) {
                this.scriptPath.setEnabled(true)
                this.pathMappings.setEnabled(true)
                this.debug.setEnabled(true)
                this.updateInfoButtonEnabled()
            } else {
                this.scriptPath.setEnabled(false)
                this.pathMappings.setEnabled(false)
                this.debug.setEnabled(false)
                this.updateInfoButtonEnabled()
            }
        }

        this.scriptPath.addBrowseFolderListener(
            createBrowseFolderListener(
                this.scriptPath.textField,
                FileChooserDescriptorFactory
                    .createSingleFileDescriptor()
                    .withShowHiddenFiles(true)
                    .withFileFilter { e ->
                        java.nio.file.Files.isExecutable(Path.of(e.path))
                    }
            )
        )

        this.infoButton.icon = AllIcons.General.BalloonInformation
        this.infoButton.addActionListener { e ->
            run {
                val service = project.service<CompletionService>()
                try {
                    this.goToElementFilter.setText("")
                    this.supportedLanguages.setText("")
                    val info = service.getInfo(settings, project, this.scriptPath.text)
                    var filter = ""
                    var languages = ""
                    if (info.containsKey("goto_element_filter")) {
                        filter = (info.get("goto_element_filter") as JsonArray).map { i -> i.jsonPrimitive.content }
                            .joinToString(",")
                        this.goToElementFilter.setText(filter)
                    }
                    if (info.containsKey("supported_languages")) {
                        languages = (info.get("supported_languages") as JsonArray).map { i -> i.jsonPrimitive.content }
                            .joinToString(",")
                        this.supportedLanguages.setText(languages)
                    }

                    val tooltip = com.intellij.ui.GotItTooltip(
                        "PSA",
                        "Successfully retrieved info: <br />Supported Languages: $languages<br />GoTo Element Filter: $filter"
                    ).withIcon(AllIcons.General.BalloonInformation)
                    tooltip.createAndShow(this.infoButton) { c, _ -> Point(c.width, c.height / 2) }
                } catch (e: Exception) {
                    val tooltip = com.intellij.ui.GotItTooltip(
                        "PSA",
                        "Error during retrieve info: <br />" + e.message
                    ).withIcon(AllIcons.General.BalloonError)
                        .withButtonLabel("OK")
                        .withSecondaryButton("Help", { BrowserUtil.browse("https://github.com/sam0delkin/intellij-psa#documentation") })
                    tooltip.createAndShow(this.infoButton) { c, _ -> Point(c.width, c.height / 2) }
                }
            }
        }
        this.updateInfoButtonEnabled()

        return this.panel1
    }

    fun updateInfoButtonEnabled() {
        this.infoButton.setEnabled(this.enabled.isSelected && this.scriptPath.text !== "")
    }

    override fun isModified(): Boolean {
        return (
                enabled.isSelected != settings.pluginEnabled
                        || debug.isSelected != settings.debug

                        || scriptPath.text != settings.scriptPath
                        || pathMappings.mappingSettings.pathMappings.map { el -> el.localRoot + " ->" + el.remoteRoot }
                    .joinToString(",") != settings.pathMappings?.map { el -> el.localRoot + " ->" + el.remoteRoot }
                    ?.joinToString(",")
                        || supportedLanguages.text != settings.supportedLanguages
                        || goToElementFilter.text != settings.goToFilter

                )
    }

    override fun reset() {
        updateUIFromSettings()
    }

    @Throws(ConfigurationException::class)
    override fun apply() {
        settings.pluginEnabled = enabled.isSelected
        settings.debug = debug.isSelected
        settings.scriptPath = scriptPath.text.trim()
        settings.pathMappings = pathMappings.mappingSettings.pathMappings.toTypedArray()
        settings.supportedLanguages = supportedLanguages.text
        settings.goToFilter = goToElementFilter.text
    }

    private fun updateUIFromSettings() {
        enabled.setSelected(settings.pluginEnabled)
        debug.setSelected(settings.debug)
        scriptPath.setText(settings.scriptPath)
        settings.pathMappings?.map { el -> pathMappings.mappingSettings.add(el) }
        supportedLanguages.setText(settings.supportedLanguages)
        goToElementFilter.setText(settings.goToFilter)
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