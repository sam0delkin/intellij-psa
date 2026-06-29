package com.github.sam0delkin.intellijpsa.language.javascript.methodArguments

import com.github.sam0delkin.intellijpsa.language.javascript.model.JsMethodArgumentProviderModel
import com.github.sam0delkin.intellijpsa.language.javascript.settings.JsPsaSettings
import com.github.sam0delkin.intellijpsa.settings.Settings
import com.intellij.lang.javascript.psi.JSFunction
import com.intellij.lang.javascript.psi.JSLiteralExpression
import com.intellij.openapi.components.service
import com.intellij.testFramework.fixtures.BasePlatformTestCase

class JsMethodArgumentReferenceContributorTest : BasePlatformTestCase() {
    override fun setUp() {
        super.setUp()
        project.service<Settings>().pluginEnabled = true
        project.service<Settings>().scriptPath = "psa.sh"
        project.service<JsPsaSettings>().enabled = true
        setupProvider()
    }

    fun testReferenceResolvesToPrototypeMethod() {
        myFixture.configureByText(
            "plugin.js",
            """
            function AdvanceLender(lender, options) {}
            AdvanceLender.prototype.updateTrancheReferrers = function (${'$'}tranche, lenderData) {};
            ${'$'}.fn.advanceLender = function (option) {};
            foo.advanceLender('updateTrancheReferrers', bar);
            """.trimIndent(),
        )

        val literal = myFixture.findElementByText("'updateTrancheReferrers'", JSLiteralExpression::class.java)
        assertNotNull("Method name literal not found", literal)

        val ref = literal!!.references.filterIsInstance<JsMethodReference>().firstOrNull()
        assertNotNull("JsMethodReference should be created for the method-name string", ref)

        val resolved = ref!!.resolve()
        assertTrue("Reference should resolve to a JS function", resolved is JSFunction)
        assertEquals("updateTrancheReferrers", (resolved as JSFunction).name)
    }

    fun testNoReferenceForUnknownMethod() {
        myFixture.configureByText(
            "plugin.js",
            """
            function AdvanceLender(lender, options) {}
            AdvanceLender.prototype.updateTrancheReferrers = function (${'$'}tranche) {};
            foo.advanceLender('doesNotExist', bar);
            """.trimIndent(),
        )

        val literal = myFixture.findElementByText("'doesNotExist'", JSLiteralExpression::class.java)
        assertNotNull(literal)
        val refs = literal!!.references.filterIsInstance<JsMethodReference>()
        assertTrue("Unknown method must not produce a reference", refs.isEmpty())
    }

    fun testNoReferenceWhenExtensionDisabled() {
        project.service<JsPsaSettings>().enabled = false
        myFixture.configureByText(
            "plugin.js",
            """
            function AdvanceLender(lender, options) {}
            AdvanceLender.prototype.updateTrancheReferrers = function (${'$'}tranche) {};
            foo.advanceLender('updateTrancheReferrers', bar);
            """.trimIndent(),
        )

        val literal = myFixture.findElementByText("'updateTrancheReferrers'", JSLiteralExpression::class.java)
        assertNotNull(literal)
        val refs = literal!!.references.filterIsInstance<JsMethodReference>()
        assertTrue("No reference when the JS extension is disabled", refs.isEmpty())
    }

    private fun setupProvider() {
        project.service<JsPsaSettings>().methodArgumentProviders =
            arrayListOf(
                JsMethodArgumentProviderModel().apply {
                    referenceName = "advanceLender"
                    `class` = "AdvanceLender"
                    methodArgumentIndex = 0
                    argumentsOffset = 1
                },
            )
    }
}
