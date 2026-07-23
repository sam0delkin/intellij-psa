package com.github.sam0delkin.intellijpsa.language.php.model

import com.intellij.testFramework.fixtures.BasePlatformTestCase
import kotlinx.serialization.json.Json

class MethodArgumentProviderModelTest : BasePlatformTestCase() {
    private val jsonFormat = Json { ignoreUnknownKeys = true }

    fun testDefaultValues() {
        val model = MethodArgumentProviderModel()

        assertEquals("", model.`class`)
        assertEquals("", model.method)
        assertNull(model.classArgumentIndex)
        assertNull(model.methodArgumentIndex)
        assertNull(model.callableArgumentIndex)
        assertEquals(0, model.argumentsArgumentIndex)
        assertEquals(0, model.argumentsOffset)
    }

    fun testSeparateArgsProviderValues() {
        val model =
            MethodArgumentProviderModel().apply {
                `class` = "QueueManager"
                method = "executeServiceMethod"
                classArgumentIndex = 0
                methodArgumentIndex = 1
                argumentsArgumentIndex = 2
            }

        assertEquals("QueueManager", model.`class`)
        assertEquals("executeServiceMethod", model.method)
        assertEquals(0, model.classArgumentIndex)
        assertEquals(1, model.methodArgumentIndex)
        assertEquals(2, model.argumentsArgumentIndex)
        assertNull(model.callableArgumentIndex)
    }

    fun testCallableProviderValues() {
        val model =
            MethodArgumentProviderModel().apply {
                `class` = "ServiceMethodMessage"
                method = "__construct"
                callableArgumentIndex = 0
                argumentsArgumentIndex = 1
            }

        assertEquals("ServiceMethodMessage", model.`class`)
        assertEquals("__construct", model.method)
        assertEquals(0, model.callableArgumentIndex)
        assertEquals(1, model.argumentsArgumentIndex)
        assertNull(model.classArgumentIndex)
        assertNull(model.methodArgumentIndex)
    }

    fun testSerialization() {
        val model =
            MethodArgumentProviderModel().apply {
                `class` = "QueueManager"
                method = "executeServiceMethod"
                classArgumentIndex = 0
                methodArgumentIndex = 1
                argumentsArgumentIndex = 2
                argumentsOffset = 1
            }

        val encoded = Json.encodeToString(MethodArgumentProviderModel.serializer(), model)
        val decoded = Json.decodeFromString(MethodArgumentProviderModel.serializer(), encoded)

        assertEquals(model.`class`, decoded.`class`)
        assertEquals(model.method, decoded.method)
        assertEquals(model.classArgumentIndex, decoded.classArgumentIndex)
        assertEquals(model.methodArgumentIndex, decoded.methodArgumentIndex)
        assertEquals(model.argumentsArgumentIndex, decoded.argumentsArgumentIndex)
        assertEquals(model.argumentsOffset, decoded.argumentsOffset)
    }

    fun testDeserializationFromSnakeCaseJson() {
        val json =
            """
            {
                "class": "ServiceMethodMessage",
                "method": "__construct",
                "callable_argument_index": 0,
                "arguments_argument_index": 1,
                "arguments_offset": 0
            }
            """.trimIndent()

        val decoded = jsonFormat.decodeFromString(MethodArgumentProviderModel.serializer(), json)

        assertEquals("ServiceMethodMessage", decoded.`class`)
        assertEquals("__construct", decoded.method)
        assertEquals(0, decoded.callableArgumentIndex)
        assertEquals(1, decoded.argumentsArgumentIndex)
        assertNull(decoded.classArgumentIndex)
        assertNull(decoded.methodArgumentIndex)
    }
}
