package com.github.sam0delkin.intellijpsa.language.php.methodArguments

import com.github.sam0delkin.intellijpsa.language.php.model.MethodArgumentProviderModel
import com.intellij.psi.util.PsiTreeUtil
import com.intellij.testFramework.fixtures.BasePlatformTestCase
import com.jetbrains.php.lang.psi.elements.ArrayCreationExpression
import com.jetbrains.php.lang.psi.elements.StringLiteralExpression

class PsaPhpMethodArgumentHelperTest : BasePlatformTestCase() {

    fun testFindProviderForMethodNameElement_callablePattern_matchesMethodString() {
        myFixture.configureByText(
            "Dispatcher.php",
            """
            <?php
            class ServiceMethodMessage {}
            class AccountStatsManager {
                public function updateStats() {}
            }
            new ServiceMethodMessage([AccountStatsManager::class, 'updateStats'], []);
            """.trimIndent(),
        )

        val providers = listOf(
            MethodArgumentProviderModel().apply {
                `class` = "ServiceMethodMessage"
                method = "__construct"
                callableArgumentIndex = 0
                argumentsArgumentIndex = 1
            },
        )

        val element = myFixture.findElementByText("'updateStats'", StringLiteralExpression::class.java)
        assertNotNull("'updateStats' element not found", element)

        val match = PsaPhpMethodArgumentHelper.findProviderForMethodNameElement(element!!, providers)
        assertNotNull("Expected a provider match for 'updateStats'", match)
        assertEquals("updateStats", match!!.method.name)
    }

    fun testFindProviderForMethodNameElement_callablePattern_classNameStringNotMatched() {
        myFixture.configureByText(
            "Dispatcher.php",
            """
            <?php
            class ServiceMethodMessage {}
            class AccountStatsManager {
                public function updateStats() {}
            }
            new ServiceMethodMessage([AccountStatsManager::class, 'updateStats'], []);
            """.trimIndent(),
        )

        val providers = listOf(
            MethodArgumentProviderModel().apply {
                `class` = "ServiceMethodMessage"
                method = "__construct"
                callableArgumentIndex = 0
                argumentsArgumentIndex = 1
            },
        )

        val classEl = myFixture.findElementByText("AccountStatsManager", com.intellij.psi.PsiElement::class.java)
        assertNotNull(classEl)
        val match = PsaPhpMethodArgumentHelper.findProviderForMethodNameElement(classEl!!, providers)
        assertNull("Class name element should not match as method name", match)
    }

    fun testFindProviderForArgumentsArray_callablePattern_matchesArgumentsArray() {
        myFixture.configureByText(
            "Dispatcher.php",
            """
            <?php
            class ServiceMethodMessage {}
            class AccountStatsManager {
                public function updateStats(${'$'}account, ${'$'}flush) {}
            }
            new ServiceMethodMessage([AccountStatsManager::class, 'updateStats'], [${'$'}account, true]);
            """.trimIndent(),
        )

        val providers = listOf(
            MethodArgumentProviderModel().apply {
                `class` = "ServiceMethodMessage"
                method = "__construct"
                callableArgumentIndex = 0
                argumentsArgumentIndex = 1
            },
        )

        val arrays = PsiTreeUtil.findChildrenOfType(myFixture.file, ArrayCreationExpression::class.java).toList()
        val argsArray = arrays.firstOrNull { it.text.contains("\$account") }
        assertNotNull("Arguments array not found", argsArray)

        val match = PsaPhpMethodArgumentHelper.findProviderForArgumentsArray(argsArray!!, providers)
        assertNotNull("Expected provider match for arguments array", match)
        assertEquals("updateStats", match!!.method.name)
    }

    fun testFindProviderForArgumentsArray_callablePattern_callableArrayNotMatched() {
        myFixture.configureByText(
            "Dispatcher.php",
            """
            <?php
            class ServiceMethodMessage {}
            class AccountStatsManager {
                public function updateStats(${'$'}account) {}
            }
            new ServiceMethodMessage([AccountStatsManager::class, 'updateStats'], [${'$'}account]);
            """.trimIndent(),
        )

        val providers = listOf(
            MethodArgumentProviderModel().apply {
                `class` = "ServiceMethodMessage"
                method = "__construct"
                callableArgumentIndex = 0
                argumentsArgumentIndex = 1
            },
        )

        val arrays = PsiTreeUtil.findChildrenOfType(myFixture.file, ArrayCreationExpression::class.java).toList()
        val callableArray = arrays.firstOrNull { it.text.contains("AccountStatsManager") }
        assertNotNull("Callable array not found", callableArray)

        val match = PsaPhpMethodArgumentHelper.findProviderForArgumentsArray(callableArray!!, providers)
        assertNull("Callable array should not match as arguments array", match)
    }

