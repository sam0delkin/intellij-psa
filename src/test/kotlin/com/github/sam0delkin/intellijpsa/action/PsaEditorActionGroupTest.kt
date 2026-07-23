package com.github.sam0delkin.intellijpsa.action

import com.github.sam0delkin.intellijpsa.action.psa.GeneratePatternModelAction
import com.github.sam0delkin.intellijpsa.action.psa.InspectPsiElementModelAction
import com.github.sam0delkin.intellijpsa.model.EditorActionSource
import com.github.sam0delkin.intellijpsa.model.EditorActionTarget
import com.github.sam0delkin.intellijpsa.services.PsaManager
import com.github.sam0delkin.intellijpsa.settings.ExecutionMode
import com.github.sam0delkin.intellijpsa.settings.PersistedEditorAction
import com.github.sam0delkin.intellijpsa.settings.Settings
import com.intellij.ide.IdeView
import com.intellij.openapi.actionSystem.ActionGroup
import com.intellij.openapi.actionSystem.CommonDataKeys
import com.intellij.openapi.actionSystem.LangDataKeys
import com.intellij.openapi.actionSystem.Separator
import com.intellij.openapi.components.service
import com.intellij.openapi.ide.CopyPasteManager
import com.intellij.openapi.project.guessProjectDir
import com.intellij.psi.PsiDirectory
import com.intellij.psi.PsiManager
import com.intellij.testFramework.TestActionEvent
import com.intellij.testFramework.fixtures.BasePlatformTestCase
import java.awt.datatransfer.DataFlavor
import java.awt.datatransfer.StringSelection
import java.io.File

class PsaEditorActionGroupTest : BasePlatformTestCase() {
    private fun ideView(directory: PsiDirectory): IdeView =
        object : IdeView {
            override fun getDirectories(): Array<PsiDirectory> = arrayOf(directory)

            override fun getOrChooseDirectory(): PsiDirectory = directory
        }

    private fun fixturePath(): File {
        val resource = javaClass.classLoader.getResource("server/counting-script.php")!!
        val file = File(resource.toURI())
        file.setExecutable(true)

        return file
    }

    fun testGetChildrenWithNullEventReturnsDefaultActions() {
        val group = PsaEditorActionGroup()

        val children = group.getChildren(null)

        assertEquals(2, children.size)
        assertTrue(children.any { it is GeneratePatternModelAction })
        assertTrue(children.any { it is InspectPsiElementModelAction })
    }

    fun testGetChildrenReturnsDefaultsWhenNoEditorActionsConfigured() {
        val group = PsaEditorActionGroup()
        val directory = PsiManager.getInstance(project).findDirectory(project.guessProjectDir()!!)!!
        val event =
            TestActionEvent.createTestEvent(
                group,
                { dataId ->
                    when {
                        CommonDataKeys.PROJECT.`is`(dataId) -> project
                        LangDataKeys.IDE_VIEW.`is`(dataId) -> ideView(directory)
                        else -> null
                    }
                },
            )

        val children = group.getChildren(event)

        assertEquals(2, children.size)
    }

    fun testGetChildrenReturnsEmptyArrayWithoutIdeView() {
        val group = PsaEditorActionGroup()
        val event = TestActionEvent.createTestEvent(group, { dataId -> if (CommonDataKeys.PROJECT.`is`(dataId)) project else null })

        val children = group.getChildren(event)

        assertEquals(0, children.size)
    }

    fun testGetChildrenReturnsEmptyArrayWithoutDirectories() {
        val group = PsaEditorActionGroup()
        val emptyView =
            object : IdeView {
                override fun getDirectories(): Array<PsiDirectory> = arrayOf()

                override fun getOrChooseDirectory(): PsiDirectory? = null
            }
        val event =
            TestActionEvent.createTestEvent(
                group,
                { dataId ->
                    when {
                        CommonDataKeys.PROJECT.`is`(dataId) -> project
                        LangDataKeys.IDE_VIEW.`is`(dataId) -> emptyView
                        else -> null
                    }
                },
            )

        val children = group.getChildren(event)

        assertEquals(0, children.size)
    }

    fun testGetChildrenAddsCustomActionsGroupedAndUngrouped() {
        val group = PsaEditorActionGroup()
        val directory = PsiManager.getInstance(project).findDirectory(project.guessProjectDir()!!)!!
        val psaManager = project.service<PsaManager>()
        psaManager.getSettings().editorActions =
            arrayListOf(
                PersistedEditorAction().apply {
                    name = "ungrouped_action"
                    title = "Ungrouped Action"
                },
                PersistedEditorAction().apply {
                    name = "grouped_action"
                    title = "Grouped Action"
                    groupName = "My Group"
                },
            )
        val event =
            TestActionEvent.createTestEvent(
                group,
                { dataId ->
                    when {
                        CommonDataKeys.PROJECT.`is`(dataId) -> project
                        LangDataKeys.IDE_VIEW.`is`(dataId) -> ideView(directory)
                        else -> null
                    }
                },
            )

        val children = group.getChildren(event)

        assertTrue(children.any { it is Separator })
        assertTrue(children.any { it.templatePresentation.text == "Ungrouped Action" })
        assertTrue(children.any { it is ActionGroup && it.templatePresentation.text == "My Group" })
    }

