@file:Suppress("ktlint:standard:no-wildcard-imports")

package com.github.sam0delkin.intellijpsa.settings

import com.github.sam0delkin.intellijpsa.services.CompletionService
import com.github.sam0delkin.intellijpsa.statusBar.PsaStatusBarWidgetFactory
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
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.jsonPrimitive
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
    private lateinit var indexingEnabled: Cell<JBCheckBox>
    private lateinit var indexingConcurrency: Cell<JSpinner>
    private lateinit var indexingBatchCount: Cell<JSpinner>
    private lateinit var indexingMaxElements: Cell<JSpinner>
    private lateinit var indexingUseOnlyIndexedElements: Cell<JBCheckBox>
    private lateinit var pathMappings: Cell<PathMappingsComponent>
    private lateinit var supportedLanguages: Cell<JTextField>
    private lateinit var goToElementFilter: Cell<JTextField>
    private lateinit var infoButton: Cell<ActionButton>
    private lateinit var supportedLanguagesButton: Cell<ActionButton>
    private lateinit var executionTimeout: Cell<JSpinner>
    private var changed: Boolean = false

    fun createComponents(): DialogPanel {
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
                            textFieldWithBrowseButton(
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
                        infoButton = actionButton(action)
                    }.rowComment("Path to the PSA executable script. Must be an executable file")
                    row("Indexing Enabled") {
                        indexingEnabled = checkBox("")
                    }.rowComment("Is indexing of currently opened files enabled")
                    row("Indexing Concurrency") {
                        val availableProcessors = Runtime.getRuntime().availableProcessors()
                        indexingConcurrency =
                            cell(JSpinner(SpinnerNumberModel(availableProcessors, 1, availableProcessors, 1)))
                    }.enabledIf(indexingEnabled.selected)
                        .rowComment("Maximum concurrency level for indexing. Default: Count of CPU cores in your system")
                    row("Indexing Batch Count") {
                        indexingBatchCount =
                            cell(JSpinner(SpinnerNumberModel(50, 10, 500, 10)))
                    }.enabledIf(indexingEnabled.selected)
                        .rowComment("Count of elements which will be sent to PSA script in batch during indexing")
                    row("Indexing Max File Elements") {
                        indexingMaxElements =
                            cell(JSpinner(SpinnerNumberModel(2000, 1, 1000000, 100)))
                    }.enabledIf(indexingEnabled.selected)
                        .rowComment(
                            "Maximum number of indexable elements on file. In case of number of elements is " +
                                "greater than this value, file will not be indexed",
                        )
                    row("Process only indexed elements") {
                        indexingUseOnlyIndexedElements =
                            checkBox("")
                    }.enabledIf(indexingEnabled.selected)
                        .rowComment("In case of indexing of file compelted, only indexed elements Completions/GoTo will work.")
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
                        supportedLanguagesButton = actionButton(action)
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

    fun updateInfoButtonEnabled() {
        this.infoButton.component.setEnabled(this.enabled.component.isSelected && this.scriptPath.component.text !== "")
    }

    override fun isModified(): Boolean =
        (
            enabled.component.isSelected != settings.pluginEnabled ||
                debug.component.isSelected != settings.debug ||

                scriptPath.component.text != settings.scriptPath ||
                indexingEnabled.component.isSelected != settings.indexingEnabled ||
                indexingConcurrency.component.value != settings.indexingConcurrency ||
                indexingBatchCount.component.value != settings.indexingBatchCount ||
                indexingMaxElements.component.value != settings.indexingMaxElements ||
                indexingUseOnlyIndexedElements.component.isSelected != settings.indexingUseOnlyIndexedElements ||
                pathMappings.component.mappingSettings.pathMappings
                    .map { el -> el.localRoot + " ->" + el.remoteRoot }
                    .joinToString(",") !=
                settings.pathMappings
                    ?.map { el -> el.localRoot + " ->" + el.remoteRoot }
                    ?.joinToString(",") ||
                supportedLanguages.component.text != settings.supportedLanguages ||
                goToElementFilter.component.text != settings.goToFilter ||
                executionTimeout.component.value != settings.executionTimeout ||
                changed

        )

    override fun reset() {
        updateUIFromSettings()
    }

    @Throws(ConfigurationException::class)
    override fun apply() {
        val completionService = project.service<CompletionService>()
        settings.pluginEnabled = enabled.component.isSelected
        settings.debug = debug.component.isSelected
        settings.scriptPath = scriptPath.component.text.trim()
        settings.indexingEnabled = indexingEnabled.component.isSelected
        settings.indexingConcurrency = indexingConcurrency.component.value as Int
        settings.indexingBatchCount = indexingBatchCount.component.value as Int
        settings.indexingMaxElements = indexingMaxElements.component.value as Int
        settings.indexingUseOnlyIndexedElements = indexingUseOnlyIndexedElements.component.isSelected
        settings.pathMappings =
            pathMappings.component.mappingSettings.pathMappings
                .toTypedArray()
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
        if (null ===
            project
                .service<StatusBarWidgetsManager>()
                .findWidgetFactory(PsaStatusBarWidgetFactory.WIDGET_ID)
        ) {
            project.service<StatusBarWidgetsManager>().updateWidget(psaStatusBarWidgetFactory)
        }
        project.service<StatusBarWidgetsManager>().updateAllWidgets()
    }

    private fun updateUIFromSettings() {
        enabled.component.setSelected(settings.pluginEnabled)
        debug.component.setSelected(settings.debug)
        scriptPath.component.setText(settings.scriptPath)
        indexingEnabled.component.isSelected = settings.indexingEnabled
        indexingConcurrency.component.value = settings.indexingConcurrency
        indexingBatchCount.component.value = settings.indexingBatchCount
        indexingMaxElements.component.value = settings.indexingMaxElements
        indexingUseOnlyIndexedElements.component.isSelected = settings.indexingUseOnlyIndexedElements
        settings.pathMappings?.map { el -> pathMappings.component.mappingSettings.add(el) }
        pathMappings.component.setMappingSettings(pathMappings.component.mappingSettings)
        supportedLanguages.component.setText(settings.supportedLanguages)
        goToElementFilter.component.setText(settings.goToFilter)
        executionTimeout.component.value = settings.executionTimeout
        changed = false
    }

    private val settings: Settings
        get() = project.service<CompletionService>().getSettings()

    override fun getDisplayName(): String = "Project Specific Autocomplete"
}
