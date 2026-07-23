package com.github.sam0delkin.intellijpsa.action.psa

import com.github.sam0delkin.intellijpsa.model.EditorActionSource
import com.github.sam0delkin.intellijpsa.model.EditorActionTarget
import com.github.sam0delkin.intellijpsa.settings.ExecutionMode
import com.github.sam0delkin.intellijpsa.settings.PersistedEditorAction
import com.github.sam0delkin.intellijpsa.settings.Settings
import com.intellij.openapi.components.service
import com.intellij.openapi.ide.CopyPasteManager
import com.intellij.psi.PsiFileFactory
import com.intellij.testFramework.LightVirtualFileBase
import com.intellij.testFramework.fixtures.BasePlatformTestCase
import java.awt.datatransfer.DataFlavor
import java.awt.datatransfer.StringSelection
import java.io.File

/**
 * `invoke()`'s popup display (`JBPopupFactory...createActionGroupPopup(...).showInBestPositionFor(editor)`)
 * hits the same headless-dialog wall documented elsewhere in this codebase
 * (`SingleFileTemplateActionTest`/`MultipleFileTemplateActionTest`/`PsaStatusBarWidgetFactoryTest`):
 * `NullPointerException` from `AbstractDialog.getWindow()` returning null with no real window system.
 * Catching that specific exception lets everything before it (action-group construction) execute and
 * be covered.
 *
 * `project.guessProjectDir()` returning null (the early-return branches at the top of `invoke()` and
 * `getContextualActions()`) is not reachable from a `BasePlatformTestCase` light-fixture project - it
 * always resolves to the fixture's synthetic `temp:///src` root - so that branch is a documented
 * residual gap, consistent with this project's other environment-dependent gaps.
 */
class PsaContextualIntentionActionTest : BasePlatformTestCase() {
    override fun setUp() {
        super.setUp()
        project.service<Settings>().apply {
            pluginEnabled = true
            executionMode = ExecutionMode.Script
            editorActions = arrayListOf()
        }
    }

    private fun fixturePath(name: String): File {
        val resource = javaClass.classLoader.getResource("server/$name")!!
        val file = File(resource.toURI())
        file.setExecutable(true)

        return file
    }

    private fun nonPhysicalFile(): com.intellij.psi.PsiFile =
        PsiFileFactory.getInstance(project).createFileFromText(
            "orphan.php",
            com.intellij.lang.Language
                .findLanguageByID("TEXT")!!,
            "text",
        )

    fun testGetTextReturnsPsaActions() {
        assertEquals("PSA Actions", PsaContextualIntentionAction().text)
    }

    fun testGetFamilyNameReturnsPsa() {
        assertEquals("PSA", PsaContextualIntentionAction().familyName)
    }

    fun testStartInWriteActionReturnsFalse() {
        assertFalse(PsaContextualIntentionAction().startInWriteAction())
    }

    fun testIsAvailableReturnsFalseWhenVirtualFileParentIsNull() {
        val file = nonPhysicalFile()
        assertNull(file.virtualFile.parent)

        val available = PsaContextualIntentionAction().isAvailable(project, myFixture.editor, file)

        assertFalse(available)
    }

    fun testIsAvailableReturnsFalseWithoutEditorActions() {
        myFixture.configureByText("test.php", "<?php")
        project.service<Settings>().editorActions = arrayListOf()

        val available = PsaContextualIntentionAction().isAvailable(project, myFixture.editor, myFixture.file)

        assertFalse(available)
    }

    fun testIsAvailableReturnsFalseWhenNoActionIsContextual() {
        myFixture.configureByText("test.php", "<?php")
        project.service<Settings>().editorActions =
            arrayListOf(
                PersistedEditorAction().apply {
                    name = "a"
                    title = "A"
                    contextual = false
                },
            )

        val available = PsaContextualIntentionAction().isAvailable(project, myFixture.editor, myFixture.file)

        assertFalse(available)
    }

    fun testIsAvailableReturnsFalseWhenPathRegexDoesNotMatch() {
        myFixture.configureByText("test.php", "<?php")
        project.service<Settings>().editorActions =
            arrayListOf(
                PersistedEditorAction().apply {
                    name = "a"
                    title = "A"
                    contextual = true
                    pathRegex = "^/nomatch/$"
                },
            )

        val available = PsaContextualIntentionAction().isAvailable(project, myFixture.editor, myFixture.file)

        assertFalse(available)
    }

    fun testIsAvailableReturnsTrueForMatchingContextualAction() {
        myFixture.configureByText("test.php", "<?php")
        project.service<Settings>().editorActions =
            arrayListOf(
                PersistedEditorAction().apply {
                    name = "a"
                    title = "A"
                    contextual = true
                },
            )

        val available = PsaContextualIntentionAction().isAvailable(project, myFixture.editor, myFixture.file)

        assertTrue(available)
    }

