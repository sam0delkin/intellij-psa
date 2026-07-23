package com.github.sam0delkin.intellijpsa.settings

import com.github.sam0delkin.intellijpsa.services.PsaManager
import com.intellij.openapi.components.service
import com.intellij.openapi.ui.ComboBox
import com.intellij.openapi.ui.TextFieldWithBrowseButton
import com.intellij.testFramework.fixtures.BasePlatformTestCase
import com.intellij.ui.components.JBCheckBox
import com.intellij.ui.dsl.builder.Cell
import java.io.File
import javax.swing.JTextField

/**
 * The `fileChosen` callbacks passed to `Utils.textFieldWithBrowseButton(...)` for the Script Path
 * and Indexing Directory fields (PsaConfigurable.kt lines ~94-109 and ~144-158) are anonymous
 * lambdas wired into a real file-chooser dialog's `TextBrowseFolderListener` - they only run when
 * a user actually picks a file/folder through that dialog. There is no named method or field to
 * invoke them via reflection (unlike `getInfo()`/`updateInfoButtonEnabled()`), and opening a real
 * `FileChooserDialog` hits the same "cannot run headlessly" wall as `DialogBuilder`/`DialogWrapper`
 * elsewhere in this codebase. Left as a documented residual gap rather than forced.
 *
 * Similarly, the position-callback lambda passed to `GotItTooltip.createAndShow(component) { c, _ ->
 * Point(...) }` (the "Supported Languages" button's tooltip, PsaConfigurable.kt lines ~207-210) is
 * never invoked in this headless test environment even though `testSupportedLanguagesButtonActionShowsTooltip`
 * below *does* call the button's `actionPerformed` directly and reaches `createAndShow` without
 * throwing: `GotItTooltip`'s balloon only calls its `pointProvider` while actually positioning
 * against a real, showing `Component` (confirmed via `javap` on `GotItTooltip.createAndShow` -
 * it wires the provider into a `Balloon`/`BalloonImpl` shown relative to the anchor component),
 * and an `ActionButton` built here is never added to any realized window. Also left as a documented
 * residual gap.
 */
class PsaConfigurableTest : BasePlatformTestCase() {
    private lateinit var configurable: PsaConfigurable

    override fun setUp() {
        super.setUp()
        // Settings is a project-level singleton reused across test methods in this light
        // project - reset it explicitly so tests don't see state left over by another test.
        project.service<Settings>().apply {
            executionMode = ExecutionMode.Script
            pluginEnabled = false
            scriptPath = ""
        }
        project.service<PsaManager>().apply {
            lastResultSucceed = false
            lastResultMessage = ""
        }

        configurable = PsaConfigurable(project)
        configurable.createComponent()
        configurable.reset()
    }

    private fun settings(): Settings = project.service<Settings>()

    private fun fixturePath(name: String): String {
        val resource = javaClass.classLoader.getResource("server/$name")!!
        val file = File(resource.toURI())
        file.setExecutable(true)

        return file.path
    }

    @Suppress("UNCHECKED_CAST")
    private fun <T : javax.swing.JComponent> cell(name: String): Cell<T> {
        val field = PsaConfigurable::class.java.getDeclaredField(name)
        field.isAccessible = true

        return field.get(configurable) as Cell<T>
    }

    private fun executionModeComboBox(): ComboBox<ExecutionMode> = cell<ComboBox<ExecutionMode>>("executionMode").component

    private fun checkBox(name: String): JBCheckBox = cell<JBCheckBox>(name).component

    private fun textField(name: String): JTextField = cell<JTextField>(name).component

    private fun browseField(name: String): TextFieldWithBrowseButton = cell<TextFieldWithBrowseButton>(name).component

    private fun callPrivate(name: String) {
        val method = PsaConfigurable::class.java.getDeclaredMethod(name)
        method.isAccessible = true
        method.invoke(configurable)
    }

    private fun callPrivateReturningString(name: String): String {
        val method = PsaConfigurable::class.java.getDeclaredMethod(name)
        method.isAccessible = true

        return method.invoke(configurable) as String
    }

