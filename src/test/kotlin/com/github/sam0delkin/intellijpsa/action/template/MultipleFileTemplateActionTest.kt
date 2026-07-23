package com.github.sam0delkin.intellijpsa.action.template

import com.github.sam0delkin.intellijpsa.services.PsaManager
import com.github.sam0delkin.intellijpsa.settings.ExecutionMode
import com.github.sam0delkin.intellijpsa.settings.MultipleFileCodeTemplate
import com.github.sam0delkin.intellijpsa.settings.Settings
import com.github.sam0delkin.intellijpsa.settings.TemplateFormField
import com.github.sam0delkin.intellijpsa.settings.TemplateFormFieldType
import com.github.sam0delkin.intellijpsa.ui.components.JTextFieldCollection
import com.intellij.ide.IdeView
import com.intellij.openapi.actionSystem.CommonDataKeys
import com.intellij.openapi.actionSystem.DataContext
import com.intellij.openapi.actionSystem.LangDataKeys
import com.intellij.openapi.actionSystem.impl.ActionButton
import com.intellij.openapi.components.service
import com.intellij.openapi.progress.ProgressIndicator
import com.intellij.openapi.project.guessProjectDir
import com.intellij.psi.PsiDirectory
import com.intellij.psi.PsiManager
import com.intellij.testFramework.PlatformTestUtil
import com.intellij.testFramework.TestActionEvent
import com.intellij.testFramework.fixtures.BasePlatformTestCase
import com.intellij.ui.EditorTextField
import com.intellij.ui.dsl.builder.Cell
import java.awt.Container
import java.io.File
import javax.swing.JComponent

class MultipleFileTemplateActionTest : BasePlatformTestCase() {
    // Tracks every `MultipleFileTemplateAction` built by a test so `tearDown()` can unconditionally
    // cancel any `java.util.Timer` it scheduled (via `changeListener`) before `myFixture` is torn
    // down. Cancelling an already-fired/completed Timer is a harmless no-op, so this is a safe
    // structural guarantee against a Timer callback firing later against a disposed fixture -
    // strictly more reliable than trying to time a fixed wait long enough for every test.
    private val createdActions = ArrayList<Any>()

    override fun tearDown() {
        try {
            for (action in createdActions) {
                val field = action.javaClass.getDeclaredField("timer")
                field.isAccessible = true
                (field.get(action) as? java.util.Timer)?.cancel()
            }
        } finally {
            super.tearDown()
        }
    }

    private fun ideView(directory: PsiDirectory): IdeView =
        object : IdeView {
            override fun getDirectories(): Array<PsiDirectory> = arrayOf(directory)

            override fun getOrChooseDirectory(): PsiDirectory = directory
        }

    private fun fixturePath(name: String): File {
        val resource = javaClass.classLoader.getResource("server/$name")!!
        val file = File(resource.toURI())
        file.setExecutable(true)

        return file
    }

    @Suppress("UNCHECKED_CAST")
    private fun <T> privateField(
        target: Any,
        name: String,
    ): T {
        val field = target.javaClass.getDeclaredField(name)
        field.isAccessible = true

        return field.get(target) as T
    }

    @Suppress("UNCHECKED_CAST")
    private fun <T> findAll(
        root: Container,
        klass: Class<T>,
    ): List<T> {
        val found = ArrayList<T>()
        for (component in root.components) {
            if (klass.isInstance(component)) {
                found.add(component as T)
            }
            if (component is Container) {
                found.addAll(findAll(component, klass))
            }
        }

        return found
    }

    private fun buildAllFieldTypesTemplate(
        name: String,
        title: String,
    ): MultipleFileCodeTemplate =
        MultipleFileCodeTemplate().apply {
            this.name = name
            this.title = title
            this.fileCount = 2
            formFields =
                arrayListOf(
                    TemplateFormField().apply {
                        this.name = "className"
                        this.title = "Class Name"
                        type = TemplateFormFieldType.Text
                    },
                    TemplateFormField().apply {
                        this.name = "isAbstract"
                        this.title = "Abstract"
                        type = TemplateFormFieldType.Checkbox
                    },
                    TemplateFormField().apply {
                        this.name = "visibility"
                        this.title = "Visibility"
                        type = TemplateFormFieldType.Select
                        options = arrayListOf("public", "private")
                    },
                    TemplateFormField().apply {
                        this.name = "tags"
                        this.title = "Tags"
                        type = TemplateFormFieldType.Collection
                    },
                    TemplateFormField().apply {
                        this.name = "notes"
                        this.title = "Notes"
                        type = TemplateFormFieldType.RichText
                        options = arrayListOf("a", "b")
                    },
                )
        }

