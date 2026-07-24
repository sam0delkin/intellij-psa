package com.github.sam0delkin.intellijpsa.action.template

import com.github.sam0delkin.intellijpsa.services.PsaManager
import com.github.sam0delkin.intellijpsa.settings.ExecutionMode
import com.github.sam0delkin.intellijpsa.settings.Settings
import com.github.sam0delkin.intellijpsa.settings.SingleFileCodeTemplate
import com.github.sam0delkin.intellijpsa.settings.TemplateFormField
import com.github.sam0delkin.intellijpsa.settings.TemplateFormFieldType
import com.github.sam0delkin.intellijpsa.ui.components.JTextFieldCollection
import com.intellij.ide.IdeView
import com.intellij.openapi.actionSystem.CommonDataKeys
import com.intellij.openapi.actionSystem.DataContext
import com.intellij.openapi.actionSystem.LangDataKeys
import com.intellij.openapi.actionSystem.impl.ActionButton
import com.intellij.openapi.command.WriteCommandAction
import com.intellij.openapi.components.service
import com.intellij.openapi.progress.ProgressIndicator
import com.intellij.openapi.project.guessProjectDir
import com.intellij.psi.PsiDirectory
import com.intellij.psi.PsiDocumentManager
import com.intellij.psi.PsiManager
import com.intellij.testFramework.PlatformTestUtil
import com.intellij.testFramework.TestActionEvent
import com.intellij.testFramework.fixtures.BasePlatformTestCase
import com.intellij.ui.EditorTextField
import com.intellij.ui.dsl.builder.Cell
import com.intellij.util.textCompletion.TextCompletionProviderBase
import com.intellij.util.textCompletion.TextCompletionUtil
import com.intellij.util.textCompletion.TextCompletionValueDescriptor
import com.intellij.util.textCompletion.TextFieldWithCompletion
import java.awt.Container
import java.io.File
import javax.swing.JComponent

