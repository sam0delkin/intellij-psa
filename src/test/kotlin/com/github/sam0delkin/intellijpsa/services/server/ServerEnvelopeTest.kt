package com.github.sam0delkin.intellijpsa.services.server

import com.github.sam0delkin.intellijpsa.model.psi.PsiElementModel
import com.intellij.testFramework.fixtures.BasePlatformTestCase
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.decodeFromJsonElement
import kotlinx.serialization.json.encodeToJsonElement

class ServerEnvelopeTest : BasePlatformTestCase() {
    private val json = Json { ignoreUnknownKeys = true }

    fun testRequestEnvelopeRoundTripWithContext() {
        val model =
            PsiElementModel(
                id = "1",
                elementType = "String",
                options = mutableMapOf(),
                elementName = null,
                elementFqn = null,
                elementSignature = null,
                text = "'bar'",
                parent = null,
                prev = null,
                next = null,
                textRange = null,
            )
        val request =
            ServerRequestEnvelope(
                id = "req-1",
                type = "Completion",
                language = "PHP",
                debug = true,
                offset = 5,
                context = json.encodeToJsonElement(model),
            )

        val encoded = json.encodeToString(request)
        val decoded = json.decodeFromString<ServerRequestEnvelope>(encoded)

        assertEquals("req-1", decoded.id)
        assertEquals("Completion", decoded.type)
        assertEquals("PHP", decoded.language)
        assertTrue(decoded.debug)
        assertEquals(5, decoded.offset)
        assertNotNull(decoded.context)

        val decodedModel = json.decodeFromJsonElement<PsiElementModel>(decoded.context!!)
        assertEquals("'bar'", decodedModel.text)
    }

    fun testResponseEnvelopeRoundTripWithResult() {
        val response =
            ServerResponseEnvelope(
                id = "req-2",
                result = json.parseToJsonElement("""{"completions":[],"notifications":[]}"""),
                error = null,
            )

        val encoded = json.encodeToString(response)
        val decoded = json.decodeFromString<ServerResponseEnvelope>(encoded)

        assertEquals("req-2", decoded.id)
        assertNull(decoded.error)
        assertNotNull(decoded.result)
    }

    fun testResponseEnvelopeRoundTripWithError() {
        val response =
            ServerResponseEnvelope(
                id = "req-3",
                result = null,
                error = "boom",
            )

        val encoded = json.encodeToString(response)
        val decoded = json.decodeFromString<ServerResponseEnvelope>(encoded)

        assertEquals("req-3", decoded.id)
        assertNull(decoded.result)
        assertEquals("boom", decoded.error)
    }

    fun testDecodeHandWrittenNdjsonLine() {
        val line = """{"id":"1","type":"Info","language":null,"debug":false,"offset":null,"context":null}"""
        val decoded = json.decodeFromString<ServerRequestEnvelope>(line)

        assertEquals("1", decoded.id)
        assertEquals("Info", decoded.type)
        assertNull(decoded.language)
        assertFalse(decoded.debug)
        assertNull(decoded.offset)
        assertNull(decoded.context)
    }

    fun testDecodeRawStringResultForPerformEditorAction() {
        val line = """{"id":"1","result":"hello"}"""
        val decoded = json.decodeFromString<ServerResponseEnvelope>(line)

        assertNotNull(decoded.result)
        val stringResult = json.decodeFromJsonElement<String>(decoded.result!!)
        assertEquals("hello", stringResult)
    }
}
