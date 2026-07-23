package com.github.sam0delkin.intellijpsa.language.javascript.methodArguments

import com.github.sam0delkin.intellijpsa.language.javascript.model.JsMethodArgumentProviderModel
import com.github.sam0delkin.intellijpsa.language.javascript.settings.JsPsaSettings
import com.github.sam0delkin.intellijpsa.settings.Settings
import com.intellij.openapi.components.service
import com.intellij.testFramework.fixtures.BasePlatformTestCase

class JsMethodArgumentInspectionTest : BasePlatformTestCase() {
    override fun setUp() {
        super.setUp()
        project.service<Settings>().pluginEnabled = true
        project.service<Settings>().scriptPath = "psa.sh"
        project.service<JsPsaSettings>().enabled = true
        project.service<JsPsaSettings>().methodArgumentProvidersInspectionsEnabled = true
        project.service<JsPsaSettings>().methodArgumentProviders =
            arrayListOf(
                JsMethodArgumentProviderModel().apply {
                    referenceName = "advanceLender"
                    `class` = "AdvanceLender"
                    methodArgumentIndex = 0
                    argumentsOffset = 1
                },
            )
        myFixture.enableInspections(JsMethodArgumentInspection())
    }

    private val pluginSource =
        """
        function AdvanceLender(lender, options) {}
        AdvanceLender.prototype.updateTrancheReferrers = function (${'$'}tranche) {};
        ${'$'}.fn.advanceLender = function (option) {};
        """.trimIndent()

    fun testFlagsUnknownMethod() {
        myFixture.configureByText("plugin.js", "$pluginSource\nfoo.advanceLender('doesNotExist', bar);")

        val problem = myFixture.doHighlighting().firstOrNull { it.description?.contains("doesNotExist") == true }
        assertNotNull("unknown method should be reported", problem)
    }

    fun testDoesNotFlagKnownMethod() {
        myFixture.configureByText("plugin.js", "$pluginSource\nfoo.advanceLender('updateTrancheReferrers', bar);")

        val problem = myFixture.doHighlighting().firstOrNull { it.description?.contains("updateTrancheReferrers") == true }
        assertNull("known method must not be reported", problem)
    }

    fun testDoesNotFlagWhenInspectionDisabledByScript() {
        project.service<JsPsaSettings>().methodArgumentProvidersInspectionsEnabled = false
        myFixture.configureByText("plugin.js", "$pluginSource\nfoo.advanceLender('doesNotExist', bar);")

        val problem = myFixture.doHighlighting().firstOrNull { it.description?.contains("doesNotExist") == true }
        assertNull("inspection must be off when the script did not enable it", problem)
    }
}
