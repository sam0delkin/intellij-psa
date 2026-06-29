package com.github.sam0delkin.intellijpsa.language.javascript.methodArguments

import com.github.sam0delkin.intellijpsa.language.javascript.model.JsMethodArgumentProviderModel
import com.github.sam0delkin.intellijpsa.language.javascript.settings.JsPsaSettings
import com.github.sam0delkin.intellijpsa.settings.Settings
import com.intellij.openapi.components.service
import com.intellij.testFramework.fixtures.BasePlatformTestCase

class JsCrossFileCompletionTest : BasePlatformTestCase() {
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

    fun testCompletesMethodNamesFromOtherFile() {
        myFixture.addFileToProject(
            "jquery.advanceLender.js",
            """
            (function (${'$'}) {
                function AdvanceLender(element, options) {}
                AdvanceLender.prototype.updateTrancheReferrers = function (${'$'}tranche, lenderData) {};
                AdvanceLender.prototype.clearReferrers = function () {};
                ${'$'}.fn.advanceLender = function (option) {};
            })(jQuery);
            """.trimIndent(),
        )

        myFixture.configureByText(
            "jquery.advanceTranche.js",
            "this.\$advanceLender.advanceLender('<caret>', this.\$tranche);",
        )

        myFixture.completeBasic()
        val items = myFixture.lookupElementStrings ?: emptyList()

        assertTrue("expected updateTrancheReferrers in $items", items.contains("updateTrancheReferrers"))
        assertTrue("expected clearReferrers in $items", items.contains("clearReferrers"))
    }

    fun testMethodNamesFromOtherFile() {
        myFixture.addFileToProject(
            "jquery.advanceLender.js",
            """
            (function (${'$'}) {
                function AdvanceLender(element, options) {}
                AdvanceLender.prototype.updateTrancheReferrers = function (${'$'}tranche) {};
                AdvanceLender.prototype.clearReferrers = function () {};
            })(jQuery);
            """.trimIndent(),
        )
        myFixture.configureByText("jquery.advanceTranche.js", "var x = 1;")

        val names = JsMethodArgumentHelper.methodNames(project, "AdvanceLender")
        assertTrue("expected updateTrancheReferrers in $names", names.contains("updateTrancheReferrers"))
        assertTrue("expected clearReferrers in $names", names.contains("clearReferrers"))
    }
}