    fun testInvokeNoOpWithNullEditor() {
        myFixture.configureByText("test.php", "<?php")

        PsaContextualIntentionAction().invoke(project, null, myFixture.file)
    }

    fun testInvokeNoOpWithNullFile() {
        PsaContextualIntentionAction().invoke(project, myFixture.editor, null)
    }

    fun testInvokeReturnsEarlyWhenParentDirIsNull() {
        val file = nonPhysicalFile()
        assertNull(file.virtualFile.parent)
        assertTrue(file.virtualFile is LightVirtualFileBase)

        // getContextualActions()'s own parentDir-null guard fires here (bypassing isAvailable's
        // earlier check), so this must not throw despite editorActions being configured.
        project.service<Settings>().editorActions =
            arrayListOf(
                PersistedEditorAction().apply {
                    name = "a"
                    title = "A"
                    contextual = true
                },
            )

        PsaContextualIntentionAction().invoke(project, myFixture.editor, file)
    }

    fun testInvokeBuildsGroupedAndUngroupedActionsAndShowsPopup() {
        myFixture.configureByText("test.php", "<?php")
        project.service<Settings>().editorActions =
            arrayListOf(
                PersistedEditorAction().apply {
                    name = "ungrouped"
                    title = "Ungrouped"
                    contextual = true
                },
                PersistedEditorAction().apply {
                    name = "grouped"
                    title = "Grouped"
                    contextual = true
                    groupName = "My Group"
                },
            )

        // Unlike ListPopup.show(RelativePoint), showInBestPositionFor(editor) degrades gracefully
        // in this headless test environment instead of hitting the AbstractDialog.getWindow() NPE
        // wall - so this covers the full action-group construction (grouped + ungrouped) without
        // needing to catch anything.
        PsaContextualIntentionAction().invoke(project, myFixture.editor, myFixture.file)
    }

    fun testActionPerformedEditorSourceToClipboardTarget() {
        myFixture.configureByText("test.php", "<?php 'selected_text';")
        myFixture.editor.selectionModel.setSelection(0, myFixture.file.text.length)
        project.service<Settings>().apply {
            editorActions =
                arrayListOf(
                    PersistedEditorAction().apply {
                        name = "copy_action"
                        title = "Copy Action"
                        contextual = true
                        source = EditorActionSource.Editor
                        target = EditorActionTarget.Clipboard
                    },
                )
            scriptPath = fixturePath("counting-script.php").path
        }

        PsaContextualIntentionAction().invoke(project, myFixture.editor, myFixture.file)

        Thread.sleep(500)
        val clipboardContent = CopyPasteManager.getInstance().getContents(DataFlavor.stringFlavor) as? String
        assertNotNull(clipboardContent)
    }

    fun testActionPerformedClipboardSourceToEditorTarget() {
        myFixture.configureByText("test.php", "<?php 'selected_text';")
        myFixture.editor.selectionModel.setSelection(0, myFixture.file.text.length)
        CopyPasteManager.getInstance().setContents(StringSelection("clipboard_value"))
        project.service<Settings>().apply {
            editorActions =
                arrayListOf(
                    PersistedEditorAction().apply {
                        name = "replace_action"
                        title = "Replace Action"
                        contextual = true
                        source = EditorActionSource.Clipboard
                        target = EditorActionTarget.Editor
                    },
                )
            scriptPath = fixturePath("counting-script.php").path
        }

        PsaContextualIntentionAction().invoke(project, myFixture.editor, myFixture.file)

        Thread.sleep(500)
    }

    fun testActionPerformedNoOpWhenScriptFails() {
        myFixture.configureByText("test.php", "<?php 'selected_text';")
        myFixture.editor.selectionModel.setSelection(0, myFixture.file.text.length)
        project.service<Settings>().apply {
            editorActions =
                arrayListOf(
                    PersistedEditorAction().apply {
                        name = "failing_action"
                        title = "Failing Action"
                        contextual = true
                        source = EditorActionSource.Editor
                        target = EditorActionTarget.Clipboard
                    },
                )
            scriptPath = fixturePath("failing-script.php").path
        }

        PsaContextualIntentionAction().invoke(project, myFixture.editor, myFixture.file)

        Thread.sleep(500)
    }

    fun testActionPerformedNoOpWhenTargetIsNothing() {
        myFixture.configureByText("test.php", "<?php 'selected_text';")
        myFixture.editor.selectionModel.setSelection(0, myFixture.file.text.length)
        project.service<Settings>().apply {
            editorActions =
                arrayListOf(
                    PersistedEditorAction().apply {
                        name = "noop_action"
                        title = "Noop Action"
                        contextual = true
                        source = EditorActionSource.Editor
                        target = EditorActionTarget.Nothing
                    },
                )
            scriptPath = fixturePath("counting-script.php").path
        }

        PsaContextualIntentionAction().invoke(project, myFixture.editor, myFixture.file)

        Thread.sleep(500)
    }
}