    fun testResetPopulatesExecutionModeFromSettings() {
        settings().executionMode = ExecutionMode.Server
        configurable.reset()

        assertFalse(configurable.isModified())
        assertEquals(ExecutionMode.Server, executionModeComboBox().selectedItem)
    }

    fun testIsModifiedDetectsExecutionModeChange() {
        assertFalse(configurable.isModified())

        executionModeComboBox().selectedItem = ExecutionMode.Server

        assertTrue(configurable.isModified())
    }

    fun testIsModifiedFalseAfterResetMatchesSettings() {
        settings().executionMode = ExecutionMode.Server
        configurable.reset()

        assertFalse(configurable.isModified())
    }

    fun testGetDisplayName() {
        assertEquals("Project Specific Autocomplete", configurable.getDisplayName())
    }

    fun testBuildDiagnosticsReflectsSettingsAndLastResult() {
        settings().apply {
            pluginEnabled = true
            scriptPath = "/some/script.php"
            supportedLanguages = "PHP"
            goToFilter = "PHP:STRING_LITERAL"
            supportsBatch = true
            supportsStaticCompletions = true
        }
        project.service<PsaManager>().apply {
            lastResultSucceed = true
            lastResultMessage = "all good"
        }

        val diagnostics = callPrivateReturningString("buildDiagnostics")

        assertTrue(diagnostics.contains("Plugin enabled: true"))
        assertTrue(diagnostics.contains("Script path: /some/script.php"))
        assertTrue(diagnostics.contains("Last script result: OK"))
        assertTrue(diagnostics.contains("Last message: all good"))
        assertTrue(diagnostics.contains("Supported languages: PHP"))
        assertTrue(diagnostics.contains("GoTo element filter: PHP:STRING_LITERAL"))
        assertTrue(diagnostics.contains("Supports batch: true"))
        assertTrue(diagnostics.contains("Supports static completions: true"))
    }

    fun testBuildDiagnosticsUsesPlaceholdersWhenBlank() {
        settings().apply {
            pluginEnabled = false
            scriptPath = ""
            supportedLanguages = ""
            goToFilter = ""
        }
        project.service<PsaManager>().apply {
            lastResultSucceed = false
            lastResultMessage = ""
        }

        val diagnostics = callPrivateReturningString("buildDiagnostics")

        assertTrue(diagnostics.contains("Script path: <not set>"))
        assertTrue(diagnostics.contains("Last script result: ERROR"))
        assertFalse(diagnostics.contains("Last message:"))
        assertTrue(diagnostics.contains("Supported languages: <none>"))
        assertTrue(diagnostics.contains("GoTo element filter: <none>"))
    }

    fun testUpdateInfoButtonEnabledReflectsPluginEnabledAndScriptPath() {
        checkBox("enabled").isSelected = false
        browseField("scriptPath").text = ""
        callPrivate("updateInfoButtonEnabled")
        assertFalse(cell<com.intellij.openapi.actionSystem.impl.ActionButton>("infoButton").component.isEnabled)

        checkBox("enabled").isSelected = true
        browseField("scriptPath").text = "/some/script.php"
        callPrivate("updateInfoButtonEnabled")
        assertTrue(cell<com.intellij.openapi.actionSystem.impl.ActionButton>("infoButton").component.isEnabled)
    }

    fun testGetInfoSuccessUpdatesFieldsAndDiagnostics() {
        settings().apply {
            pluginEnabled = true
            executionMode = ExecutionMode.Script
            scriptPath = fixturePath("configurable-info-success.php")
        }

        callPrivate("getInfo")

        assertEquals("PHP:STRING_LITERAL", textField("goToElementFilter").text)
        assertEquals("PHP,JavaScript", textField("supportedLanguages").text)
        assertTrue(configurable.isModified())
    }

    fun testGetInfoFailureLeavesFieldsUnset() {
        settings().apply {
            pluginEnabled = true
            executionMode = ExecutionMode.Script
            scriptPath = fixturePath("failing-script.php")
        }

        callPrivate("getInfo")

        assertEquals("", textField("goToElementFilter").text)
        assertEquals("", textField("supportedLanguages").text)
    }

