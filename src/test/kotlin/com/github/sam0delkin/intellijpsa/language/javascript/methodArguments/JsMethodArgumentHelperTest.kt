package com.github.sam0delkin.intellijpsa.language.javascript.methodArguments

import com.github.sam0delkin.intellijpsa.language.javascript.model.JsMethodArgumentProviderModel
import com.intellij.lang.javascript.psi.JSCallExpression
import com.intellij.lang.javascript.psi.JSLiteralExpression
import com.intellij.psi.util.PsiTreeUtil
import com.intellij.testFramework.fixtures.BasePlatformTestCase

class JsMethodArgumentHelperTest : BasePlatformTestCase() {
    fun testResolveMethodFindsMethodOnlyDefinedViaExtend() {
        myFixture.configureByText(
            "plugin.js",
            """
            function AdvanceLender(lender, options) {}
            ${'$'}.extend(AdvanceLender.prototype, { extendedOnlyMethod: function (a) {} });
            foo.advanceLender('extendedOnlyMethod', bar);
            """.trimIndent(),
        )

        val provider =
            JsMethodArgumentProviderModel().apply {
                referenceName = "advanceLender"
                `class` = "AdvanceLender"
                methodArgumentIndex = 0
                argumentsOffset = 1
            }
        val literal = myFixture.findElementByText("'extendedOnlyMethod'", JSLiteralExpression::class.java)!!

        val match = JsMethodArgumentHelper.findProviderForMethodNameElement(literal, listOf(provider))

        assertNotNull("Method defined only via \$.extend(Class.prototype, {...}) should still resolve", match)
        assertEquals("extendedOnlyMethod", match!!.function.name)
    }

    fun testMapArgumentsToParametersMapsTrailingArgsToRestParameter() {
        myFixture.configureByText(
            "plugin.js",
            """
            function AdvanceLender(lender, options) {}
            AdvanceLender.prototype.doWork = function (first, ...rest) {};
            foo.advanceLender('doWork', a, b, c);
            """.trimIndent(),
        )

        val call =
            PsiTreeUtil
                .findChildrenOfType(myFixture.file, JSCallExpression::class.java)
                .first { it.methodExpression?.text?.endsWith("advanceLender") == true }
        val provider =
            JsMethodArgumentProviderModel().apply {
                referenceName = "advanceLender"
                `class` = "AdvanceLender"
                methodArgumentIndex = 0
                argumentsOffset = 1
            }
        val match = JsMethodArgumentHelper.findProviderForCall(call, listOf(provider))!!

        val mappings = JsMethodArgumentHelper.mapArgumentsToParameters(call, match)

        assertEquals(3, mappings.size)
        assertEquals(listOf("first", "rest", "rest"), mappings.map { it.parameter.name })
        assertTrue(mappings[1].parameter.isRest)
        assertTrue(mappings[2].parameter.isRest)
    }

    fun testMapArgumentsToParametersSkipsExcessArgsWithoutRestParameter() {
        myFixture.configureByText(
            "plugin.js",
            """
            function AdvanceLender(lender, options) {}
            AdvanceLender.prototype.doWork = function (first) {};
            foo.advanceLender('doWork', a, b);
            """.trimIndent(),
        )

        val call =
            PsiTreeUtil
                .findChildrenOfType(myFixture.file, JSCallExpression::class.java)
                .first { it.methodExpression?.text?.endsWith("advanceLender") == true }
        val provider =
            JsMethodArgumentProviderModel().apply {
                referenceName = "advanceLender"
                `class` = "AdvanceLender"
                methodArgumentIndex = 0
                argumentsOffset = 1
            }
        val match = JsMethodArgumentHelper.findProviderForCall(call, listOf(provider))!!

        val mappings = JsMethodArgumentHelper.mapArgumentsToParameters(call, match)

        assertEquals(1, mappings.size)
        assertEquals("first", mappings[0].parameter.name)
    }

    fun testFindProviderForMethodNamePositionWhenElementIsTheLiteralItself() {
        myFixture.configureByText(
            "plugin.js",
            """
            function AdvanceLender(lender, options) {}
            foo.advanceLender('updateTrancheReferrers', bar);
            """.trimIndent(),
        )

        val provider =
            JsMethodArgumentProviderModel().apply {
                referenceName = "advanceLender"
                `class` = "AdvanceLender"
                methodArgumentIndex = 0
                argumentsOffset = 1
            }
        val literal = myFixture.findElementByText("'updateTrancheReferrers'", JSLiteralExpression::class.java)!!

        val match = JsMethodArgumentHelper.findProviderForMethodNamePosition(literal, listOf(provider))

        assertNotNull(match)
    }

    fun testFindProviderForMethodNamePositionReturnsNullForNonLiteralElement() {
        myFixture.configureByText(
            "plugin.js",
            """
            function foo() {}
            """.trimIndent(),
        )

        val element = myFixture.findElementByText("foo", com.intellij.psi.PsiElement::class.java)!!

        val match = JsMethodArgumentHelper.findProviderForMethodNamePosition(element, emptyList())

        assertNull(match)
    }

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

    fun testResolveMethodCacheNeverStoresAPluginDefinedType() {
        // This cache is keyed on the Project via CachedValuesManager, so a cached entry survives
        // indefinitely until the next PSI change - even across a plugin update. If its value type
        // were defined by this plugin (instead of a JDK type like java.util.Optional), a single
        // dynamic-dispatch resolution would keep the plugin's classloader reachable and block a
        // clean dynamic plugin reload.
        myFixture.configureByText(
            "plugin.js",
            """
            function AdvanceLender(lender, options) {}
            AdvanceLender.prototype.updateTrancheReferrers = function (${'$'}tranche, lenderData) {};
            foo.advanceLender('updateTrancheReferrers', bar);
            """.trimIndent(),
        )

        val provider =
            JsMethodArgumentProviderModel().apply {
                referenceName = "advanceLender"
                `class` = "AdvanceLender"
                methodArgumentIndex = 0
                argumentsOffset = 1
            }
        val literal = myFixture.findElementByText("'updateTrancheReferrers'", JSLiteralExpression::class.java)
        assertNotNull(literal)

        val match = JsMethodArgumentHelper.findProviderForMethodNameElement(literal!!, listOf(provider))
        assertNotNull("Setup should have resolved the method", match)

        val cache = JsMethodArgumentHelper.resolveMethodCache(project)
        assertFalse("Cache should be populated by the resolution above", cache.isEmpty())
        cache.values.forEach { value ->
            assertTrue(
                "Cached value must be a JDK type (e.g. java.util.Optional), not a class defined by this " +
                    "plugin, or it will pin the plugin's classloader and block a clean dynamic reload. Was: " +
                    value.javaClass.name,
                value.javaClass.name.startsWith("java."),
            )
        }
    }
}