    fun testAddCustomActionsSkipsActionWithMismatchedPathRegex() {
        val group = PsaEditorActionGroup()
        val actions = HashMap<String?, ArrayList<com.intellij.openapi.actionSystem.AnAction>>()
        val editorActions =
            arrayListOf(
                PersistedEditorAction().apply {
                    name = "a"
                    title = "A"
                    pathRegex = "^/nomatch/"
                },
            )

        group.addCustomActions(editorActions, "/src/", project.service<PsaManager>(), Settings(), actions, false)

        assertTrue(actions[null].orEmpty().isEmpty())
    }

    fun testAddCustomActionsSkipsContextActionMismatch() {
        val group = PsaEditorActionGroup()
        val actions = HashMap<String?, ArrayList<com.intellij.openapi.actionSystem.AnAction>>()
        val editorActions =
            arrayListOf(
                PersistedEditorAction().apply {
                    name = "a"
                    title = "A"
                    contextAction = true
                },
            )

        group.addCustomActions(editorActions, "/src/", project.service<PsaManager>(), Settings(), actions, false)

        assertTrue(actions[null].orEmpty().isEmpty())
    }

    fun testAddCustomActionsSkipsEmptyTitle() {
        val group = PsaEditorActionGroup()
        val actions = HashMap<String?, ArrayList<com.intellij.openapi.actionSystem.AnAction>>()
        val editorActions =
            arrayListOf(
                PersistedEditorAction().apply {
                    name = "a"
                    title = ""
                },
            )

        group.addCustomActions(editorActions, "/src/", project.service<PsaManager>(), Settings(), actions, false)

        assertTrue(actions[null].orEmpty().isEmpty())
    }

    fun testCustomActionPerformedEditorSourceToClipboardTarget() {
        myFixture.configureByText("test.php", "<?php 'selected_text';")
        myFixture.editor.selectionModel.setSelection(0, myFixture.file.text.length)

        val group = PsaEditorActionGroup()
        val psaManager = project.service<PsaManager>()
        project.service<Settings>().apply {
            pluginEnabled = true
            executionMode = ExecutionMode.Script
            scriptPath = fixturePath().path
        }
        val actions = HashMap<String?, ArrayList<com.intellij.openapi.actionSystem.AnAction>>()
        val editorActions =
            arrayListOf(
                PersistedEditorAction().apply {
                    name = "copy_action"
                    title = "Copy Action"
                    source = EditorActionSource.Editor
                    target = EditorActionTarget.Clipboard
                },
            )
        group.addCustomActions(editorActions, "/src/", psaManager, project.service<Settings>(), actions, false)
        val action = actions[null]!!.first()

        val event = TestActionEvent.createTestEvent(action, { dataId -> if (CommonDataKeys.PROJECT.`is`(dataId)) project else null })
        action.actionPerformed(event)

        Thread.sleep(500)
        val clipboardContent = CopyPasteManager.getInstance().getContents(DataFlavor.stringFlavor) as? String
        assertNotNull(clipboardContent)
    }

    fun testCustomActionPerformedClipboardSourceToEditorTarget() {
        myFixture.configureByText("test.php", "<?php 'selected_text';")
        myFixture.editor.selectionModel.setSelection(0, myFixture.file.text.length)
        CopyPasteManager.getInstance().setContents(StringSelection("clipboard_value"))

        val group = PsaEditorActionGroup()
        val psaManager = project.service<PsaManager>()
        project.service<Settings>().apply {
            pluginEnabled = true
            executionMode = ExecutionMode.Script
            scriptPath = fixturePath().path
        }
        val actions = HashMap<String?, ArrayList<com.intellij.openapi.actionSystem.AnAction>>()
        val editorActions =
            arrayListOf(
                PersistedEditorAction().apply {
                    name = "replace_action"
                    title = "Replace Action"
                    source = EditorActionSource.Clipboard
                    target = EditorActionTarget.Editor
                },
            )
        group.addCustomActions(editorActions, "/src/", psaManager, project.service<Settings>(), actions, false)
        val action = actions[null]!!.first()

        val event = TestActionEvent.createTestEvent(action, { dataId -> if (CommonDataKeys.PROJECT.`is`(dataId)) project else null })
        action.actionPerformed(event)

        Thread.sleep(500)
    }
}
