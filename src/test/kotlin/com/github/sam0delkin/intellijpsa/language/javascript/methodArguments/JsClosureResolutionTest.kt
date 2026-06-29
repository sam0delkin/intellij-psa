package com.github.sam0delkin.intellijpsa.language.javascript.methodArguments

import com.github.sam0delkin.intellijpsa.language.javascript.model.JsMethodArgumentProviderModel
import com.github.sam0delkin.intellijpsa.language.javascript.settings.JsPsaSettings
import com.github.sam0delkin.intellijpsa.settings.Settings
import com.intellij.lang.javascript.psi.JSFunction
import com.intellij.lang.javascript.psi.JSLiteralExpression
import com.intellij.openapi.components.service
import com.intellij.testFramework.fixtures.BasePlatformTestCase

class JsClosureResolutionTest : BasePlatformTestCase() {
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

    private val pluginSource =
        """
        (function (${'$'}) {
            function AdvanceLender(element, options) {}
            AdvanceLender.prototype.updateTrancheReferrers = function (${'$'}tranche, lenderData) {};
            AdvanceLender.prototype.clearReferrers = function () {};
            ${'$'}.fn.advanceLender = function (option) {
                return this.each(function () {
                    var data = new AdvanceLender(this, option);
                    if (typeof option === 'string') data[option]();
                });
            };
        })(jQuery);
        """.trimIndent()

    fun testReferenceResolvesForClosureLocalClass() {
        myFixture.configureByText(
            "jquery.advanceLender.js",
            "$pluginSource\nfoo.advanceLender('updateTrancheReferrers', bar);",
        )

        val literal = myFixture.findElementByText("'updateTrancheReferrers'", JSLiteralExpression::class.java)
        val ref = literal!!.references.filterIsInstance<JsMethodReference>().firstOrNull()
        assertNotNull("reference should be created for closure-local class", ref)

        val resolved = ref!!.resolve()
        assertTrue("should resolve to a JS function", resolved is JSFunction)
        assertEquals("updateTrancheReferrers", (resolved as JSFunction).name)
    }

    fun testMethodNamesEnumeratesClosureLocalPrototype() {
        myFixture.configureByText("jquery.advanceLender.js", pluginSource)

        val names = JsMethodArgumentHelper.methodNames(project, "AdvanceLender")

        assertTrue("expected updateTrancheReferrers in $names", names.contains("updateTrancheReferrers"))
        assertTrue("expected clearReferrers in $names", names.contains("clearReferrers"))
    }
}
