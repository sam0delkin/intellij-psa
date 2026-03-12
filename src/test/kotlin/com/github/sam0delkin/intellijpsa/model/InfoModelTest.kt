package com.github.sam0delkin.intellijpsa.model

import com.github.sam0delkin.intellijpsa.settings.TemplateFormFieldType
import com.intellij.testFramework.fixtures.BasePlatformTestCase

class InfoModelTest : BasePlatformTestCase() {
    fun testInfoModelCreation() {
        val model = InfoModel().apply {
            supportedLanguages.addAll(listOf("PHP", "JavaScript"))
        }

        assertEquals(2, model.supportedLanguages.size)
        assertTrue(model.supportedLanguages.contains("PHP"))
        assertTrue(model.supportedLanguages.contains("JavaScript"))
    }

    fun testInfoModelDefaultValues() {
        val model = InfoModel()

        assertEquals(0, model.supportedLanguages.size)
        assertNull(model.goToElementFilter)
        assertNull(model.supportsBatch)
        assertNull(model.supportsStaticCompletions)
        assertNull(model.templates)
        assertNull(model.editorActions)
    }

    fun testEditorActionSourceValues() {
        assertEquals(EditorActionSource.Editor, EditorActionSource.Editor)
        assertEquals(EditorActionSource.Clipboard, EditorActionSource.Clipboard)
    }

    fun testEditorActionTargetValues() {
        assertEquals(EditorActionTarget.Editor, EditorActionTarget.Editor)
        assertEquals(EditorActionTarget.Clipboard, EditorActionTarget.Clipboard)
        assertEquals(EditorActionTarget.Nothing, EditorActionTarget.Nothing)
    }

    fun testTemplateTypeValues() {
        assertEquals(TemplateType.SINGLE_FILE, TemplateType.SINGLE_FILE)
        assertEquals(TemplateType.MULTIPLE_FILE, TemplateType.MULTIPLE_FILE)
    }

    fun testFormFieldModel() {
        val field = FormFieldModel()

        assertEquals("", field.name)
        assertEquals("", field.title)
        assertEquals(TemplateFormFieldType.Text, field.type)
        assertEquals(false, field.focused)
        assertEquals(0, field.options.size)
    }

    fun testStaticCompletionModel() {
        val model = StaticCompletionModel().apply {
            name = "my_completion"
            title = "My Completion"
            patterns = arrayListOf()
            matcher = "element_type == 'STRING_LITERAL'"
        }

        assertEquals("my_completion", model.name)
        assertEquals("My Completion", model.title)
        assertEquals("element_type == 'STRING_LITERAL'", model.matcher)
    }

    fun testStaticCompletionsModel() {
        val model = StaticCompletionsModel()

        assertNull(model.staticCompletions)
    }
}