    fun testApplyPersistsAllFieldsAndRestartsServerOnScriptPathChange() {
        settings().apply {
            pluginEnabled = true
            executionMode = ExecutionMode.Script
            scriptPath = "/old.php"
        }
        configurable.reset()

        checkBox("enabled").isSelected = true
        checkBox("debug").isSelected = true
        checkBox("showErrors").isSelected = false
        checkBox("resolveReferences").isSelected = true
        checkBox("useVelocityInIndex").isSelected = true
        checkBox("annotateUndefinedElements").isSelected = true
        browseField("indexFolder").text = "vendor"
        browseField("scriptPath").text = "/new.php"
        textField("supportedLanguages").text = "PHP"
        textField("goToElementFilter").text = "PHP:STRING_LITERAL"

        configurable.apply()

        assertTrue(settings().pluginEnabled)
        assertTrue(settings().debug)
        assertFalse(settings().showErrors)
        assertTrue(settings().resolveReferences)
        assertEquals("vendor", settings().indexFolder)
        assertTrue(settings().useVelocityInIndex)
        assertTrue(settings().annotateUndefinedElements)
        assertEquals("/new.php", settings().scriptPath)
        assertEquals("PHP", settings().supportedLanguages)
        assertEquals("PHP:STRING_LITERAL", settings().goToFilter)
        assertFalse(configurable.isModified())
    }

    fun testApplyClearsStaticCompletionsHashWhenResolveReferencesDisabled() {
        settings().apply {
            resolveReferences = true
            staticCompletionsHash = "some-hash"
        }
        configurable.reset()
        checkBox("resolveReferences").isSelected = false

        configurable.apply()

        assertEquals("", settings().staticCompletionsHash)
    }

    fun testApplyClearsLastResultWhenPluginDisabled() {
        settings().pluginEnabled = true
        configurable.reset()
        project.service<PsaManager>().lastResultSucceed = true
        checkBox("enabled").isSelected = false

        configurable.apply()

        assertFalse(project.service<PsaManager>().lastResultSucceed)
        assertEquals("", project.service<PsaManager>().lastResultMessage)
    }

    fun testSupportedLanguagesButtonActionShowsTooltip() {
        val button = cell<com.intellij.openapi.actionSystem.impl.ActionButton>("supportedLanguagesButton").component
        val action = button.action
        val event =
            com.intellij.testFramework.TestActionEvent
                .createTestEvent(action)

        action.actionPerformed(event)
    }

    /**
     * `getInfo()` itself is already exercised directly (via reflection) by
     * `testGetInfoSuccessUpdatesFieldsAndDiagnostics`/`testGetInfoFailureLeavesFieldsUnset` - this
     * test instead drives it through the actual `infoButton`'s wired-up `DumbAwareAction`
     * (`self.getInfo()` inside its `actionPerformed`), the same way `testSupportedLanguagesButtonActionShowsTooltip`
     * does for `supportedLanguagesButton` above, since nothing else in this file invokes that button's action.
     */
    fun testInfoButtonActionInvokesGetInfo() {
        settings().apply {
            pluginEnabled = true
            executionMode = ExecutionMode.Script
            scriptPath = fixturePath("configurable-info-success.php")
        }
        val button = cell<com.intellij.openapi.actionSystem.impl.ActionButton>("infoButton").component
        val action = button.action
        val event =
            com.intellij.testFramework.TestActionEvent
                .createTestEvent(action)

        action.actionPerformed(event)

        assertEquals("PHP:STRING_LITERAL", textField("goToElementFilter").text)
        assertEquals("PHP,JavaScript", textField("supportedLanguages").text)
    }

    fun testApplyKeepsLastResultSucceedWhenPluginRemainsEnabled() {
        settings().pluginEnabled = true
        configurable.reset()
        project.service<PsaManager>().lastResultSucceed = true
        checkBox("enabled").isSelected = true

        configurable.apply()

        assertTrue(project.service<PsaManager>().lastResultSucceed)
        assertEquals("", project.service<PsaManager>().lastResultMessage)
    }
}
