package com.github.sam0delkin.intellijpsa.language.javascript.model

import com.intellij.testFramework.fixtures.BasePlatformTestCase
import kotlinx.serialization.json.Json

class JsInfoModelTest : BasePlatformTestCase() {
    private val jsonFormat = Json { ignoreUnknownKeys = true }

    fun testDefaultValues() {
        val model = JsInfoModel()

        assertNull(model.methodArgumentProviders)
        assertNull(model.methodArgumentInspections)
    }

    fun testInheritsFromInfoModel() {
        val model =
            JsInfoModel().apply {
                supportedLanguages.addAll(listOf("JavaScript"))
            }

        assertEquals(1, model.supportedLanguages.size)
        assertEquals("JavaScript", model.supportedLanguages[0])
    }

    fun testDeserializationFromSnakeCaseJson() {
        val json =
            """
            {
                "supported_languages": ["JavaScript"],
                "js_method_argument_providers": [
                    {
                        "reference_name": "somePlugin",
                        "class": "AdvanceLender",
                        "method_argument_index": 0,
                        "arguments_offset": 1
                    }
                ],
                "js_method_argument_inspections": true
            }
            """.trimIndent()

        val decoded = jsonFormat.decodeFromString(JsInfoModel.serializer(), json)

        assertEquals(1, decoded.supportedLanguages.size)
        assertEquals(1, decoded.methodArgumentProviders?.size)
        assertEquals("somePlugin", decoded.methodArgumentProviders?.get(0)?.referenceName)
        assertEquals(true, decoded.methodArgumentInspections)
    }

    fun testDeserializationWithoutOptionalFields() {
        val json = """{"supported_languages": ["JavaScript"]}"""

        val decoded = jsonFormat.decodeFromString(JsInfoModel.serializer(), json)

        assertNull(decoded.methodArgumentProviders)
        assertNull(decoded.methodArgumentInspections)
    }
}
