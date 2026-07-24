package com.github.sam0delkin.intellijpsa.language.php

import com.github.sam0delkin.intellijpsa.language.php.services.PhpPsaManager
import com.intellij.openapi.components.service
import com.intellij.openapi.util.Disposer
import com.intellij.testFramework.fixtures.BasePlatformTestCase
import com.intellij.ui.components.JBCheckBox
import com.intellij.ui.dsl.builder.Cell
import com.intellij.ui.dsl.builder.panel

class PhpPsaExtensionTest : BasePlatformTestCase() {
    private lateinit var extension: PhpPsaExtension

    override fun setUp() {
        super.setUp()
        extension = PhpPsaExtension()
    }

    @Suppress("UNCHECKED_CAST")
    private fun enabledCheckbox(): JBCheckBox {
        val field = PhpPsaExtension::class.java.getDeclaredField("enabled")
        field.isAccessible = true
        return (field.get(extension) as Cell<*>).component as JBCheckBox
    }

    fun testApplyDisconnectsConnectionWhenTransitioningToDisabled() {
        val phpSettings = project.service<PhpPsaManager>().getSettings()
        phpSettings.enabled = true
        phpSettings.toStringValueFormatter = "some_formatter"
        extension.initialize(project)
        assertNotNull(extension.connection)

        panel { extension.configure(this, project) }
        enabledCheckbox().isSelected = false

        extension.apply(project)

        assertFalse(phpSettings.enabled)
        assertNull(extension.connection)
    }

    fun testApplyKeepsConnectionWhenStayingEnabled() {
        val phpSettings = project.service<PhpPsaManager>().getSettings()
        phpSettings.enabled = true

        panel { extension.configure(this, project) }
        enabledCheckbox().isSelected = true

        extension.apply(project)

        assertTrue(phpSettings.enabled)
    }

    fun testGetDiagnosticsWhenDisabled() {
        project.service<PhpPsaManager>().getSettings().enabled = false

        assertEquals("PHP: disabled", extension.getDiagnostics(project))
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

    @Suppress("DEPRECATION")
    fun testXdebugConnectionIsDisconnectedWhenPhpPsaManagerIsDisposed() {
        val phpSettings = project.service<PhpPsaManager>().getSettings()
        phpSettings.enabled = true
        phpSettings.toStringValueFormatter = "some_formatter"

        extension.initialize(project)

        val connection = extension.connection
        assertNotNull(connection)
        assertFalse(Disposer.isDisposed(connection!!))

        // PhpPsaExtension is a plain `psaExtension` EP instance - nothing disposes it directly.
        // The connection must be anchored to PhpPsaManager (a light service the platform disposes
        // on project close / plugin unload), or it would otherwise leak past disposal and keep the
        // plugin's classloader from being unloaded.
        Disposer.dispose(project.service<PhpPsaManager>())

        assertTrue(Disposer.isDisposed(connection))
    }
}