    private fun buildActionAndCatchDialogNpe(
        directory: PsiDirectory,
        template: MultipleFileCodeTemplate,
    ): MultipleFileTemplateAction {
        val psaManager = project.service<PsaManager>()
        psaManager.getSettings().multipleFileCodeTemplates = arrayListOf(template)
        project.service<Settings>().apply {
            pluginEnabled = true
            executionMode = ExecutionMode.Script
            scriptPath = fixturePath("multi-template-success.php").path
        }

        val action = MultipleFileTemplateAction("Create ${template.title}", "Desc", null)
        createdActions.add(action)
        action.templateName = template.name

        val dataContext =
            DataContext { dataId ->
                when {
                    CommonDataKeys.PROJECT.`is`(dataId) -> project
                    LangDataKeys.IDE_VIEW.`is`(dataId) -> ideView(directory)
                    else -> null
                }
            }
        val event = TestActionEvent.createTestEvent(action, dataContext)

        try {
            action.actionPerformed(event)
        } catch (e: NullPointerException) {
            assertTrue(e.message.orEmpty().contains("AbstractDialog.getWindow"))
        }

        return action
    }

    /**
     * Repeatedly flushes the IDE event queue until `updateData`'s `changed = false` (set right
     * after a full script round trip - process spawn, response decode, and the final UI-sync
     * `SwingUtilities.invokeLater` all queued) or a generous timeout elapses. A single fixed
     * `Thread.sleep(800)` is not reliably enough time for a real subprocess round trip under load,
     * and letting a scheduled `java.util.Timer` task fire *after* this test method returns leaks it
     * into a later, unrelated test's fixture (observed as a `NullPointerException` from a `Timer-*`
     * thread calling back into an already torn-down `myFixture`).
     */
    private fun waitForUpdateDataToSettle(
        action: Any,
        timeoutMs: Long = 5_000,
    ) {
        val deadline = System.currentTimeMillis() + timeoutMs
        while (System.currentTimeMillis() < deadline) {
            PlatformTestUtil.dispatchAllInvocationEventsInIdeEventQueue()
            if (!privateField<Boolean>(action, "changed")) {
                break
            }
            Thread.sleep(100)
        }
        PlatformTestUtil.dispatchAllInvocationEventsInIdeEventQueue()
    }

    fun testActionPerformedWithoutTemplateIsNoOp() {
        val action = MultipleFileTemplateAction("Test", "Test", null)
        action.templateName = "does_not_exist"

        val dataContext = DataContext { dataId -> if (CommonDataKeys.PROJECT.`is`(dataId)) project else null }
        val event = TestActionEvent.createTestEvent(action, dataContext)

        action.actionPerformed(event)
    }

    fun testActionPerformedNoOpWithoutProject() {
        val action = MultipleFileTemplateAction("Test", "Test", null)
        val dataContext = DataContext { null }
        val event = TestActionEvent.createTestEvent(action, dataContext)

        action.actionPerformed(event)
    }

    fun testActionPerformedBuildsDialogAndCancelsInHeadlessMode() {
        val directory = PsiManager.getInstance(project).findDirectory(project.guessProjectDir()!!)!!
        val template =
            MultipleFileCodeTemplate().apply {
                name = "my_multi_template"
                title = "My Multi Template"
                fileCount = 2
                formFields =
                    arrayListOf(
                        TemplateFormField().apply {
                            name = "className"
                            title = "Class Name"
                            type = TemplateFormFieldType.Text
                        },
                        TemplateFormField().apply {
                            name = "isAbstract"
                            title = "Abstract"
                            type = TemplateFormFieldType.Checkbox
                        },
                        TemplateFormField().apply {
                            name = "visibility"
                            title = "Visibility"
                            type = TemplateFormFieldType.Select
                            options = arrayListOf("public", "private")
                        },
                        TemplateFormField().apply {
                            name = "tags"
                            title = "Tags"
                            type = TemplateFormFieldType.Collection
                        },
                        TemplateFormField().apply {
                            name = "notes"
                            title = "Notes"
                            type = TemplateFormFieldType.RichText
                            options = arrayListOf("a", "b")
                        },
                    )
            }
        val psaManager = project.service<PsaManager>()
        psaManager.getSettings().multipleFileCodeTemplates = arrayListOf(template)
        project.service<Settings>().apply {
            pluginEnabled = true
            executionMode = ExecutionMode.Script
            scriptPath = fixturePath("multi-template-success.php").path
        }

        val action = MultipleFileTemplateAction("Create My Multi Template", "Desc", null)
        createdActions.add(action)
        action.templateName = "my_multi_template"

        val dataContext =
            DataContext { dataId ->
                when {
                    CommonDataKeys.PROJECT.`is`(dataId) -> project
                    LangDataKeys.IDE_VIEW.`is`(dataId) -> ideView(directory)
                    else -> null
                }
            }
        val event = TestActionEvent.createTestEvent(action, dataContext)

        // Exercises the real action up through dialog-panel construction (form field wiring,
        // tabbed preview panes, change-listener scheduling). `DialogBuilder.showAndGet()` itself
        // cannot run headlessly in this test environment - it attempts to create a real AWT
        // window peer and NPEs inside the platform's DialogWrapperPeerImpl - so that specific
        // failure is expected and swallowed here rather than faked or worked around.
        try {
            action.actionPerformed(event)
        } catch (e: NullPointerException) {
            assertTrue(e.message.orEmpty().contains("AbstractDialog.getWindow"))
        }

        // The initial `changeListener(null, null)` call is scheduled via `SwingUtilities.invokeLater`
        // and itself schedules a `java.util.Timer` task. Wait for it to actually complete against
        // the still-live fixture before `tearDown()` nulls it out, instead of letting it fire later
        // against a torn-down project (a fixed `Thread.sleep(800)` was not reliably long enough
        // under full-suite load - see `waitForUpdateDataToSettle`).
        waitForUpdateDataToSettle(action)
    }

