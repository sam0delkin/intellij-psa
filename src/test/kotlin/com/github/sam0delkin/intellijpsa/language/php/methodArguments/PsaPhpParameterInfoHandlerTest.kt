package com.github.sam0delkin.intellijpsa.language.php.methodArguments

import com.github.sam0delkin.intellijpsa.language.php.model.MethodArgumentProviderModel
import com.github.sam0delkin.intellijpsa.language.php.settings.PhpPsaSettings
import com.github.sam0delkin.intellijpsa.settings.Settings
import com.intellij.openapi.components.service
import com.intellij.testFramework.fixtures.BasePlatformTestCase
import com.intellij.testFramework.utils.parameterInfo.MockCreateParameterInfoContext
import com.intellij.testFramework.utils.parameterInfo.MockParameterInfoUIContext
import com.intellij.testFramework.utils.parameterInfo.MockUpdateParameterInfoContext
import com.jetbrains.php.lang.lexer.PhpTokenTypes
import com.jetbrains.php.lang.psi.elements.ArrayCreationExpression
import com.jetbrains.php.lang.psi.elements.Parameter
import com.jetbrains.php.lang.psi.elements.ParameterList

class PsaPhpParameterInfoHandlerTest : BasePlatformTestCase() {
    private val code =
        """
        <?php
        class QueueManager {
            public function executeServiceMethod(${'$'}service, ${'$'}method, ${'$'}args) {}
        }
        class MyService {
            public function doWork(${'$'}a, ${'$'}b = 'x') {}
        }
        ${'$'}queue = new QueueManager();
        ${'$'}queue->executeServiceMethod('MyService', 'doWork', [1, <caret>2]);
        """.trimIndent()

    private fun provider() =
        MethodArgumentProviderModel().apply {
            `class` = "QueueManager"
            method = "executeServiceMethod"
            classArgumentIndex = 0
            methodArgumentIndex = 1
            argumentsArgumentIndex = 2
        }

    private fun enablePlugin() {
        project.service<Settings>().pluginEnabled = true
        project.service<PhpPsaSettings>().enabled = true
        project.service<PhpPsaSettings>().methodArgumentProviders = arrayListOf(provider())
    }

    fun testSimpleDelegationMethods() {
        val handler = PsaPhpParameterInfoHandler()

        assertEquals(ArrayCreationExpression::class.java, handler.getArgumentListClass())
        assertEquals(PhpTokenTypes.opCOMMA, handler.getActualParameterDelimiterType())
        assertEquals(PhpTokenTypes.chRBRACKET, handler.getActualParametersRBraceType())
        assertEquals(setOf(ParameterList::class.java), handler.getArgumentListAllowedParentClasses())
        assertEquals(emptySet<Class<*>>(), handler.getArgListStopSearchClasses())
    }

    fun testGetActualParametersFiltersDelimitersAndWhitespace() {
        myFixture.configureByText("test.php", code)
        val array =
            com.intellij.psi.util.PsiTreeUtil
                .findChildrenOfType(myFixture.file, ArrayCreationExpression::class.java)
                .first()
        val handler = PsaPhpParameterInfoHandler()

        val values = handler.getActualParameters(array)

        assertEquals(2, values.size)
        assertEquals("1", values[0].text)
        assertEquals("2", values[1].text)
    }

    fun testFindElementForParameterInfoReturnsNullWhenPluginDisabled() {
        myFixture.configureByText("test.php", code)
        project.service<Settings>().pluginEnabled = false
        project.service<PhpPsaSettings>().enabled = true
        project.service<PhpPsaSettings>().methodArgumentProviders = arrayListOf(provider())
        val handler = PsaPhpParameterInfoHandler()
        val context = MockCreateParameterInfoContext(myFixture.editor, myFixture.file)

        assertNull(handler.findElementForParameterInfo(context))
    }

    fun testFindElementForParameterInfoReturnsNullWhenExtensionDisabled() {
        myFixture.configureByText("test.php", code)
        project.service<Settings>().pluginEnabled = true
        project.service<PhpPsaSettings>().enabled = false
        project.service<PhpPsaSettings>().methodArgumentProviders = arrayListOf(provider())
        val handler = PsaPhpParameterInfoHandler()
        val context = MockCreateParameterInfoContext(myFixture.editor, myFixture.file)

        assertNull(handler.findElementForParameterInfo(context))
    }

    fun testFindElementForParameterInfoReturnsNullWhenNoProviders() {
        myFixture.configureByText("test.php", code)
        project.service<Settings>().pluginEnabled = true
        project.service<PhpPsaSettings>().enabled = true
        project.service<PhpPsaSettings>().methodArgumentProviders = arrayListOf()
        val handler = PsaPhpParameterInfoHandler()
        val context = MockCreateParameterInfoContext(myFixture.editor, myFixture.file)

        assertNull(handler.findElementForParameterInfo(context))
    }

