package com.github.sam0delkin.intellijpsa.index

import com.intellij.testFramework.fixtures.BasePlatformTestCase
import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import java.io.DataInputStream
import java.io.DataOutputStream

class MapDataExternalizerTest : BasePlatformTestCase() {
    fun testSaveAndReadRoundTrip() {
        val map =
            mapOf(
                "key1" to listOf("value1", "value2"),
                "key2" to listOf("value3"),
            )
        val externalizer = MapDataExternalizer()

        val bytes = ByteArrayOutputStream()
        externalizer.save(DataOutputStream(bytes), map)

        val restored = externalizer.read(DataInputStream(ByteArrayInputStream(bytes.toByteArray())))

        assertEquals(map, restored)
    }

    fun testSaveAndReadEmptyMap() {
        val externalizer = MapDataExternalizer()

        val bytes = ByteArrayOutputStream()
        externalizer.save(DataOutputStream(bytes), emptyMap())

        val restored = externalizer.read(DataInputStream(ByteArrayInputStream(bytes.toByteArray())))

        assertTrue(restored.isEmpty())
    }

    fun testSaveAndReadMapWithEmptyList() {
        val map = mapOf("key" to emptyList<String>())
        val externalizer = MapDataExternalizer()

        val bytes = ByteArrayOutputStream()
        externalizer.save(DataOutputStream(bytes), map)

        val restored = externalizer.read(DataInputStream(ByteArrayInputStream(bytes.toByteArray())))

        assertEquals(map, restored)
    }
}
