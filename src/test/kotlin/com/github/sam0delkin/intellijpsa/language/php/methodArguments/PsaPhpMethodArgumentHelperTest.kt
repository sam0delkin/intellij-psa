package com.github.sam0delkin.intellijpsa.language.php.methodArguments

import com.github.sam0delkin.intellijpsa.language.php.model.MethodArgumentProviderModel
import com.intellij.psi.PsiElement
import com.intellij.psi.util.PsiTreeUtil
import com.intellij.testFramework.fixtures.BasePlatformTestCase
import com.jetbrains.php.lang.psi.elements.ArrayCreationExpression
import com.jetbrains.php.lang.psi.elements.StringLiteralExpression

class PsaPhpMethodArgumentHelperTest : BasePlatformTestCase() {
    fun testFindProviderForMethodNameElementCallablePatternMatchesMethodString() {
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

        val providers =
            listOf(
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

    fun testFindProviderForMethodNameElementCallablePatternClassNameStringNotMatched() {
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

        val providers =
            listOf(
                MethodArgumentProviderModel().apply {
                    `class` = "ServiceMethodMessage"
                    method = "__construct"
                    callableArgumentIndex = 0
                    argumentsArgumentIndex = 1
                },
            )

        val classEl = myFixture.findElementByText("AccountStatsManager", PsiElement::class.java)
        assertNotNull(classEl)
        val match = PsaPhpMethodArgumentHelper.findProviderForMethodNameElement(classEl!!, providers)
        assertNull("Class name element should not match as method name", match)
    }

    fun testFindProviderForArgumentsArrayCallablePatternMatchesArgumentsArray() {
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

        val providers =
            listOf(
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

    fun testFindProviderForArgumentsArrayCallablePatternCallableArrayNotMatched() {
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

        val providers =
            listOf(
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

    fun testFindProviderForMethodNameElementSeparateArgsMatchesMethodString() {
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

        val providers =
            listOf(
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

    fun testFindProviderForArgumentsArraySeparateArgsMatchesArgumentsArray() {
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

        val providers =
            listOf(
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

    fun testFindProviderForMethodNameElementNoProvidersReturnsNull() {
        myFixture.configureByText(
            "test.php",
            "<?php class Foo { public function bar() {} } new Foo(['Bar', 'baz'], []);",
        )

        val element = myFixture.findElementByText("'baz'", StringLiteralExpression::class.java)
        assertNotNull(element)
        val match = PsaPhpMethodArgumentHelper.findProviderForMethodNameElement(element!!, emptyList())
        assertNull(match)
    }

    fun testFindProviderForMethodNameElementWrongOuterMethodReturnsNull() {
        myFixture.configureByText(
            "test.php",
            """
            <?php
            class ServiceMethodMessage {}
            class Foo { public function bar() {} }
            somethingElse([Foo::class, 'bar'], []);
            """.trimIndent(),
        )

        val providers =
            listOf(
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

    fun testMapArgumentsToParametersNoOffsetMapsByPosition() {
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

    fun testMapArgumentsToParametersWithOffsetSkipsLeadingParameters() {
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

    fun testMapArgumentsToParametersVariadicRepeatsLastParameter() {
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

    fun testMapArgumentsToParametersExtraArgumentsAreSkipped() {
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

    // ── provider edge cases ──────────────────────────────────────────────

    private fun queueProvider(
        classIndex: Int? = 0,
        methodIndex: Int? = 1,
    ) = MethodArgumentProviderModel().apply {
        `class` = "QueueManager"
        method = "executeServiceMethod"
        classArgumentIndex = classIndex
        methodArgumentIndex = methodIndex
        argumentsArgumentIndex = 2
    }

    private fun configureQueueCall(call: String) {
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
            ${'$'}queue->executeServiceMethod($call);
            """.trimIndent(),
        )
    }

    private fun argumentsArray(): ArrayCreationExpression =
        PsiTreeUtil.findChildrenOfType(myFixture.file, ArrayCreationExpression::class.java).last()

    // A provider that names neither a callable nor a class + method argument can't match anything.
    fun testProviderWithoutCallableOrMethodArgumentNeverMatches() {
        configureQueueCall("'MyService', 'doWork', [1, 2]")
        val provider = queueProvider(classIndex = null, methodIndex = null)
        val element = myFixture.findElementByText("'doWork'", StringLiteralExpression::class.java)!!

        assertNull(PsaPhpMethodArgumentHelper.findProviderForMethodNameElement(element, listOf(provider)))
        assertNull(PsaPhpMethodArgumentHelper.findProviderForArgumentsArray(argumentsArray(), listOf(provider)))
    }

    // The class argument may be a `Foo::class` constant directly.
    fun testClassArgumentAsClassConstant() {
        configureQueueCall("MyService::class, 'doWork', [1, 2]")

        val match = PsaPhpMethodArgumentHelper.findProviderForArgumentsArray(argumentsArray(), listOf(queueProvider()))

        assertEquals("doWork", match!!.method.name)
    }

    // `Foo::class` that doesn't resolve (here: a namespace mismatch) falls back to a same-named class
    // declared in the same file.
    fun testUnresolvedClassConstantFallsBackToSameNamedClassInFile() {
        myFixture.configureByText(
            "Queue.php",
            """
            <?php
            namespace App {
                class QueueManager {
                    public function executeServiceMethod(${'$'}service, ${'$'}method, ${'$'}args) {}
                }
                class MyService {
                    public function doWork(${'$'}a) {}
                }
            }
            namespace Other {
                (new \App\QueueManager())->executeServiceMethod(MyService::class, 'doWork', [1]);
            }
            """.trimIndent(),
        )

        val match = PsaPhpMethodArgumentHelper.findProviderForArgumentsArray(argumentsArray(), listOf(queueProvider()))

        assertEquals("doWork", match!!.method.name)
    }

    fun testClassArgumentThatIsNotAClassReferenceDoesNotMatch() {
        configureQueueCall("\$something, 'doWork', [1, 2]")

        assertNull(PsaPhpMethodArgumentHelper.findProviderForArgumentsArray(argumentsArray(), listOf(queueProvider())))
    }

    // ── callable provider edge cases ─────────────────────────────────────

    private fun configureCallableMessage(callable: String) {
        myFixture.configureByText(
            "Dispatcher.php",
            """
            <?php
            class ServiceMethodMessage {}
            class AccountStatsManager {
                public function updateStats(${'$'}account) {}
            }
            function wrap(${'$'}callable) { return ${'$'}callable; }
            new ServiceMethodMessage($callable, [${'$'}account]);
            """.trimIndent(),
        )
    }

    private val callableProvider =
        MethodArgumentProviderModel().apply {
            `class` = "ServiceMethodMessage"
            method = "__construct"
            callableArgumentIndex = 0
            argumentsArgumentIndex = 1
        }

    // The callable array may be nested inside another expression (e.g. a wrapper call).
    fun testCallableArrayNestedInsideAnotherExpression() {
        configureCallableMessage("wrap([AccountStatsManager::class, 'updateStats'])")

        val match = PsaPhpMethodArgumentHelper.findProviderForArgumentsArray(argumentsArray(), listOf(callableProvider))

        assertEquals("updateStats", match!!.method.name)
    }

    fun testCallableArgumentWithoutArrayDoesNotMatch() {
        configureCallableMessage("\$callable")

        assertNull(PsaPhpMethodArgumentHelper.findProviderForArgumentsArray(argumentsArray(), listOf(callableProvider)))
    }

    // ── findArgumentForParameter ─────────────────────────────────────────

    private fun doWorkElement() = myFixture.findElementByText("'doWork'", StringLiteralExpression::class.java)!!

    fun testFindArgumentForParameterReturnsArrayValueAtParameterIndex() {
        configureQueueCall("'MyService', 'doWork', [1, 2]")

        assertEquals("2", PsaPhpMethodArgumentHelper.findArgumentForParameter(doWorkElement(), queueProvider(), 1)!!.text)
    }

    // The arguments array may be nested inside another expression (e.g. array_merge(...)).
    fun testFindArgumentForParameterFindsNestedArgumentsArray() {
        configureQueueCall("'MyService', 'doWork', array_merge([1, 2])")

        assertEquals("1", PsaPhpMethodArgumentHelper.findArgumentForParameter(doWorkElement(), queueProvider(), 0)!!.text)
    }

    fun testFindArgumentForParameterWithoutArgumentsArrayIsNull() {
        configureQueueCall("'MyService', 'doWork', \$args")

        assertNull(PsaPhpMethodArgumentHelper.findArgumentForParameter(doWorkElement(), queueProvider(), 0))
    }

    // Parameters before `arguments_offset` aren't supplied by the arguments array.
    fun testFindArgumentForParameterBeforeArgumentsOffsetIsNull() {
        configureQueueCall("'MyService', 'doWork', [1, 2]")
        val provider = queueProvider().apply { argumentsOffset = 1 }

        assertNull(PsaPhpMethodArgumentHelper.findArgumentForParameter(doWorkElement(), provider, 0))
        assertEquals("1", PsaPhpMethodArgumentHelper.findArgumentForParameter(doWorkElement(), provider, 1)!!.text)
    }
}