    fun testFindElementForParameterInfoReturnsNullWhenNoMatchingProvider() {
        myFixture.configureByText("test.php", code)
        project.service<Settings>().pluginEnabled = true
        project.service<PhpPsaSettings>().enabled = true
        project.service<PhpPsaSettings>().methodArgumentProviders =
            arrayListOf(
                MethodArgumentProviderModel().apply {
                    `class` = "SomeOtherClass"
                    method = "someOtherMethod"
                    classArgumentIndex = 0
                    methodArgumentIndex = 1
                    argumentsArgumentIndex = 2
                },
            )
        val handler = PsaPhpParameterInfoHandler()
        val context = MockCreateParameterInfoContext(myFixture.editor, myFixture.file)

        assertNull(handler.findElementForParameterInfo(context))
    }

    fun testFindElementForParameterInfoMatchesAndSetsItemsToShow() {
        myFixture.configureByText("test.php", code)
        enablePlugin()
        val handler = PsaPhpParameterInfoHandler()
        val context = MockCreateParameterInfoContext(myFixture.editor, myFixture.file)

        val array = handler.findElementForParameterInfo(context)

        assertNotNull(array)
        val items = context.itemsToShow
        assertNotNull(items)
        assertEquals(1, items!!.size)

        @Suppress("UNCHECKED_CAST")
        val parameters = items[0] as Array<Parameter>
        assertEquals(2, parameters.size)
        assertEquals("a", parameters[0].name)
        assertEquals("b", parameters[1].name)
    }

    fun testShowParameterInfoShowsHintWithoutThrowing() {
        myFixture.configureByText("test.php", code)
        enablePlugin()
        val handler = PsaPhpParameterInfoHandler()
        val context = MockCreateParameterInfoContext(myFixture.editor, myFixture.file)
        val array = handler.findElementForParameterInfo(context)!!

        handler.showParameterInfo(array, context)
    }

    fun testFindElementForUpdatingParameterInfoReturnsNullWithoutMatch() {
        myFixture.configureByText("test.php", code)
        project.service<Settings>().pluginEnabled = false
        val handler = PsaPhpParameterInfoHandler()
        val context = MockUpdateParameterInfoContext(myFixture.editor, myFixture.file)

        assertNull(handler.findElementForUpdatingParameterInfo(context))
    }

    fun testUpdateParameterInfoSetsCurrentParameterWithOffset() {
        myFixture.configureByText("test.php", code)
        enablePlugin()
        val handler = PsaPhpParameterInfoHandler()
        val context = MockUpdateParameterInfoContext(myFixture.editor, myFixture.file)
        val array = handler.findElementForUpdatingParameterInfo(context)!!

        handler.updateParameterInfo(array, context)

        // Caret sits on the second array value (index 1); the matched provider's
        // argumentsOffset defaults to 0, so the resulting parameter index is 1.
        assertEquals(1, context.currentParameter)
    }

    fun testUpdateUIDisablesComponentWhenParametersAreNull() {
        val handler = PsaPhpParameterInfoHandler()
        myFixture.configureByText("test.php", code)
        val array =
            com.intellij.psi.util.PsiTreeUtil
                .findChildrenOfType(myFixture.file, ArrayCreationExpression::class.java)
                .first()
        val uiContext = MockParameterInfoUIContext(array)

        handler.updateUI(null, uiContext)

        assertFalse(uiContext.isUIComponentEnabled)
    }

    fun testUpdateUIDisablesComponentWhenParametersAreEmpty() {
        val handler = PsaPhpParameterInfoHandler()
        myFixture.configureByText("test.php", code)
        val array =
            com.intellij.psi.util.PsiTreeUtil
                .findChildrenOfType(myFixture.file, ArrayCreationExpression::class.java)
                .first()
        val uiContext = MockParameterInfoUIContext(array)

        handler.updateUI(arrayOf(), uiContext)

        assertFalse(uiContext.isUIComponentEnabled)
    }

    fun testUpdateUIBuildsPresentationWithHighlightAndDefaultValue() {
        myFixture.configureByText("test.php", code)
        enablePlugin()
        val handler = PsaPhpParameterInfoHandler()
        val context = MockCreateParameterInfoContext(myFixture.editor, myFixture.file)
        handler.findElementForParameterInfo(context)

        @Suppress("UNCHECKED_CAST")
        val parameters = context.itemsToShow!![0] as Array<Parameter>
        val array =
            com.intellij.psi.util.PsiTreeUtil
                .findChildrenOfType(myFixture.file, ArrayCreationExpression::class.java)
                .first()
        val uiContext = MockParameterInfoUIContext(array)
        uiContext.currentParameterIndex = 1

        handler.updateUI(parameters, uiContext)

        // MockParameterInfoUIContext.setupUIComponentPresentation only stores text/highlight
        // fields - it never flips the "enabled" flag (that only happens via the disabled-path
        // setUIComponentEnabled(false) call, exercised in the tests above), so the meaningful
        // assertions here are about the built text and highlight range.
        assertTrue(uiContext.text.contains("\$a"))
        assertTrue(uiContext.text.contains("\$b"))
        assertTrue(uiContext.text.contains("= 'x'"))
        assertTrue(uiContext.highlightStart >= 0)
        assertTrue(uiContext.highlightEnd > uiContext.highlightStart)
    }
}
