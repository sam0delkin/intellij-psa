package com.github.sam0delkin.intellijpsa.util

import com.intellij.testFramework.fixtures.BasePlatformTestCase

class PropertyAccessorTest : BasePlatformTestCase() {
    data class Inner(
        val value: String,
    )

    data class Sample(
        val name: String,
        val inner: Inner,
        val items: List<String>,
        val tags: List<String>,
        val map: Map<String, String>,
    )

    fun testGetPropertyValueEmptyPathReturnsToString() {
        assertEquals("hello", PropertyAccessor.getPropertyValue("hello", ""))
    }

    fun testGetPropertyValueSimpleProperty() {
        val sample = Sample("my_name", Inner("x"), listOf(), listOf(), mapOf())

        assertEquals("my_name", PropertyAccessor.getPropertyValue(sample, "name"))
    }

    fun testGetPropertyValueNestedProperty() {
        val sample = Sample("my_name", Inner("nested_value"), listOf(), listOf(), mapOf())

        assertEquals("nested_value", PropertyAccessor.getPropertyValue(sample, "inner.value"))
    }

    fun testGetPropertyValueListIndex() {
        val sample = Sample("n", Inner("x"), listOf("a", "b", "c"), listOf(), mapOf())

        assertEquals("b", PropertyAccessor.getPropertyValue(sample, "items.1"))
    }

    fun testGetPropertyValueArrayIndex() {
        val sample = Sample("n", Inner("x"), listOf(), listOf("a", "b"), mapOf())

        assertEquals("b", PropertyAccessor.getPropertyValue(sample, "tags.1"))
    }

    fun testGetPropertyValueMapKey() {
        val sample = Sample("n", Inner("x"), listOf(), listOf(), mapOf("k" to "v"))

        assertEquals("v", PropertyAccessor.getPropertyValue(sample, "map.k"))
    }

    fun testGetPropertyValueReturnsNullWhenIntermediateIsNull() {
        assertNull(PropertyAccessor.getPropertyValue(mapOf("a" to null), "a.b"))
    }

    fun testGetIndexedPropertyThrowsForNonListNonArray() {
        val sample = Sample("n", Inner("x"), listOf(), listOf(), mapOf())

        try {
            PropertyAccessor.getPropertyValue(sample, "name.0")
            fail("Expected IllegalArgumentException")
        } catch (_: IllegalArgumentException) {
        }
    }

    fun testConvertToPropertyPathMapWithMap() {
        val result = PropertyAccessor.convertToPropertyPathMap(mapOf("a" to "1", "b" to "2"))

        assertEquals("1", result["a"])
        assertEquals("2", result["b"])
    }

    fun testConvertToPropertyPathMapWithList() {
        val result = PropertyAccessor.convertToPropertyPathMap(listOf("x", "y"))

        assertEquals("x", result["0"])
        assertEquals("y", result["1"])
    }

    fun testConvertToPropertyPathMapWithArray() {
        val result = PropertyAccessor.convertToPropertyPathMap(arrayOf("x", "y"))

        assertEquals("x", result["0"])
        assertEquals("y", result["1"])
    }

    fun testConvertToPropertyPathMapWithDataClass() {
        val sample = Sample("my_name", Inner("nested"), listOf("a"), listOf("b"), mapOf("k" to "v"))

        val result = PropertyAccessor.convertToPropertyPathMap(sample)

        assertEquals("my_name", result["name"])
        assertEquals("nested", result["inner.value"])
        assertEquals("a", result["items.0"])
        assertEquals("b", result["tags.0"])
        assertEquals("v", result["map.k"])
    }

    fun testConvertToPropertyPathMapWithNonDataClass() {
        val result = PropertyAccessor.convertToPropertyPathMap(42)

        assertEquals("42", result[""])
    }

    fun testConvertToPropertyPathMapWithNullSkipsEntirely() {
        val result = PropertyAccessor.convertToPropertyPathMap(mapOf("a" to null))

        assertTrue(result.isEmpty())
    }

    fun testInstantiation() {
        assertNotNull(PropertyAccessor())
    }
}
