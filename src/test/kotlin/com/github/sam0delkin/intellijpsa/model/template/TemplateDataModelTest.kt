package com.github.sam0delkin.intellijpsa.model.template

import com.intellij.testFramework.fixtures.BasePlatformTestCase
import kotlinx.serialization.json.JsonPrimitive

class TemplateDataModelTest : BasePlatformTestCase() {
    fun testFormFieldDataModel() {
        val field = FormFieldDataModel().apply {
            value = JsonPrimitive("test_value")
            options.addAll(listOf("option1", "option2", "option3"))
        }

        assertNotNull(field.value)
        assertEquals("test_value", (field.value as JsonPrimitive).content)
        assertEquals(3, field.options.size)
        assertEquals("option1", field.options[0])
    }

    fun testTemplateDataModelFileName() {
        val model = TemplateDataModel()

        assertNull(model.fileName)
        assertNull(model.fileNames)
        assertEquals("", model.content)
        assertNull(model.contents)
        assertNull(model.formFields)
    }

    fun testTemplateDataModelWithFormFields() {
        val model = TemplateDataModel()

        assertNotNull(model)
        assertEquals("", model.content)
    }

    fun testTemplateDataModelMultipleFiles() {
        val model = TemplateDataModel()

        assertNotNull(model)
    }

    fun testFormFieldDataModelWithArrayValue() {
        val field = FormFieldDataModel()

        assertNotNull(field)
        assertEquals(0, field.options.size)
    }

    fun testTemplateDataModelNullContent() {
        val model = TemplateDataModel()

        assertEquals("", model.content)
    }
}
