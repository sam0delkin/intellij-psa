package com.github.sam0delkin.intellijpsa.language.javascript.methodArguments

import com.github.sam0delkin.intellijpsa.language.javascript.model.JsMethodArgumentProviderModel
import com.github.sam0delkin.intellijpsa.language.javascript.settings.JsPsaSettings
import com.github.sam0delkin.intellijpsa.settings.Settings
import com.intellij.openapi.components.service
import com.intellij.testFramework.fixtures.BasePlatformTestCase

class JsMethodNameCompletionContributorTest : BasePlatformTestCase() {
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

    fun testCompletesMethodNames() {
        myFixture.configureByText(
            "plugin.js",
            """
            function AdvanceLender(lender, options) {}
            AdvanceLender.prototype.updateTrancheReferrers = function (${'$'}tranche) {};
            AdvanceLender.prototype.clearReferrers = function () {};
            ${'$'}.fn.advanceLender = function (option) {};
            foo.advanceLender('<caret>', bar);
            """.trimIndent(),
        )

        myFixture.completeBasic()
        val items = myFixture.lookupElementStrings ?: emptyList()

        assertTrue("expected updateTrancheReferrers in $items", items.contains("updateTrancheReferrers"))
        assertTrue("expected clearReferrers in $items", items.contains("clearReferrers"))
    }

    fun testNoMethodCompletionForNonMatchingCall() {
        myFixture.configureByText(
            "plugin.js",
            """
            function AdvanceLender(lender, options) {}
            AdvanceLender.prototype.updateTrancheReferrers = function (${'$'}tranche) {};
            foo.somethingElse('<caret>', bar);
            """.trimIndent(),
        )

        myFixture.completeBasic()
        val items = myFixture.lookupElementStrings ?: emptyList()

        assertFalse("must not contribute for non-matching call", items.contains("updateTrancheReferrers"))
    }
}
