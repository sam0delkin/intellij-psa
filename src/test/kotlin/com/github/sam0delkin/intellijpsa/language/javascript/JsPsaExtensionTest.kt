package com.github.sam0delkin.intellijpsa.language.javascript

import com.github.sam0delkin.intellijpsa.language.javascript.model.JsMethodArgumentProviderModel
import com.github.sam0delkin.intellijpsa.language.javascript.settings.JsPsaSettings
import com.intellij.openapi.components.service
import com.intellij.testFramework.fixtures.BasePlatformTestCase

class JsPsaExtensionTest : BasePlatformTestCase() {
    fun testDiagnosticsWhenDisabled() {
        project.service<JsPsaSettings>().enabled = false

        assertEquals("JavaScript: disabled", JsPsaExtension().getDiagnostics(project))
    }

    fun testUpdateInfoWhenEnabledAppliesMethodArgumentProviders() {
        project.service<JsPsaSettings>().enabled = true

        JsPsaExtension().updateInfo(
            project,
            """
            {
                "js_method_argument_providers": [
                    {"reference_name": "advanceLender", "class": "AdvanceLender", "method_argument_index": 0, "arguments_offset": 1}
                ],
                "js_method_argument_inspections": true
            }
            """.trimIndent(),
        )

        val settings = project.service<JsPsaSettings>()
        assertEquals(1, settings.methodArgumentProviders?.size)
        assertEquals("AdvanceLender", settings.methodArgumentProviders?.get(0)?.`class`)
        assertTrue(settings.methodArgumentProvidersInspectionsEnabled)
    }

    fun testUpdateInfoIgnoredWhenDisabled() {
        project.service<JsPsaSettings>().enabled = false
        project.service<JsPsaSettings>().methodArgumentProviders = null

        JsPsaExtension().updateInfo(
            project,
            """{"js_method_argument_providers": [{"reference_name": "x", "class": "Y"}]}""",
        )

        assertNull(project.service<JsPsaSettings>().methodArgumentProviders)
    }

    fun testUpdateInfoWithInvalidJsonDoesNotThrow() {
        project.service<JsPsaSettings>().enabled = true

        JsPsaExtension().updateInfo(project, "not valid json")
    }

    fun testDiagnosticsWhenEnabled() {
        project.service<JsPsaSettings>().enabled = true
        project.service<JsPsaSettings>().methodArgumentProvidersInspectionsEnabled = true
        project.service<JsPsaSettings>().methodArgumentProviders =
            arrayListOf(
                JsMethodArgumentProviderModel().apply {
                    referenceName = "advanceLender"
                    `class` = "AdvanceLender"
                },
            )

        val diagnostics = JsPsaExtension().getDiagnostics(project)!!

        assertTrue(diagnostics.contains("JavaScript: enabled"))
        assertTrue(diagnostics.contains("Method argument inspections: true"))
        assertTrue(diagnostics.contains("Method argument providers: 1"))
        assertTrue(diagnostics.contains("AdvanceLender"))
    }
}
