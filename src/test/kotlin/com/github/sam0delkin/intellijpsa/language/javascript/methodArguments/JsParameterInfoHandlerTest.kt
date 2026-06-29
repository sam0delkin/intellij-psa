package com.github.sam0delkin.intellijpsa.language.javascript.methodArguments

import com.github.sam0delkin.intellijpsa.language.javascript.model.JsMethodArgumentProviderModel
import com.github.sam0delkin.intellijpsa.language.javascript.settings.JsPsaSettings
import com.github.sam0delkin.intellijpsa.settings.Settings
import com.intellij.lang.javascript.psi.JSParameterListElement
import com.intellij.openapi.components.service
import com.intellij.testFramework.fixtures.BasePlatformTestCase
import com.intellij.testFramework.utils.parameterInfo.MockCreateParameterInfoContext

class JsParameterInfoHandlerTest : BasePlatformTestCase() {
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

    fun testShowsResolvedMethodParameters() {
        myFixture.configureByText(
            "plugin.js",
            """
            function AdvanceLender(lender, options) {}
            AdvanceLender.prototype.updateTrancheReferrers = function (${'$'}tranche, lenderData) {};
            ${'$'}.fn.advanceLender = function (option) {};
            foo.advanceLender('updateTrancheReferrers', ba<caret>r);
            """.trimIndent(),
        )

        val handler = JsParameterInfoHandler()
        val context = MockCreateParameterInfoContext(myFixture.editor, myFixture.file)
        val argumentList = handler.findElementForParameterInfo(context)

        assertNotNull("parameter info should fire on matching call", argumentList)

        val items = context.itemsToShow
        assertNotNull(items)

        @Suppress("UNCHECKED_CAST")
        val parameters = items!![0] as Array<JSParameterListElement>
        assertEquals(2, parameters.size)
        assertEquals("\$tranche", parameters[0].name)
        assertEquals("lenderData", parameters[1].name)
    }

    fun testDoesNotFireForNonMatchingCall() {
        myFixture.configureByText(
            "plugin.js",
            """
            function AdvanceLender(lender, options) {}
            AdvanceLender.prototype.updateTrancheReferrers = function (${'$'}tranche) {};
            foo.somethingElse('updateTrancheReferrers', ba<caret>r);
            """.trimIndent(),
        )

        val handler = JsParameterInfoHandler()
        val context = MockCreateParameterInfoContext(myFixture.editor, myFixture.file)

        assertNull(handler.findElementForParameterInfo(context))
    }
}
