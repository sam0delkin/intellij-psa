package com.github.sam0delkin.intellijpsa.language.php.model

import com.intellij.testFramework.fixtures.BasePlatformTestCase

class PhpRequestTypeTest : BasePlatformTestCase() {
    fun testValues() {
        val values = PhpRequestType.values()

        assertEquals(1, values.size)
        assertEquals(PhpRequestType.GetTypeProviders, values[0])
    }

    fun testValueOf() {
        assertEquals(PhpRequestType.GetTypeProviders, PhpRequestType.valueOf("GetTypeProviders"))
    }

    fun testName() {
        assertEquals("GetTypeProviders", PhpRequestType.GetTypeProviders.name)
    }
}
