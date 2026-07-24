package com.github.sam0delkin.intellijpsa.util

import com.intellij.testFramework.fixtures.BasePlatformTestCase

class BuildConfigTest : BasePlatformTestCase() {
    fun testPluginVersionIsNotBlank() {
        assertTrue(BuildConfig.PLUGIN_VERSION.isNotBlank())
    }
}
