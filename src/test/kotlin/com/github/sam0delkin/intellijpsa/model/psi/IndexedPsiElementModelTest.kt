package com.github.sam0delkin.intellijpsa.model.psi

import com.intellij.testFramework.fixtures.BasePlatformTestCase
import kotlinx.serialization.json.Json

class IndexedPsiElementModelTest : BasePlatformTestCase() {
    private fun createModel(): PsiElementModel =
        PsiElementModel(
            id = "hash123",
            elementType = "STRING_LITERAL",
            options = mutableMapOf(),
            elementName = null,
            elementFqn = null,
            elementSignature = null,
            text = "'test'",
            parent = null,
            prev = null,
            next = null,
            textRange = null,
        )

    fun testIndexedPsiElementModel() {
        val model = createModel()
        val indexed = IndexedPsiElementModel(model = model, textRange = "0:10")

        assertEquals(model, indexed.model)
        assertEquals("0:10", indexed.textRange)
    }

    fun testIndexedPsiElementModelEqualsAndCopy() {
        val model = createModel()
        val indexed1 = IndexedPsiElementModel(model = model, textRange = "0:10")
        val indexed2 = IndexedPsiElementModel(model = model, textRange = "0:10")
        val copy = indexed1.copy(textRange = "5:15")

        assertEquals(indexed1, indexed2)
        assertEquals(indexed1.hashCode(), indexed2.hashCode())
        assertEquals("5:15", copy.textRange)
        assertNotNull(indexed1.toString())
    }

    fun testIndexedPsiElementModelSerialization() {
        val model = createModel()
        val indexed = IndexedPsiElementModel(model = model, textRange = "0:10")

        val encoded = Json.encodeToString(IndexedPsiElementModel.serializer(), indexed)
        val decoded = Json.decodeFromString(IndexedPsiElementModel.serializer(), encoded)

        assertEquals(indexed.textRange, decoded.textRange)
        assertEquals(indexed.model.id, decoded.model.id)
    }
}
