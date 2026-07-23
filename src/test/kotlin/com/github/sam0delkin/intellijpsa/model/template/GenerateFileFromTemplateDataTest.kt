package com.github.sam0delkin.intellijpsa.model.template

import com.intellij.testFramework.fixtures.BasePlatformTestCase
import kotlinx.serialization.json.Json

class GenerateFileFromTemplateDataTest : BasePlatformTestCase() {
    fun testGenerateFileFromTemplateData() {
        val data =
            GenerateFileFromTemplateData(
                actionPath = "/path/to/action",
                templateType = "single_file",
                templateName = "MyTemplate",
                originatorFieldName = "className",
                formFields = mapOf("field1" to "value1"),
            )

        assertEquals("/path/to/action", data.actionPath)
        assertEquals("single_file", data.templateType)
        assertEquals("MyTemplate", data.templateName)
        assertEquals("className", data.originatorFieldName)
        assertEquals("value1", data.formFields["field1"])
    }

    fun testGenerateFileFromTemplateDataSerialization() {
        val data =
            GenerateFileFromTemplateData(
                actionPath = "/path",
                templateType = "multiple_file",
                templateName = "Template",
                originatorFieldName = null,
                formFields = emptyMap(),
            )

        val encoded = Json.encodeToString(GenerateFileFromTemplateData.serializer(), data)
        val decoded = Json.decodeFromString(GenerateFileFromTemplateData.serializer(), encoded)

        assertEquals(data, decoded)
    }
}
