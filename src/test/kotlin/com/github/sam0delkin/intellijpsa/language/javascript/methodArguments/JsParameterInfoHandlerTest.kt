package com.github.sam0delkin.intellijpsa.language.javascript.methodArguments

import com.github.sam0delkin.intellijpsa.language.javascript.model.JsMethodArgumentProviderModel
import com.github.sam0delkin.intellijpsa.language.javascript.settings.JsPsaSettings
import com.github.sam0delkin.intellijpsa.settings.Settings
import com.intellij.lang.javascript.JSTokenTypes
import com.intellij.lang.javascript.psi.JSArgumentList
import com.intellij.lang.javascript.psi.JSCallExpression
import com.intellij.lang.javascript.psi.JSFunction
import com.intellij.lang.javascript.psi.JSParameterListElement
import com.intellij.openapi.components.service
import com.intellij.psi.util.PsiTreeUtil
import com.intellij.testFramework.fixtures.BasePlatformTestCase
import com.intellij.testFramework.utils.parameterInfo.MockCreateParameterInfoContext
import com.intellij.testFramework.utils.parameterInfo.MockParameterInfoUIContext
import com.intellij.testFramework.utils.parameterInfo.MockUpdateParameterInfoContext

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

    private fun configureMatchingCall(): JSArgumentList {
        myFixture.configureByText(
            "plugin.js",
            """
            function AdvanceLender(lender, options) {}
            AdvanceLender.prototype.updateTrancheReferrers = function (${'$'}tranche, lenderData) {};
            foo.advanceLender('updateTrancheReferrers', ba<caret>r);
            """.trimIndent(),
        )

        return PsiTreeUtil.findChildOfType(myFixture.file, JSCallExpression::class.java)!!.argumentList!!
    }

    // Covers the trivial one-liner overrides that ParameterInfoHandlerWithTabActionSupport
    // requires: getArgumentListClass, getActualParameters, getActualParameterDelimiterType,
    // getActualParametersRBraceType, getArgumentListAllowedParentClasses, getArgListStopSearchClasses.
    fun testTrivialOverridesReturnExpectedValues() {
        val argumentList = configureMatchingCall()
        val handler = JsParameterInfoHandler()

        assertEquals(JSArgumentList::class.java, handler.getArgumentListClass())
        assertEquals(argumentList.arguments.toList(), handler.getActualParameters(argumentList).toList())
        assertEquals(JSTokenTypes.COMMA, handler.getActualParameterDelimiterType())
        assertEquals(JSTokenTypes.RPAR, handler.getActualParametersRBraceType())
        assertEquals(setOf(JSCallExpression::class.java), handler.getArgumentListAllowedParentClasses())
        assertEquals(emptySet<Class<*>>(), handler.getArgListStopSearchClasses())
    }

    // showParameterInfo() just delegates to context.showHint(...); MockCreateParameterInfoContext's
    // showHint() is a no-op, so this only asserts the call doesn't throw.
    fun testShowParameterInfoDoesNotThrow() {
        val argumentList = configureMatchingCall()
        val handler = JsParameterInfoHandler()
        val context = MockCreateParameterInfoContext(myFixture.editor, myFixture.file)

        handler.showParameterInfo(argumentList, context)
    }

    fun testFindElementForUpdatingParameterInfoAndUpdateParameterInfoResolveCurrentParameterIndex() {
        configureMatchingCall()
        val handler = JsParameterInfoHandler()
        val context = MockUpdateParameterInfoContext(myFixture.editor, myFixture.file)

        val argumentList = handler.findElementForUpdatingParameterInfo(context)

        assertNotNull(argumentList)
        handler.updateParameterInfo(argumentList!!, context)

        // The caret sits in the call's 2nd argument (callArgIndex=1, 0-based); the configured
        // provider's argumentsOffset=1 shifts that back to parameter index 0 ($tranche).
        assertEquals(0, context.currentParameter)
    }

    fun testFindElementForUpdatingParameterInfoReturnsNullForNonMatchingCall() {
        myFixture.configureByText(
            "plugin.js",
            """
            function AdvanceLender(lender, options) {}
            AdvanceLender.prototype.updateTrancheReferrers = function (${'$'}tranche) {};
            foo.somethingElse('updateTrancheReferrers', ba<caret>r);
            """.trimIndent(),
        )

        val handler = JsParameterInfoHandler()
        val context = MockUpdateParameterInfoContext(myFixture.editor, myFixture.file)

        assertNull(handler.findElementForUpdatingParameterInfo(context))
    }

    // Regression/coverage test for currentParameterIndex()'s `findMatch(argumentList) ?: return
    // callArgIndex` fallback: findElementForUpdatingParameterInfo() only succeeds while a match
    // exists, but the provider list can change (e.g. a settings update) before
    // updateParameterInfo() runs its own, separate findMatch() call.
    fun testUpdateParameterInfoFallsBackToCallArgIndexWhenMatchDisappears() {
        configureMatchingCall()
        val handler = JsParameterInfoHandler()
        val context = MockUpdateParameterInfoContext(myFixture.editor, myFixture.file)
        val argumentList = handler.findElementForUpdatingParameterInfo(context)
        assertNotNull(argumentList)

        project.service<JsPsaSettings>().methodArgumentProviders = arrayListOf()
        handler.updateParameterInfo(argumentList!!, context)

        // No provider match anymore, so no argumentsOffset shift is applied - callArgIndex (1) is
        // returned as-is.
        assertEquals(1, context.currentParameter)
    }

    fun testFindElementForParameterInfoReturnsNullWhenArgumentListParentIsNotACallExpression() {
        myFixture.configureByText(
            "plugin.js",
            """
            function AdvanceLender(lender, options) {}
            AdvanceLender.prototype.updateTrancheReferrers = function (${'$'}tranche, lenderData) {};
            new AdvanceLender('updateTrancheReferrers', ba<caret>r);
            """.trimIndent(),
        )

        val handler = JsParameterInfoHandler()
        val context = MockCreateParameterInfoContext(myFixture.editor, myFixture.file)

        assertNull(handler.findElementForParameterInfo(context))
    }

    fun testFindElementForParameterInfoReturnsNullWhenPluginDisabled() {
        configureMatchingCall()
        project.service<Settings>().pluginEnabled = false
        val handler = JsParameterInfoHandler()
        val context = MockCreateParameterInfoContext(myFixture.editor, myFixture.file)

        assertNull(handler.findElementForParameterInfo(context))
    }

    fun testFindElementForParameterInfoReturnsNullWhenJsExtensionDisabled() {
        configureMatchingCall()
        project.service<JsPsaSettings>().enabled = false
        val handler = JsParameterInfoHandler()
        val context = MockCreateParameterInfoContext(myFixture.editor, myFixture.file)

        assertNull(handler.findElementForParameterInfo(context))
    }

    fun testFindElementForParameterInfoReturnsNullWhenProvidersAreNull() {
        configureMatchingCall()
        project.service<JsPsaSettings>().methodArgumentProviders = null
        val handler = JsParameterInfoHandler()
        val context = MockCreateParameterInfoContext(myFixture.editor, myFixture.file)

        assertNull(handler.findElementForParameterInfo(context))
    }

    fun testFindElementForParameterInfoReturnsNullWhenProvidersAreEmpty() {
        configureMatchingCall()
        project.service<JsPsaSettings>().methodArgumentProviders = arrayListOf()
        val handler = JsParameterInfoHandler()
        val context = MockCreateParameterInfoContext(myFixture.editor, myFixture.file)

        assertNull(handler.findElementForParameterInfo(context))
    }

    // Covers updateUI()/renderParameter() across isRest, isOptional (both explicit "?" and the
    // implicit optionality a default value gives a TypeScript parameter), a type annotation and an
    // initializer - a plain .js file's ES6 dialect doesn't parse type annotations, so this uses
    // TypeScript syntax instead. Bypasses the method-argument-provider matching machinery entirely
    // by calling updateUI() directly with a hand-picked parameter list.
    fun testUpdateUiRendersRestOptionalTypeAndInitializerParameters() {
        myFixture.configureByText(
            "test.ts",
            "function f(d, a: number, b?: string, c: number = 5, ...rest: any[]) {}",
        )
        val function = PsiTreeUtil.findChildOfType(myFixture.file, JSFunction::class.java)!!
        val parameters = function.parameterList!!.parameters

        val handler = JsParameterInfoHandler()
        val context = MockParameterInfoUIContext(function)
        context.currentParameterIndex = 3

        handler.updateUI(parameters, context)

        // Note: updateUI() only ever sets isUIComponentEnabled explicitly to false (the
        // null/empty-parameters guard below) - the success path relies on
        // setupUIComponentPresentation() itself to enable the component, which
        // MockParameterInfoUIContext's implementation doesn't reflect back into
        // isUIComponentEnabled, so that flag isn't asserted on here.
        // "d" (untyped, no default, leading so it doesn't affect the optionality inference of the
        // parameters after it) exercises renderParameter()'s null-skip branches for typeElement and
        // initializer, alongside the populated branches from the other parameters.
        assertEquals("d, a: number, b?: string, c?: number = 5, ...rest: any[]", context.text)
        // Highlighted range should cover the 4th rendered parameter ("c?: number = 5").
        assertEquals(context.text.indexOf("c?: number = 5"), context.highlightStart)
        assertEquals(context.text.indexOf("c?: number = 5") + "c?: number = 5".length, context.highlightEnd)
    }

    // Regression/coverage test for findArgumentList()'s
    // `PsiTreeUtil.getParentOfType(leaf, JSArgumentList::class.java) ?: return null` guard: a
    // caret position with no enclosing call argument list at all.
    fun testFindElementForParameterInfoReturnsNullWhenNoEnclosingArgumentList() {
        myFixture.configureByText("plugin.js", "var x = <caret>1;")
        val handler = JsParameterInfoHandler()
        val context = MockCreateParameterInfoContext(myFixture.editor, myFixture.file)

        assertNull(handler.findElementForParameterInfo(context))
    }

    fun testUpdateUiDisablesComponentForNullOrEmptyParameters() {
        myFixture.configureByText("plugin.js", "foo();")
        val handler = JsParameterInfoHandler()
        val function = myFixture.file

        val emptyContext = MockParameterInfoUIContext(function)
        handler.updateUI(arrayOf(), emptyContext)
        assertFalse(emptyContext.isUIComponentEnabled)

        val nullContext = MockParameterInfoUIContext(function)
        handler.updateUI(null, nullContext)
        assertFalse(nullContext.isUIComponentEnabled)
    }
}
