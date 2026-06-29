package com.github.sam0delkin.intellijpsa.language.javascript.methodArguments

import com.intellij.testFramework.fixtures.BasePlatformTestCase

class JsMethodArgumentHelperTest : BasePlatformTestCase() {
    fun testMethodNamesEnumeratesPrototypeMethods() {
        myFixture.configureByText(
            "plugin.js",
            """
            function AdvanceLender(lender, options) {}
            AdvanceLender.prototype.updateTrancheReferrers = function (${'$'}tranche, lenderData) {};
            AdvanceLender.prototype.clearReferrers = function () {};
            ${'$'}.extend(AdvanceLender.prototype, { extendedMethod: function () {} });
            """.trimIndent(),
        )

        val names = JsMethodArgumentHelper.methodNames(project, "AdvanceLender")

        assertTrue("expected updateTrancheReferrers in $names", names.contains("updateTrancheReferrers"))
        assertTrue("expected clearReferrers in $names", names.contains("clearReferrers"))
        assertTrue("expected extendedMethod in $names", names.contains("extendedMethod"))
    }
}
