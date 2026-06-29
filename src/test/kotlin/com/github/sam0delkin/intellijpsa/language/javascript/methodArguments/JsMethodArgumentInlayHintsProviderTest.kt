package com.github.sam0delkin.intellijpsa.language.javascript.methodArguments

import com.github.sam0delkin.intellijpsa.language.javascript.model.JsMethodArgumentProviderModel
import com.github.sam0delkin.intellijpsa.language.javascript.settings.JsPsaSettings
import com.github.sam0delkin.intellijpsa.settings.Settings
import com.intellij.lang.javascript.psi.JSCallExpression
import com.intellij.openapi.components.service
import com.intellij.psi.util.PsiTreeUtil
import com.intellij.testFramework.fixtures.BasePlatformTestCase

class JsMethodArgumentInlayHintsProviderTest : BasePlatformTestCase() {
    override fun setUp() {
        super.setUp()
        project.service<Settings>().pluginEnabled = true
        project.service<Settings>().scriptPath = "psa.sh"
        project.service<JsPsaSettings>().enabled = true
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

    fun testMapsDispatchedArgumentsToParameters() {
        myFixture.configureByText(
            "plugin.js",
            """
            function AdvanceLender(lender, options) {}
            AdvanceLender.prototype.updateTrancheReferrers = function (${'$'}tranche, lenderData) {};
            ${'$'}.fn.advanceLender = function (option) {};
            foo.advanceLender('updateTrancheReferrers', tranche, data);
            """.trimIndent(),
        )

        val call =
            PsiTreeUtil
                .findChildrenOfType(myFixture.file, JSCallExpression::class.java)
                .first { it.methodExpression?.text?.endsWith("advanceLender") == true && it.arguments.size == 3 }
        val match = JsMethodArgumentHelper.findProviderForCall(call, project.service<JsPsaSettings>().methodArgumentProviders!!)

        assertNotNull(match)

        val mappings = JsMethodArgumentHelper.mapArgumentsToParameters(call, match!!)
        assertEquals(2, mappings.size)
        assertEquals("\$tranche", mappings[0].parameter.name)
        assertEquals("tranche", mappings[0].value.text)
        assertEquals("lenderData", mappings[1].parameter.name)
        assertEquals("data", mappings[1].value.text)
    }
}