    fun testFindProviderForMethodNameElement_separateArgs_matchesMethodString() {
        myFixture.configureByText(
            "Queue.php",
            """
            <?php
            class QueueManager {
                public function executeServiceMethod(${'$'}service, ${'$'}method, ${'$'}args) {}
            }
            class MyService {
                public function doWork(${'$'}a, ${'$'}b) {}
            }
            ${'$'}queue = new QueueManager();
            ${'$'}queue->executeServiceMethod('MyService', 'doWork', [1, 2]);
            """.trimIndent(),
        )

        val providers = listOf(
            MethodArgumentProviderModel().apply {
                `class` = "QueueManager"
                method = "executeServiceMethod"
                classArgumentIndex = 0
                methodArgumentIndex = 1
                argumentsArgumentIndex = 2
            },
        )

        val element = myFixture.findElementByText("'doWork'", StringLiteralExpression::class.java)
        assertNotNull("'doWork' element not found", element)

        val match = PsaPhpMethodArgumentHelper.findProviderForMethodNameElement(element!!, providers)
        assertNotNull("Expected a provider match for 'doWork'", match)
        assertEquals("doWork", match!!.method.name)
    }

    fun testFindProviderForArgumentsArray_separateArgs_matchesArgumentsArray() {
        myFixture.configureByText(
            "Queue.php",
            """
            <?php
            class QueueManager {
                public function executeServiceMethod(${'$'}service, ${'$'}method, ${'$'}args) {}
            }
            class MyService {
                public function doWork(${'$'}a, ${'$'}b) {}
            }
            ${'$'}queue = new QueueManager();
            ${'$'}queue->executeServiceMethod('MyService', 'doWork', [1, 2]);
            """.trimIndent(),
        )

        val providers = listOf(
            MethodArgumentProviderModel().apply {
                `class` = "QueueManager"
                method = "executeServiceMethod"
                classArgumentIndex = 0
                methodArgumentIndex = 1
                argumentsArgumentIndex = 2
            },
        )

        val arrays = PsiTreeUtil.findChildrenOfType(myFixture.file, ArrayCreationExpression::class.java).toList()
        val argsArray = arrays.firstOrNull { it.text.contains("1") && it.text.contains("2") }
        assertNotNull("Arguments array [1, 2] not found", argsArray)

        val match = PsaPhpMethodArgumentHelper.findProviderForArgumentsArray(argsArray!!, providers)
        assertNotNull("Expected provider match for arguments array", match)
        assertEquals("doWork", match!!.method.name)
    }

    fun testFindProviderForMethodNameElement_noProviders_returnsNull() {
        myFixture.configureByText(
            "test.php",
            "<?php class Foo { public function bar() {} } new Foo(['Bar', 'baz'], []);",
        )

        val element = myFixture.findElementByText("'baz'", StringLiteralExpression::class.java)
        assertNotNull(element)
        val match = PsaPhpMethodArgumentHelper.findProviderForMethodNameElement(element!!, emptyList())
        assertNull(match)
    }

    fun testFindProviderForMethodNameElement_wrongOuterMethod_returnsNull() {
        myFixture.configureByText(
            "test.php",
            """
            <?php
            class ServiceMethodMessage {}
            class Foo { public function bar() {} }
            somethingElse([Foo::class, 'bar'], []);
            """.trimIndent(),
        )

        val providers = listOf(
            MethodArgumentProviderModel().apply {
                `class` = "ServiceMethodMessage"
                method = "__construct"
                callableArgumentIndex = 0
                argumentsArgumentIndex = 1
            },
        )

        val element = myFixture.findElementByText("'bar'", StringLiteralExpression::class.java)
        assertNotNull(element)
        val match = PsaPhpMethodArgumentHelper.findProviderForMethodNameElement(element!!, providers)
        assertNull("Should not match — outer call is not 'new ServiceMethodMessage'", match)
    }

