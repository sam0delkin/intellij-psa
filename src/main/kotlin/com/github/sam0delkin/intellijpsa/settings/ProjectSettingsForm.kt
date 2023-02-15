package com.github.sam0delkin.intellijpsa.settings

import com.github.sam0delkin.intellijpsa.services.ProjectService
import com.intellij.execution.util.PathMappingsComponent
import com.intellij.openapi.components.service
import com.intellij.openapi.fileChooser.FileChooser
import com.intellij.openapi.fileChooser.FileChooserDescriptor
import com.intellij.openapi.fileChooser.FileChooserDescriptorFactory
import com.intellij.openapi.observable.util.bindEnabled
import com.intellij.openapi.options.Configurable
import com.intellij.openapi.options.ConfigurationException
import com.intellij.openapi.project.Project
import com.intellij.openapi.project.guessProjectDir
import com.intellij.openapi.ui.TextBrowseFolderListener
import com.intellij.openapi.ui.TextFieldWithBrowseButton
import com.intellij.openapi.vfs.VfsUtil
import org.jetbrains.annotations.Nls
import java.awt.event.ActionEvent
import javax.swing.JCheckBox
import javax.swing.JComponent
import javax.swing.JPanel
import javax.swing.JTextField

class ProjectSettingsForm(private val project: Project) : Configurable {
    private lateinit var panel1: JPanel
    private lateinit var enabled: JCheckBox
    private lateinit var debug: JCheckBox

    private lateinit var phpEnabled: JCheckBox
    private lateinit var phpScriptPath: TextFieldWithBrowseButton
    private lateinit var phpPathMappings: PathMappingsComponent

    private lateinit var jsEnabled: JCheckBox
    private lateinit var jsScriptPath: TextFieldWithBrowseButton
    private lateinit var jsPathMappings: PathMappingsComponent

    override fun createComponent(): JComponent? {
        this.enabled.addItemListener { e ->
            if ((e.source as JCheckBox).isSelected) {
                this.phpEnabled.setEnabled(true)
                this.phpScriptPath.setEnabled(true)
                this.phpPathMappings.setEnabled(true)

                this.jsEnabled.setEnabled(true)
                this.jsScriptPath.setEnabled(true)
                this.jsPathMappings.setEnabled(true)

                this.debug.setEnabled(true)
            } else {
                this.phpEnabled.setEnabled(false)
                this.phpScriptPath.setEnabled(false)
                this.phpPathMappings.setEnabled(false)

                this.jsEnabled.setEnabled(false)
                this.jsScriptPath.setEnabled(false)
                this.jsPathMappings.setEnabled(false)
                this.debug.setEnabled(false)
            }
        }
        this.phpScriptPath.addBrowseFolderListener(
            createBrowseFolderListener(
                this.phpScriptPath.textField,
                FileChooserDescriptorFactory.createSingleFileDescriptor()
            )
        )
        this.phpEnabled.addItemListener { e ->
            if ((e.source as JCheckBox).isSelected) {
                this.phpScriptPath.setEnabled(true)
                this.phpPathMappings.setEnabled(true)
            } else {
                this.phpScriptPath.setEnabled(false)
                this.phpPathMappings.setEnabled(false)
            }
        }
        this.jsEnabled.addItemListener { e ->
            if ((e.source as JCheckBox).isSelected) {
                this.jsScriptPath.setEnabled(true)
                this.jsPathMappings.setEnabled(true)
            } else {
                this.phpScriptPath.setEnabled(false)
                this.jsPathMappings.setEnabled(false)
            }
        }

        this.jsScriptPath.addBrowseFolderListener(
            createBrowseFolderListener(
                this.jsScriptPath.textField,
                FileChooserDescriptorFactory.createSingleFileDescriptor()
            )
        )
        return this.panel1
    }

    override fun isModified(): Boolean {
        return (
                enabled.isSelected != settings.pluginEnabled
                        || debug.isSelected != settings.debug

                        || phpEnabled.isSelected != settings.phpEnabled
                        || phpScriptPath.text != settings.phpScriptPath
                        || phpPathMappings.mappingSettings.pathMappings != settings.phpPathMappings

                        || jsEnabled.isSelected != settings.jsEnabled
                        || jsScriptPath.text != settings.jsScriptPath
                        || jsPathMappings.mappingSettings.pathMappings != settings.jsPathMappings
                )
    }

    override fun reset() {
        updateUIFromSettings()
    }

    @Throws(ConfigurationException::class)
    override fun apply() {
        settings.pluginEnabled = enabled.isSelected
        settings.debug = debug.isSelected

        settings.phpEnabled = phpEnabled.isSelected
        settings.phpScriptPath = phpScriptPath.text.trim()
        settings.phpPathMappings = phpPathMappings.mappingSettings.pathMappings.toTypedArray()

        settings.jsEnabled = jsEnabled.isSelected
        settings.jsScriptPath = jsScriptPath.text.trim()
        settings.jsPathMappings = jsPathMappings.mappingSettings.pathMappings.toTypedArray()
    }

    private fun updateUIFromSettings() {
        enabled.setSelected(settings.pluginEnabled)
        debug.setSelected(settings.debug)

        phpEnabled.setSelected(settings.phpEnabled)
        phpScriptPath.setText(settings.phpScriptPath)
        settings.phpPathMappings?.map { el -> phpPathMappings.mappingSettings.add(el) }

        jsEnabled.setSelected(settings.jsEnabled)
        jsScriptPath.setText(settings.jsScriptPath)
        settings.jsPathMappings?.map { el -> jsPathMappings.mappingSettings.add(el) }
    }

    private val settings: Settings
        get() = project.service<ProjectService>().getSettings()

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
            }
        }
    }

    override fun getDisplayName(): @Nls String? {
        return "Project Specific Autocomplete"
    }
}