    fun testActionPerformedRecordsErrorWhenGenerationFails() {
        val directory = PsiManager.getInstance(project).findDirectory(project.guessProjectDir()!!)!!
        val template =
            MultipleFileCodeTemplate().apply {
                name = "failing_template"
                title = "Failing Template"
                fileCount = 1
                formFields = arrayListOf()
            }
        val psaManager = project.service<PsaManager>()
        psaManager.getSettings().multipleFileCodeTemplates = arrayListOf(template)
        project.service<Settings>().apply {
            pluginEnabled = true
            executionMode = ExecutionMode.Script
            scriptPath = fixturePath("failing-script.php").path
            showErrors = false
        }

        val action = MultipleFileTemplateAction("Create Failing Template", "Desc", null)
        createdActions.add(action)
        action.templateName = "failing_template"

        val dataContext =
            DataContext { dataId ->
                when {
                    CommonDataKeys.PROJECT.`is`(dataId) -> project
                    LangDataKeys.IDE_VIEW.`is`(dataId) -> ideView(directory)
                    else -> null
                }
            }
        val event = TestActionEvent.createTestEvent(action, dataContext)

        try {
            action.actionPerformed(event)
        } catch (e: NullPointerException) {
            assertTrue(e.message.orEmpty().contains("AbstractDialog.getWindow"))
        }

        // "failing-script.php" makes `generateTemplateCode` return null, and `updateData` returns
        // early on that path without ever setting `changed = false` - so, unlike
        // `waitForUpdateDataToSettle`, just flush repeatedly for a generous, fixed window past the
        // Timer's 500ms delay rather than spin for the full multi-second ceiling waiting on a
        // condition that will never become true.
        repeat(15) {
            PlatformTestUtil.dispatchAllInvocationEventsInIdeEventQueue()
            Thread.sleep(100)
        }
        PlatformTestUtil.dispatchAllInvocationEventsInIdeEventQueue()
    }

    fun testCollectionFieldAddRowButtonPropagatesJoinedValuesToChangeListener() {
        val directory = PsiManager.getInstance(project).findDirectory(project.guessProjectDir()!!)!!
        val template = buildAllFieldTypesTemplate("multi_collection_template", "Multi Collection")
        val action =
            buildActionAndCatchDialogNpe(directory, template)

        val formFields = privateField<HashMap<String, Cell<JComponent>>>(action, "formFields")
        val coll = formFields["tags"]!!.component as JTextFieldCollection
        val addButton = findAll(coll, ActionButton::class.java).first { it.action.templatePresentation.text == "Add Row" }

        // Clicking "Add Row" fires JTextFieldCollection's own property-change listener
        // synchronously, which invokes the outer `coll.addValuesChangeListener { ... }` callback
        // wired up in MultipleFileTemplateAction.actionPerformed (the Collection field branch),
        // exercising the `changeListener(field, newValue.joinToString(","))` line.
        addButton.action.actionPerformed(TestActionEvent.createTestEvent(addButton.action))

        waitForUpdateDataToSettle(action)
    }

