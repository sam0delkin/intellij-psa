package com.github.sam0delkin.intellijpsa.action.template

import com.github.sam0delkin.intellijpsa.services.PsaManager
import com.github.sam0delkin.intellijpsa.settings.MultipleFileCodeTemplate
import com.github.sam0delkin.intellijpsa.settings.Settings
import com.github.sam0delkin.intellijpsa.settings.SingleFileCodeTemplate
import com.intellij.ide.IdeView
import com.intellij.openapi.actionSystem.CommonDataKeys
import com.intellij.openapi.actionSystem.LangDataKeys
import com.intellij.openapi.components.service
import com.intellij.openapi.project.guessProjectDir
import com.intellij.psi.PsiDirectory
import com.intellij.psi.PsiManager
import com.intellij.testFramework.TestActionEvent
import com.intellij.testFramework.fixtures.BasePlatformTestCase

class PsaFileTemplateActionGroupTest : BasePlatformTestCase() {
    private fun ideView(directory: PsiDirectory): IdeView =
        object : IdeView {
            override fun getDirectories(): Array<PsiDirectory> = arrayOf(directory)

            override fun getOrChooseDirectory(): PsiDirectory = directory
        }

    override fun tearDown() {
        try {
            project.service<Settings>().loadState(Settings())
        } finally {
            super.tearDown()
        }
    }

    fun testGetChildrenReturnsEmptyArrayWithNullEvent() {
        val group = PsaFileTemplateActionGroup()

        val children = group.getChildren(null)

        assertEquals(0, children.size)
    }

    fun testGetChildrenReturnsEmptyArrayWhenNoTemplatesConfigured() {
        val group = PsaFileTemplateActionGroup()
        val event =
            TestActionEvent.createTestEvent(
                group,
                { dataId -> if (CommonDataKeys.PROJECT.`is`(dataId)) project else null },
            )

        val children = group.getChildren(event)

        assertEquals(0, children.size)
    }

    fun testGetChildrenReturnsEmptyArrayWhenSingleFileCodeTemplatesIsEmptyList() {
        val group = PsaFileTemplateActionGroup()
        val psaManager = project.service<PsaManager>()
        psaManager.getSettings().singleFileCodeTemplates = arrayListOf()
        val event =
            TestActionEvent.createTestEvent(
                group,
                { dataId -> if (CommonDataKeys.PROJECT.`is`(dataId)) project else null },
            )

        val children = group.getChildren(event)

        assertEquals(0, children.size)
    }

    fun testGetChildrenReturnsEmptyArrayWithNonNullEventButNoProject() {
        val group = PsaFileTemplateActionGroup()
        val event = TestActionEvent.createTestEvent(group, { null })

        val children = group.getChildren(event)

        assertEquals(0, children.size)
    }

    fun testGetChildrenReturnsEmptyArrayWithoutIdeView() {
        val group = PsaFileTemplateActionGroup()
        val psaManager = project.service<PsaManager>()
        psaManager.getSettings().singleFileCodeTemplates =
            arrayListOf(
                SingleFileCodeTemplate().apply {
                    name = "single_template"
                    title = "Single Template"
                },
            )
        val event =
            TestActionEvent.createTestEvent(
                group,
                { dataId -> if (CommonDataKeys.PROJECT.`is`(dataId)) project else null },
            )

        val children = group.getChildren(event)

        assertEquals(0, children.size)
    }

    fun testGetChildrenReturnsEmptyArrayWithoutDirectories() {
        val group = PsaFileTemplateActionGroup()
        val psaManager = project.service<PsaManager>()
        psaManager.getSettings().singleFileCodeTemplates =
            arrayListOf(
                SingleFileCodeTemplate().apply {
                    name = "single_template"
                    title = "Single Template"
                },
            )
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

    fun testGetChildrenReturnsActionsForMatchingTemplates() {
        val group = PsaFileTemplateActionGroup()
        val directory = PsiManager.getInstance(project).findDirectory(project.guessProjectDir()!!)!!
        val psaManager = project.service<PsaManager>()
        psaManager.getSettings().apply {
            singleFileCodeTemplates =
                arrayListOf(
                    SingleFileCodeTemplate().apply {
                        name = "single_template"
                        title = "Single Template"
                        pathRegex = ".*"
                    },
                )
            multipleFileCodeTemplates =
                arrayListOf(
                    MultipleFileCodeTemplate().apply {
                        name = "multi_template"
                        title = "Multi Template"
                        fileCount = 2
                        pathRegex = ".*"
                    },
                )
        }
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
        assertTrue(
            children.any {
                it is SingleFileTemplateAction &&
                    it.templatePresentation.text == "Single Template" &&
                    it.templateName == "single_template"
            },
        )
        assertTrue(
            children.any {
                it is MultipleFileTemplateAction &&
                    it.templatePresentation.text == "Multi Template" &&
                    it.templateName == "multi_template"
            },
        )
    }

    fun testGetChildrenSkipsTemplatesWithMismatchedPathRegex() {
        val group = PsaFileTemplateActionGroup()
        val directory = PsiManager.getInstance(project).findDirectory(project.guessProjectDir()!!)!!
        val psaManager = project.service<PsaManager>()
        psaManager.getSettings().apply {
            singleFileCodeTemplates =
                arrayListOf(
                    SingleFileCodeTemplate().apply {
                        name = "single_template"
                        title = "Single Template"
                        pathRegex = "^/nomatch/"
                    },
                )
            multipleFileCodeTemplates =
                arrayListOf(
                    MultipleFileCodeTemplate().apply {
                        name = "multi_template"
                        title = "Multi Template"
                        fileCount = 2
                        pathRegex = "^/nomatch/"
                    },
                )
        }
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

        assertEquals(0, children.size)
    }

    fun testGetChildrenIncludesTemplatesWithoutPathRegexFilter() {
        val group = PsaFileTemplateActionGroup()
        val directory = PsiManager.getInstance(project).findDirectory(project.guessProjectDir()!!)!!
        val psaManager = project.service<PsaManager>()
        psaManager.getSettings().apply {
            singleFileCodeTemplates =
                arrayListOf(
                    SingleFileCodeTemplate().apply {
                        name = "single_template"
                        title = "Single Template"
                    },
                )
            multipleFileCodeTemplates =
                arrayListOf(
                    MultipleFileCodeTemplate().apply {
                        name = "multi_template"
                        title = "Multi Template"
                        fileCount = 2
                    },
                )
        }
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
        assertTrue(children.any { it is SingleFileTemplateAction && it.templateName == "single_template" })
        assertTrue(children.any { it is MultipleFileTemplateAction && it.templateName == "multi_template" })
    }
}