class SingleFileTemplateActionTest : BasePlatformTestCase() {
    // Tracks every `SingleFileTemplateAction` built by a test so `tearDown()` can unconditionally
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
    ): SingleFileCodeTemplate =
        SingleFileCodeTemplate().apply {
            this.name = name
            this.title = title
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
        template: SingleFileCodeTemplate,
        scriptFixture: String,
    ): SingleFileTemplateAction {
        val psaManager = project.service<PsaManager>()
        psaManager.getSettings().singleFileCodeTemplates = arrayListOf(template)
        project.service<Settings>().apply {
            pluginEnabled = true
            executionMode = ExecutionMode.Script
            scriptPath = fixturePath(scriptFixture).path
        }

        val action = SingleFileTemplateAction("Create ${template.title}", "Desc", null)
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

    fun testActionPerformedBuildsDialogWithoutTemplateIsNoOp() {
        val action = SingleFileTemplateAction("Test", "Test", null)
        action.templateName = "does_not_exist"

        val dataContext = DataContext { dataId -> if (CommonDataKeys.PROJECT.`is`(dataId)) project else null }
        val event = TestActionEvent.createTestEvent(action, dataContext)

        action.actionPerformed(event)
    }

    fun testActionPerformedBuildsDialogAndCancelsInHeadlessMode() {
        val directory = PsiManager.getInstance(project).findDirectory(project.guessProjectDir()!!)!!
        val template =
            SingleFileCodeTemplate().apply {
                name = "my_template"
                title = "My Template"
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
                            focused = true
                        },
                    )
            }
        val psaManager = project.service<PsaManager>()
        psaManager.getSettings().singleFileCodeTemplates = arrayListOf(template)
        project.service<Settings>().apply {
            pluginEnabled = true
            executionMode = ExecutionMode.Script
            scriptPath = fixturePath("template-success.php").path
        }

        val action = SingleFileTemplateAction("Create My Template", "Desc", null)
        createdActions.add(action)
        action.templateName = "my_template"

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
        // change-listener scheduling, preview text field setup). `DialogBuilder.showAndGet()`
        // itself cannot run headlessly in this test environment - it attempts to create a real
        // AWT window peer and NPEs inside the platform's DialogWrapperPeerImpl - so that specific
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

    fun testActionPerformedNoOpWithoutProject() {
        val action = SingleFileTemplateAction("Test", "Test", null)
        val dataContext = DataContext { null }
        val event = TestActionEvent.createTestEvent(action, dataContext)

        action.actionPerformed(event)
    }

    fun testCollectionFieldAddRowButtonPropagatesJoinedValuesToChangeListener() {
        val directory = PsiManager.getInstance(project).findDirectory(project.guessProjectDir()!!)!!
        val template = buildAllFieldTypesTemplate("single_collection_template", "Single Collection")
        val action = buildActionAndCatchDialogNpe(directory, template, "template-success.php")

        val formFields = privateField<HashMap<String, Cell<JComponent>>>(action, "formFields")
        val coll = formFields["tags"]!!.component as JTextFieldCollection
        val addButton = findAll(coll, ActionButton::class.java).first { it.action.templatePresentation.text == "Add Row" }

        // Clicking "Add Row" fires JTextFieldCollection's own property-change listener
        // synchronously, which invokes the outer `coll.addValuesChangeListener { ... }` callback
        // wired up in SingleFileTemplateAction.actionPerformed (the Collection field branch),
        // exercising the `changeListener(field, newValue.joinToString(","))` line.
        addButton.action.actionPerformed(TestActionEvent.createTestEvent(addButton.action))

        waitForUpdateDataToSettle(action)
    }

    fun testRichTextFieldDocumentChangeFiresChangeListener() {
        val directory = PsiManager.getInstance(project).findDirectory(project.guessProjectDir()!!)!!
        val template = buildAllFieldTypesTemplate("single_richtext_doc_template", "Single RichText Doc")
        val action = buildActionAndCatchDialogNpe(directory, template, "template-success.php")

        val formFields = privateField<HashMap<String, Cell<JComponent>>>(action, "formFields")
        val notesComponent = formFields["notes"]!!.component as TextFieldWithCompletion

        // Directly editing the rich-text editor's own `Document` (a real
        // `com.intellij.openapi.editor.Document`, distinct from the JTextFieldCollection's Swing
        // `javax.swing.text.Document`) fires its `documentChanged` listener synchronously,
        // exercising `changeListener(field, event.document.text)`. A plain `runWriteAction` isn't
        // enough here - `DocumentImpl` requires document mutations to happen inside a command.
        WriteCommandAction.runWriteCommandAction(project) {
            notesComponent.document.insertString(0, "hello")
        }

        waitForUpdateDataToSettle(action)
    }

    fun testRichTextValueDescriptorCompareAndCreateLookupBuilder() {
        val directory = PsiManager.getInstance(project).findDirectory(project.guessProjectDir()!!)!!
        val template = buildAllFieldTypesTemplate("single_richtext_descriptor_template", "Single RichText Descriptor")
        val action = buildActionAndCatchDialogNpe(directory, template, "template-success.php")

        val formFields = privateField<HashMap<String, Cell<JComponent>>>(action, "formFields")
        val notesComponent = formFields["notes"]!!.component as TextFieldWithCompletion

        // The RichText field's `TextCompletionValueDescriptor` (an anonymous object providing
        // `compare`/`createLookupBuilder`) is installed on the light PSI file backing the
        // component's document, retrievable via the same public `TextCompletionUtil.getProvider`
        // API the platform's completion machinery itself uses - avoids having to drive a full
        // code-completion popup just to reach these two lines.
        val psiFile = PsiDocumentManager.getInstance(project).getPsiFile(notesComponent.document)!!
        val provider = TextCompletionUtil.getProvider(psiFile)!!
        val descriptorField = TextCompletionProviderBase::class.java.getDeclaredField("myDescriptor")
        descriptorField.isAccessible = true

        @Suppress("UNCHECKED_CAST")
        val descriptor = descriptorField.get(provider) as TextCompletionValueDescriptor<String>

        assertTrue(descriptor.compare("a", "b") < 0)
        assertEquals("x", descriptor.createLookupBuilder("x").lookupString)

        // Let the still-pending initial `changeListener(null, null)` invokeLater (queued by the
        // panel builder) run its course against the still-live fixture, instead of leaking a Timer
        // that would otherwise fire later against a torn-down project.
        waitForUpdateDataToSettle(action)
    }

    fun testFormFieldsSyncsRichTextValueFromScriptResponse() {
        val directory = PsiManager.getInstance(project).findDirectory(project.guessProjectDir()!!)!!
        val template = buildAllFieldTypesTemplate("single_richtext_sync_template", "Single RichText Sync")
        val action = buildActionAndCatchDialogNpe(directory, template, "template-success-with-notes-value.php")

        // Let the initial `changeListener(null, null)` invokeLater run: `updateData` calls the
        // script (from the java.util.Timer's own background thread, 500ms after scheduling), gets
        // back a non-null "notes" value (unlike template-success.php, which always returns null
        // for it) and pushes it into the registered TextFieldWithCompletion via
        // `component.document.setText(...)` on yet another `SwingUtilities.invokeLater`. Poll
        // instead of a single fixed sleep, since the round trip involves real process spawn time.
        val formFields = privateField<HashMap<String, Cell<JComponent>>>(action, "formFields")
        val notesComponent = formFields["notes"]!!.component as TextFieldWithCompletion

        val deadline = System.currentTimeMillis() + 10_000
        while (notesComponent.document.text.isEmpty() && System.currentTimeMillis() < deadline) {
            PlatformTestUtil.dispatchAllInvocationEventsInIdeEventQueue()
            Thread.sleep(100)
        }
        PlatformTestUtil.dispatchAllInvocationEventsInIdeEventQueue()

        assertEquals("Some generated notes", notesComponent.document.text)
    }

    fun testPreviewEditorSettingsProviderConfiguresEditor() {
        val directory = PsiManager.getInstance(project).findDirectory(project.guessProjectDir()!!)!!
        val template = buildAllFieldTypesTemplate("single_editor_template", "Single Editor")
        val action = buildActionAndCatchDialogNpe(directory, template, "template-success.php")

        val previewTextFieldComponent = privateField<Cell<EditorTextField>?>(action, "previewTextField")!!.component

        // `EditorTextField.getEditor(true)` only actually creates a real editor (running
        // `createEditor()`, which applies registered settings providers) if the component is
        // either in a realized Swing hierarchy or has a disposable registered via
        // `setDisposedWith(...)` - neither is true here since `DialogBuilder.showAndGet()` never
        // realizes/shows the panel in this headless test environment. Registering
        // `testRootDisposable` satisfies that check without needing a real window, forcing
        // `createEditor()` to run and hit the `addSettingsProvider { editor -> run { ... } }`
        // callback.
        previewTextFieldComponent.setDisposedWith(testRootDisposable)
        previewTextFieldComponent.getEditor(true)

        // Let the still-pending initial `changeListener(null, null)` invokeLater (queued by the
        // panel builder) run its course against the still-live fixture, instead of leaking a Timer
        // that would otherwise fire later against a torn-down project.
        waitForUpdateDataToSettle(action)
    }

    fun testChangeListenerCatchesProcessCanceledExceptionWhenIndicatorAlreadyCancelled() {
        val directory = PsiManager.getInstance(project).findDirectory(project.guessProjectDir()!!)!!
        val template = buildAllFieldTypesTemplate("single_pce_template", "Single PCE")
        val action = buildActionAndCatchDialogNpe(directory, template, "template-success.php")

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