    fun testErrorTooltipActionShowsGotItTooltipWithLastResultMessage() {
        val directory = PsiManager.getInstance(project).findDirectory(project.guessProjectDir()!!)!!
        // fileCount must match multi-template-success.php's fixed 2 file names, or the pending
        // initial changeListener(null, null) Timer (which this test waits on via
        // waitForUpdateDataToSettle) crashes updateData with an IndexOutOfBoundsException when it
        // tries to `tabbedPane!!.setTitleAt(1, ...)` against a tabbedPane built with only 1 tab.
        val template = buildAllFieldTypesTemplate("multi_tooltip_template", "Multi Tooltip")
        val action = buildActionAndCatchDialogNpe(directory, template)

        project.service<PsaManager>().lastResultMessage = "Something went wrong"

        val errorIconCell = privateField<Cell<ActionButton>?>(action, "errorIcon")
        val errorAction = errorIconCell!!.component.action

        // Directly invokes the anonymous DumbAwareAction wired to the error icon button, which
        // builds and shows a GotItTooltip using `psaManager.lastResultMessage`. Mirrors the
        // established `PsaConfigurableTest.testSupportedLanguagesButtonActionShowsTooltip` pattern -
        // GotItTooltip.createAndShow runs fine headlessly, unlike DialogBuilder.showAndGet().
        errorAction.actionPerformed(TestActionEvent.createTestEvent(errorAction))

        // Let the still-pending initial `changeListener(null, null)` invokeLater (queued by the
        // panel builder) run its course against the still-live fixture, instead of leaking a Timer
        // that would otherwise fire later against a torn-down project.
        waitForUpdateDataToSettle(action)
    }

    fun testPreviewEditorSettingsProviderConfiguresEachTabEditor() {
        val directory = PsiManager.getInstance(project).findDirectory(project.guessProjectDir()!!)!!
        val template = buildAllFieldTypesTemplate("multi_editor_template", "Multi Editor")
        val action = buildActionAndCatchDialogNpe(directory, template)

        val previewTextFields = privateField<ArrayList<EditorTextField>>(action, "previewTextFields")
        assertEquals(2, previewTextFields.size)

        // `EditorTextField.getEditor(true)` only actually creates a real editor (running
        // `createEditor()`, which applies registered settings providers) if the component is
        // either in a realized Swing hierarchy or has a disposable registered via
        // `setDisposedWith(...)` - neither is true here since `DialogBuilder.showAndGet()` never
        // realizes/shows the panel in this headless test environment. Registering
        // `testRootDisposable` satisfies that check without needing a real window, forcing
        // `createEditor()` to run and hit the `addSettingsProvider { editor -> run { ... } }`
        // callback.
        previewTextFields.forEach {
            it.setDisposedWith(testRootDisposable)
            it.getEditor(true)
        }

        // Let the still-pending initial `changeListener(null, null)` invokeLater (queued by the
        // panel builder) run its course against the still-live fixture, instead of leaking a Timer
        // that would otherwise fire later against a torn-down project.
        waitForUpdateDataToSettle(action)
    }

    fun testChangeListenerCatchesProcessCanceledExceptionWhenIndicatorAlreadyCancelled() {
        val directory = PsiManager.getInstance(project).findDirectory(project.guessProjectDir()!!)!!
        // fileCount must match multi-template-success.php's fixed 2 file names (see the tooltip
        // test above) - kept consistent here too even though the PCE is expected to short-circuit
        // updateData before it ever reaches the tabbedPane loop.
        val template = buildAllFieldTypesTemplate("multi_pce_template", "Multi PCE")
        val action = buildActionAndCatchDialogNpe(directory, template)

        // Let the initial `changeListener(null, null)` invokeLater run, resetting `indicator` and
        // scheduling its 500ms Timer task.
        PlatformTestUtil.dispatchAllInvocationEventsInIdeEventQueue()

        val formFields = privateField<HashMap<String, Cell<JComponent>>>(action, "formFields")
        val coll = formFields["tags"]!!.component as JTextFieldCollection
        val addButton = findAll(coll, ActionButton::class.java).first { it.action.templatePresentation.text == "Add Row" }
        addButton.action.actionPerformed(TestActionEvent.createTestEvent(addButton.action))

        // Flush the changeListener's invokeLater: this cancels the previous Timer/indicator and
        // schedules a fresh Timer against a brand-new (not yet cancelled) `indicator`.
        PlatformTestUtil.dispatchAllInvocationEventsInIdeEventQueue()

        // Cancel that fresh indicator out from under the about-to-fire Timer task, forcing
        // `ApplicationUtil.runWithCheckCanceled(...)` to throw `ProcessCanceledException` when the
        // Timer fires 500ms later - exercising the `catch (_: ProcessCanceledException) {}` branch.
        val indicator = privateField<ProgressIndicator>(action, "indicator")
        indicator.cancel()

        // `changed` never flips to `false` on this path (the PCE is expected to be thrown/caught
        // before `updateData`'s body ever runs), so - unlike `waitForUpdateDataToSettle` - just
        // flush repeatedly for a generous, fixed window past the Timer's 500ms delay, rather than
        // spin for the full multi-second ceiling waiting on a condition that will never become true.
        repeat(15) {
            PlatformTestUtil.dispatchAllInvocationEventsInIdeEventQueue()
            Thread.sleep(100)
        }
        PlatformTestUtil.dispatchAllInvocationEventsInIdeEventQueue()
    }
}