    fun testMapArgumentsToParameters_noOffset_mapsByPosition() {
        myFixture.configureByText(
            "Call.php",
            """
            <?php
            class ServiceMethodMessage {}
            class AccountStatsManager {
                public function updateStats(${'$'}account, ${'$'}flush) {}
            }
            new ServiceMethodMessage([AccountStatsManager::class, 'updateStats'], [${'$'}account, true]);
            """.trimIndent(),
        )

        val providers = callableProviders(offset = 0)
        val array = argumentsArray("true")
        val match = PsaPhpMethodArgumentHelper.findProviderForArgumentsArray(array, providers)
        assertNotNull(match)

        val mappings = PsaPhpMethodArgumentHelper.mapArgumentsToParameters(array, match!!)
        assertEquals(2, mappings.size)
        assertEquals("account", mappings[0].parameter.name)
        assertEquals(0, mappings[0].parameterIndex)
        assertEquals("flush", mappings[1].parameter.name)
        assertEquals(1, mappings[1].parameterIndex)
    }

    fun testMapArgumentsToParameters_withOffset_skipsLeadingParameters() {
        myFixture.configureByText(
            "Call.php",
            """
            <?php
            class ServiceMethodMessage {}
            class AccountStatsManager {
                public function updateStats(${'$'}requestId, ${'$'}account, ${'$'}flush) {}
            }
            new ServiceMethodMessage([AccountStatsManager::class, 'updateStats'], [${'$'}account, true]);
            """.trimIndent(),
        )

        val providers = callableProviders(offset = 1)
        val array = argumentsArray("true")
        val match = PsaPhpMethodArgumentHelper.findProviderForArgumentsArray(array, providers)
        assertNotNull(match)

        val mappings = PsaPhpMethodArgumentHelper.mapArgumentsToParameters(array, match!!)
        assertEquals(2, mappings.size)
        assertEquals("account", mappings[0].parameter.name)
        assertEquals(1, mappings[0].parameterIndex)
        assertEquals("flush", mappings[1].parameter.name)
        assertEquals(2, mappings[1].parameterIndex)
    }

    fun testMapArgumentsToParameters_variadic_repeatsLastParameter() {
        myFixture.configureByText(
            "Call.php",
            """
            <?php
            class ServiceMethodMessage {}
            class AccountStatsManager {
                public function updateStats(${'$'}account, ...${'$'}rest) {}
            }
            new ServiceMethodMessage([AccountStatsManager::class, 'updateStats'], [${'$'}account, 1, 2, 3]);
            """.trimIndent(),
        )

        val providers = callableProviders(offset = 0)
        val array = argumentsArray("1, 2, 3")
        val match = PsaPhpMethodArgumentHelper.findProviderForArgumentsArray(array, providers)
        assertNotNull(match)

        val mappings = PsaPhpMethodArgumentHelper.mapArgumentsToParameters(array, match!!)
        assertEquals(4, mappings.size)
        assertEquals(listOf("account", "rest", "rest", "rest"), mappings.map { it.parameter.name })
        assertTrue("Trailing values map to the variadic parameter", mappings[3].parameter.isVariadic)
    }

    fun testMapArgumentsToParameters_extraArguments_areSkipped() {
        myFixture.configureByText(
            "Call.php",
            """
            <?php
            class ServiceMethodMessage {}
            class AccountStatsManager {
                public function updateStats(${'$'}account) {}
            }
            new ServiceMethodMessage([AccountStatsManager::class, 'updateStats'], [${'$'}account, true]);
            """.trimIndent(),
        )

        val providers = callableProviders(offset = 0)
        val array = argumentsArray("true")
        val match = PsaPhpMethodArgumentHelper.findProviderForArgumentsArray(array, providers)
        assertNotNull(match)

        val mappings = PsaPhpMethodArgumentHelper.mapArgumentsToParameters(array, match!!)
        assertEquals("Extra value without a parameter is skipped", 1, mappings.size)
        assertEquals("account", mappings[0].parameter.name)
    }

    private fun callableProviders(offset: Int) =
        listOf(
            MethodArgumentProviderModel().apply {
                `class` = "ServiceMethodMessage"
                method = "__construct"
                callableArgumentIndex = 0
                argumentsArgumentIndex = 1
                argumentsOffset = offset
            },
        )

    private fun argumentsArray(containing: String): ArrayCreationExpression =
        PsiTreeUtil
            .findChildrenOfType(myFixture.file, ArrayCreationExpression::class.java)
            .first { it.text.contains(containing) && !it.text.contains("::class") }
}
