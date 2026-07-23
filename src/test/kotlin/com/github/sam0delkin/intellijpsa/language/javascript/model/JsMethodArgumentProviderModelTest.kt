package com.github.sam0delkin.intellijpsa.language.javascript.model

import com.intellij.testFramework.fixtures.BasePlatformTestCase
import kotlinx.serialization.json.Json

class JsMethodArgumentProviderModelTest : BasePlatformTestCase() {
    private val jsonFormat = Json { ignoreUnknownKeys = true }

    fun testDefaultValues() {
        val model = JsMethodArgumentProviderModel()

        assertEquals("", model.referenceName)
        assertEquals("", model.`class`)
        assertEquals(0, model.methodArgumentIndex)
        assertEquals(1, model.argumentsOffset)
    }

    fun testValuesCanBeSet() {
        val model =
            JsMethodArgumentProviderModel().apply {
                referenceName = "somePlugin"
                `class` = "AdvanceLender"
                methodArgumentIndex = 0
                argumentsOffset = 2
            }

        assertEquals("somePlugin", model.referenceName)
        assertEquals("AdvanceLender", model.`class`)
        assertEquals(0, model.methodArgumentIndex)
        assertEquals(2, model.argumentsOffset)
    }

    fun testSerialization() {
        val model =
            JsMethodArgumentProviderModel().apply {
                referenceName = "somePlugin"
                `class` = "AdvanceLender"
                methodArgumentIndex = 1
                argumentsOffset = 2
            }

        val encoded = Json.encodeToString(JsMethodArgumentProviderModel.serializer(), model)
        val decoded = Json.decodeFromString(JsMethodArgumentProviderModel.serializer(), encoded)

        assertEquals(model.referenceName, decoded.referenceName)
        assertEquals(model.`class`, decoded.`class`)
        assertEquals(model.methodArgumentIndex, decoded.methodArgumentIndex)
        assertEquals(model.argumentsOffset, decoded.argumentsOffset)
    }

    fun testDeserializationFromSnakeCaseJson() {
        val json =
            """
            {
                "reference_name": "somePlugin",
                "class": "AdvanceLender",
                "method_argument_index": 0,
                "arguments_offset": 1
            }
            """.trimIndent()

        val decoded = jsonFormat.decodeFromString(JsMethodArgumentProviderModel.serializer(), json)

        assertEquals("somePlugin", decoded.referenceName)
        assertEquals("AdvanceLender", decoded.`class`)
        assertEquals(0, decoded.methodArgumentIndex)
        assertEquals(1, decoded.argumentsOffset)
    }
}
