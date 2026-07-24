package com.github.sam0delkin.intellijpsa.model

import com.github.sam0delkin.intellijpsa.model.completion.CompletionModel
import com.github.sam0delkin.intellijpsa.model.completion.CompletionsModel
import com.github.sam0delkin.intellijpsa.model.completion.NotificationModel
import com.github.sam0delkin.intellijpsa.model.psi.PsiElementPatternModel
import com.intellij.testFramework.fixtures.BasePlatformTestCase

class ExtendedInfoModelTest : BasePlatformTestCase() {
    fun testExtendedStaticCompletionModelCreateFromModelCopiesFields() {
        val model =
            StaticCompletionModel().apply {
                name = "my_static"
                title = "My Static"
                patterns = arrayListOf(PsiElementPatternModel(withType = "STRING_LITERAL"))
                matcher = "some_matcher"
                completions =
                    CompletionsModel().apply {
                        completions = listOf(CompletionModel().apply { text = "A" })
                    }
            }

        val extended = ExtendedStaticCompletionModel.createFromModel(model, project)

        assertEquals("my_static", extended.name)
        assertEquals("My Static", extended.title)
        assertEquals(1, extended.patterns?.size)
        assertEquals("some_matcher", extended.matcher)
        assertSame(model.completions, extended.completions)
        assertNotNull(extended.extendedCompletions)
        assertEquals(1, extended.extendedCompletions?.extendedCompletions?.size)
    }

    fun testExtendedStaticCompletionModelToStaticCompletionModel() {
        val model =
            StaticCompletionModel().apply {
                name = "my_static"
                title = "My Static"
                patterns = arrayListOf(PsiElementPatternModel(withType = "STRING_LITERAL"))
                matcher = "some_matcher"
                completions = CompletionsModel().apply { completions = listOf(CompletionModel().apply { text = "A" }) }
            }
        val extended = ExtendedStaticCompletionModel.createFromModel(model, project)

        val roundTripped = extended.toStaticCompletionModel()

        assertEquals(model.name, roundTripped.name)
        assertEquals(model.title, roundTripped.title)
        assertEquals(model.patterns, roundTripped.patterns)
        assertEquals(model.matcher, roundTripped.matcher)
        assertSame(model.completions, roundTripped.completions)
    }

    fun testExtendedCompletionsModelCreateFromModelCopiesCompletionsAndNotifications() {
        val model =
            CompletionsModel().apply {
                completions =
                    listOf(
                        CompletionModel().apply { text = "A" },
                        CompletionModel().apply { text = "B" },
                    )
                notifications =
                    listOf(
                        NotificationModel().apply {
                            type = "info"
                            text = "hi"
                        },
                    )
            }

        val extended = ExtendedCompletionsModel.createFromModel(model, project)

        assertSame(model.completions, extended.completions)
        assertEquals(model.notifications, extended.notifications)
        assertEquals(2, extended.extendedCompletions?.size)
        assertEquals("A", extended.extendedCompletions?.get(0)?.text)
        assertEquals("B", extended.extendedCompletions?.get(1)?.text)
    }

    fun testExtendedCompletionsModelCreateFromModelWithEmptyCompletions() {
        val model = CompletionsModel().apply { completions = emptyList() }

        val extended = ExtendedCompletionsModel.createFromModel(model, project)

        assertNotNull(extended.extendedCompletions)
        assertTrue(extended.extendedCompletions!!.isEmpty())
    }

    fun testExtendedStaticCompletionsModelDefaultValues() {
        val model = ExtendedStaticCompletionsModel()

        assertNull(model.staticCompletions)
        assertNull(model.hash)
    }

    fun testExtendedStaticCompletionsModelValuesCanBeSet() {
        val staticModel =
            StaticCompletionModel().apply {
                name = "my_static"
                completions = CompletionsModel().apply { completions = listOf(CompletionModel().apply { text = "A" }) }
            }
        val extendedStatic = ExtendedStaticCompletionModel.createFromModel(staticModel, project)

        val model =
            ExtendedStaticCompletionsModel().apply {
                staticCompletions = arrayListOf(extendedStatic)
                hash = "abc123"
            }

        assertEquals(1, model.staticCompletions?.size)
        assertSame(extendedStatic, model.staticCompletions?.get(0))
        assertEquals("abc123", model.hash)
    }
}
