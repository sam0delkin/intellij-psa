package com.github.sam0delkin.intellijpsa.language.php

import com.github.sam0delkin.intellijpsa.language.php.services.PhpPsaManager
import com.intellij.openapi.components.service
import com.intellij.testFramework.fixtures.BasePlatformTestCase

class PhpPsaExtensionTest : BasePlatformTestCase() {
    private lateinit var extension: PhpPsaExtension

    override fun setUp() {
        super.setUp()
        extension = PhpPsaExtension()
    }

    fun testUpdateInfo() {
        val phpSettings = project.service<PhpPsaManager>().getSettings()
        phpSettings.enabled = true
        phpSettings.supportsTypeProviders = false
        phpSettings.toStringValueFormatter = null

        val infoJson =
            """
            {
                "supports_type_providers": true,
                "to_string_value_formatter": "some_formatter"
            }
            """.trimIndent()

        extension.updateInfo(project, infoJson)

        assertTrue(phpSettings.supportsTypeProviders)
        assertEquals("some_formatter", phpSettings.toStringValueFormatter)
    }

    fun testUpdateInfoDisabled() {
        val phpSettings = project.service<PhpPsaManager>().getSettings()
        phpSettings.enabled = false
        phpSettings.supportsTypeProviders = false

        val infoJson =
            """
            {
                "supports_type_providers": true
            }
            """.trimIndent()

        extension.updateInfo(project, infoJson)

        // Should NOT update if disabled
        assertFalse(phpSettings.supportsTypeProviders)
    }

    fun testUpdateInfoInvalidJson() {
        val phpSettings = project.service<PhpPsaManager>().getSettings()
        phpSettings.enabled = true
        phpSettings.supportsTypeProviders = false

        extension.updateInfo(project, "invalid json")

        // Should NOT update and not throw exception
        assertFalse(phpSettings.supportsTypeProviders)
    }

    fun testUpdateInfoPartialJson() {
        val phpSettings = project.service<PhpPsaManager>().getSettings()
        phpSettings.enabled = true
        phpSettings.supportsTypeProviders = true
        phpSettings.toStringValueFormatter = "old_formatter"

        val infoJson =
            """
            {
                "supports_type_providers": false
            }
            """.trimIndent()

        extension.updateInfo(project, infoJson)

        assertFalse(phpSettings.supportsTypeProviders)
        assertNull(phpSettings.toStringValueFormatter)
    }
}
