package com.github.sam0delkin.intellijpsa.icons

import com.intellij.testFramework.fixtures.BasePlatformTestCase

class IconsTest : BasePlatformTestCase() {
    fun testPluginIconLoads() {
        assertNotNull(Icons.PluginIcon)
    }

    fun testPluginActiveIconLoads() {
        assertNotNull(Icons.PluginActiveIcon)
    }

    fun testPluginErrorIconLoads() {
        assertNotNull(Icons.PluginErrorIcon)
    }

    fun testIconsAreDistinct() {
        assertNotSame(Icons.PluginIcon, Icons.PluginActiveIcon)
        assertNotSame(Icons.PluginIcon, Icons.PluginErrorIcon)
        assertNotSame(Icons.PluginActiveIcon, Icons.PluginErrorIcon)
    }
}
