package com.github.sam0delkin.intellijpsa.language.php.methodArguments

import com.github.sam0delkin.intellijpsa.language.php.PhpPsaExtension
import com.github.sam0delkin.intellijpsa.language.php.model.MethodArgumentProviderModel
import com.github.sam0delkin.intellijpsa.language.php.services.PhpPsaManager
import com.github.sam0delkin.intellijpsa.settings.Settings
import com.intellij.openapi.command.WriteCommandAction
import com.intellij.openapi.components.service
import com.intellij.openapi.util.Ref
import com.intellij.psi.util.PsiTreeUtil
import com.intellij.refactoring.rename.ResolveSnapshotProvider
import com.intellij.testFramework.fixtures.BasePlatformTestCase
import com.intellij.usageView.UsageInfo
import com.jetbrains.php.lang.psi.elements.ArrayCreationExpression
import com.jetbrains.php.lang.psi.elements.Method
import com.jetbrains.php.refactoring.changeSignature.PhpChangeInfo
import com.jetbrains.php.refactoring.changeSignature.PhpMethodDescriptor
import com.jetbrains.php.refactoring.changeSignature.PhpParameterInfo
import org.mockito.Mockito.mock

class PsaPhpChangeSignatureUsageProcessorTest : BasePlatformTestCase() {
    private lateinit var processor: PsaPhpChangeSignatureUsageProcessor

    override fun setUp() {
        super.setUp()
        processor = PsaPhpChangeSignatureUsageProcessor()
        project.service<Settings>().pluginEnabled = true
        project.service<Settings>().scriptPath = "psa.sh"
        project.service<PhpPsaManager>().getSettings().enabled = true
    }

    fun testFindUsagesCallablePatternFindsArgumentsArray() {
        setupCallableProvider()
        myFixture.configureByText(
            "Call.php",
            """
            <?php
            class ServiceMethodMessage {}
            class AccountStatsManager {
                public function updateStats(${'$'}a) {}
            }
            new ServiceMethodMessage([AccountStatsManager::class, 'updateStats'], [${'$'}a]);
            """.trimIndent(),
        )

        val method = myFixture.findElementByText("updateStats", Method::class.java)
        assertNotNull(method)

        val changeInfo = buildChangeInfo(method!!, listOf(PhpParameterInfo(0, "a")))
        val usages = processor.findUsages(changeInfo)

        assertTrue("Should find at least one dynamic call site usage", usages.isNotEmpty())
        assertInstanceOf(usages.first(), PsaPhpDynamicCallUsageInfo::class.java)
    }

    fun testFindUsagesSeparateArgsPatternFindsArgumentsArray() {
        setupSeparateArgsProvider()
        myFixture.configureByText(
            "Call.php",
            """
            <?php
            class QueueManager {
                public function executeServiceMethod(${'$'}svc, ${'$'}method, ${'$'}args) {}
            }
            class MyService {
                public function doWork(${'$'}x) {}
            }
            ${'$'}q = new QueueManager();
            ${'$'}q->executeServiceMethod('MyService', 'doWork', [1]);
            """.trimIndent(),
        )

        val method = myFixture.findElementByText("doWork", Method::class.java)
        assertNotNull(method)

        val changeInfo = buildChangeInfo(method!!, listOf(PhpParameterInfo(0, "x")))
        val usages = processor.findUsages(changeInfo)

        assertTrue("Should find dynamic call site for separate-args pattern", usages.isNotEmpty())
        val usage = usages.first() as PsaPhpDynamicCallUsageInfo
        assertTrue("Usage element should be an array", usage.array.text.contains("1"))
    }

    fun testFindUsagesPluginDisabledReturnsEmpty() {
        project.service<Settings>().pluginEnabled = false
        setupCallableProvider()

        myFixture.configureByText(
            "Call.php",
            """
            <?php
            class ServiceMethodMessage {}
            class AccountStatsManager { public function updateStats(${'$'}a) {} }
            new ServiceMethodMessage([AccountStatsManager::class, 'updateStats'], [${'$'}a]);
            """.trimIndent(),
        )

        val method = myFixture.findElementByText("updateStats", Method::class.java)
        assertNotNull(method)

        val changeInfo = buildChangeInfo(method!!, emptyList())
        val usages = processor.findUsages(changeInfo)

        assertCollectionEmpty("Should find no usages when plugin is disabled", usages.toList())
    }

    fun testProcessUsageAddParameterAtEndCallablePattern() {
        setupCallableProvider()
        myFixture.configureByText(
            "Call.php",
            """
            <?php
            class ServiceMethodMessage {}
            class AccountStatsManager {
                public function updateStats(${'$'}a) {}
            }
            new ServiceMethodMessage([AccountStatsManager::class, 'updateStats'], [${'$'}a]);
            """.trimIndent(),
        )

        val method = myFixture.findElementByText("updateStats", Method::class.java)
        assertNotNull(method)

        val existingParam = PhpParameterInfo(0, "a")
        val newParam = PhpParameterInfo(-1, "newParam").apply { defaultValue = "null" }
        val changeInfo = buildChangeInfo(method!!, listOf(existingParam, newParam))

        val array =
            PsiTreeUtil
                .findChildrenOfType(myFixture.file, ArrayCreationExpression::class.java)
                .first { it.text.contains("\$a") }

        val usageInfo =
            PsaPhpDynamicCallUsageInfo(
                array,
                project
                    .service<PhpPsaManager>()
                    .getSettings()
                    .methodArgumentProviders!!
                    .first(),
            )
        val allUsages: Array<UsageInfo> = arrayOf(usageInfo)

        WriteCommandAction.runWriteCommandAction(project) {
            processor.processUsage(changeInfo, usageInfo, false, allUsages)
        }

        val updatedText = myFixture.file.text
        assertTrue(
            "New parameter placeholder 'null' should be appended to the arguments array",
            updatedText.contains("\$a") && updatedText.contains("null"),
        )
    }

    fun testProcessUsageAddParameterAtEndSeparateArgsPattern() {
        setupSeparateArgsProvider()
        myFixture.configureByText(
            "Call.php",
            """
            <?php
            class QueueManager {
                public function executeServiceMethod(${'$'}svc, ${'$'}method, ${'$'}args) {}
            }
            class MyService {
                public function doWork(${'$'}x) {}
            }
            ${'$'}q = new QueueManager();
            ${'$'}q->executeServiceMethod('MyService', 'doWork', [1]);
            """.trimIndent(),
        )

        val method = myFixture.findElementByText("doWork", Method::class.java)
        assertNotNull(method)

        val existingParam = PhpParameterInfo(0, "x")
        val newParam = PhpParameterInfo(-1, "y").apply { defaultValue = "0" }
        val changeInfo = buildChangeInfo(method!!, listOf(existingParam, newParam))

        val array =
            PsiTreeUtil
                .findChildrenOfType(myFixture.file, ArrayCreationExpression::class.java)
                .first { it.text.trim() == "[1]" }

        val provider =
            project
                .service<PhpPsaManager>()
                .getSettings()
                .methodArgumentProviders!!
                .first()
        val usageInfo = PsaPhpDynamicCallUsageInfo(array, provider)

        val allUsages: Array<UsageInfo> = arrayOf(usageInfo)
        WriteCommandAction.runWriteCommandAction(project) {
            processor.processUsage(changeInfo, usageInfo, false, allUsages)
        }

        val updatedText = myFixture.file.text
        assertTrue(
            "New parameter placeholder '0' should be appended",
            updatedText.contains("0"),
        )
    }

    fun testProcessUsageReorderParametersCallablePattern() {
        setupCallableProvider()
        myFixture.configureByText(
            "Call.php",
            """
            <?php
            class ServiceMethodMessage {}
            class AccountStatsManager {
                public function updateStats(${'$'}a, ${'$'}b) {}
            }
            new ServiceMethodMessage([AccountStatsManager::class, 'updateStats'], [1, 2]);
            """.trimIndent(),
        )

        val method = myFixture.findElementByText("updateStats", Method::class.java)!!
        // Swap the two parameters: new order is (b, a)
        val changeInfo = buildChangeInfo(method, listOf(PhpParameterInfo(1, "b"), PhpParameterInfo(0, "a")))

        val array =
            PsiTreeUtil
                .findChildrenOfType(myFixture.file, ArrayCreationExpression::class.java)
                .first { it.text.trim() == "[1, 2]" }
        val provider =
            project
                .service<PhpPsaManager>()
                .getSettings()
                .methodArgumentProviders!!
                .first()
        val usageInfo = PsaPhpDynamicCallUsageInfo(array, provider)

        WriteCommandAction.runWriteCommandAction(project) {
            processor.processUsage(changeInfo, usageInfo, false, arrayOf(usageInfo))
        }

        assertTrue(
            "Array values should be reordered to match the new parameter order",
            myFixture.file.text.contains("[2, 1]"),
        )
    }

    fun testProcessUsageRemoveParameterCallablePattern() {
        setupCallableProvider()
        myFixture.configureByText(
            "Call.php",
            """
            <?php
            class ServiceMethodMessage {}
            class AccountStatsManager {
                public function updateStats(${'$'}a, ${'$'}b, ${'$'}c) {}
            }
            new ServiceMethodMessage([AccountStatsManager::class, 'updateStats'], [1, 2, 3]);
            """.trimIndent(),
        )

        val method = myFixture.findElementByText("updateStats", Method::class.java)!!
        // Drop the middle parameter: new order is (a, c)
        val changeInfo = buildChangeInfo(method, listOf(PhpParameterInfo(0, "a"), PhpParameterInfo(2, "c")))

        val array =
            PsiTreeUtil
                .findChildrenOfType(myFixture.file, ArrayCreationExpression::class.java)
                .first { it.text.trim() == "[1, 2, 3]" }
        val provider =
            project
                .service<PhpPsaManager>()
                .getSettings()
                .methodArgumentProviders!!
                .first()
        val usageInfo = PsaPhpDynamicCallUsageInfo(array, provider)

        WriteCommandAction.runWriteCommandAction(project) {
            processor.processUsage(changeInfo, usageInfo, false, arrayOf(usageInfo))
        }

        assertTrue(
            "Removed parameter's value should be dropped from the array",
            myFixture.file.text.contains("[1, 3]"),
        )
    }

    fun testProcessUsageReorderWithOffsetKeepsLeadingArrayPositions() {
        project.service<PhpPsaManager>().getSettings().methodArgumentProviders =
            arrayListOf(
                MethodArgumentProviderModel().apply {
                    `class` = "ServiceMethodMessage"
                    method = "__construct"
                    callableArgumentIndex = 0
                    argumentsArgumentIndex = 1
                    argumentsOffset = 1
                },
            )
        myFixture.configureByText(
            "Call.php",
            """
            <?php
            class ServiceMethodMessage {}
            class AccountStatsManager {
                public function updateStats(${'$'}requestId, ${'$'}a, ${'$'}b) {}
            }
            new ServiceMethodMessage([AccountStatsManager::class, 'updateStats'], [1, 2]);
            """.trimIndent(),
        )

        val method = myFixture.findElementByText("updateStats", Method::class.java)!!
        // Keep requestId at index 0, swap a and b
        val changeInfo =
            buildChangeInfo(
                method,
                listOf(PhpParameterInfo(0, "requestId"), PhpParameterInfo(2, "b"), PhpParameterInfo(1, "a")),
            )

        val array =
            PsiTreeUtil
                .findChildrenOfType(myFixture.file, ArrayCreationExpression::class.java)
                .first { it.text.trim() == "[1, 2]" }
        val provider =
            project
                .service<PhpPsaManager>()
                .getSettings()
                .methodArgumentProviders!!
                .first()
        val usageInfo = PsaPhpDynamicCallUsageInfo(array, provider)

        WriteCommandAction.runWriteCommandAction(project) {
            processor.processUsage(changeInfo, usageInfo, false, arrayOf(usageInfo))
        }

        assertTrue(
            "Offset-skipped leading parameter is ignored; remaining values are reordered",
            myFixture.file.text.contains("[2, 1]"),
        )
    }

    fun testProcessUsageBeforeMethodChangeDoesNothing() {
        setupCallableProvider()
        myFixture.configureByText(
            "Call.php",
            """
            <?php
            class ServiceMethodMessage {}
            class AccountStatsManager { public function updateStats(${'$'}a) {} }
            new ServiceMethodMessage([AccountStatsManager::class, 'updateStats'], [${'$'}a]);
            """.trimIndent(),
        )

        val method = myFixture.findElementByText("updateStats", Method::class.java)!!
        val newParam = PhpParameterInfo(-1, "newParam").apply { defaultValue = "null" }
        val changeInfo = buildChangeInfo(method, listOf(PhpParameterInfo(0, "a"), newParam))

        val array =
            PsiTreeUtil
                .findChildrenOfType(myFixture.file, ArrayCreationExpression::class.java)
                .first { it.text.contains("\$a") }
        val provider =
            project
                .service<PhpPsaManager>()
                .getSettings()
                .methodArgumentProviders!!
                .first()
        val usageInfo = PsaPhpDynamicCallUsageInfo(array, provider)
        val originalText = myFixture.file.text

        WriteCommandAction.runWriteCommandAction(project) {
            val result = processor.processUsage(changeInfo, usageInfo, true, arrayOf(usageInfo))
            assertFalse("processUsage with beforeMethodChange=true should return false", result)
        }

        assertEquals("File should be unchanged when beforeMethodChange=true", originalText, myFixture.file.text)
    }

    fun testProcessUsageNonDynamicUsageReturnsFalse() {
        setupCallableProvider()
        myFixture.configureByText("test.php", "<?php class A { public function b() {} }")
        val method = myFixture.findElementByText("b", Method::class.java)!!
        val changeInfo = buildChangeInfo(method, emptyList())

        val plainUsage = UsageInfo(method)
        val result = processor.processUsage(changeInfo, plainUsage, false, arrayOf(plainUsage))

        assertFalse("processUsage should return false for non-dynamic usages", result)
    }

    fun testUpdateInfoStoresMethodArgumentProviders() {
        val phpSettings = project.service<PhpPsaManager>().getSettings()
        phpSettings.enabled = true

        val extension =
            PhpPsaExtension()
        extension.updateInfo(
            project,
            """
            {
                "method_argument_providers": [
                    {
                        "class": "ServiceMethodMessage",
                        "method": "__construct",
                        "callable_argument_index": 0,
                        "arguments_argument_index": 1,
                        "arguments_offset": 0
                    }
                ]
            }
            """.trimIndent(),
        )

        val providers = phpSettings.methodArgumentProviders
        assertNotNull("methodArgumentProviders should be stored after updateInfo", providers)
        assertEquals(1, providers!!.size)
        assertEquals("ServiceMethodMessage", providers[0].`class`)
        assertEquals("__construct", providers[0].method)
        assertEquals(0, providers[0].callableArgumentIndex)
        assertEquals(1, providers[0].argumentsArgumentIndex)
    }

    // Regression test: only the first configured provider's argument layout used to be considered
    // when locating the arguments array, so call sites of any later provider were missed.
    fun testFindUsagesUsesTheMatchingProviderWhenSeveralAreConfigured() {
        project.service<PhpPsaManager>().getSettings().methodArgumentProviders =
            arrayListOf(
                MethodArgumentProviderModel().apply {
                    `class` = "QueueManager"
                    method = "executeServiceMethod"
                    classArgumentIndex = 0
                    methodArgumentIndex = 1
                    argumentsArgumentIndex = 2
                },
                MethodArgumentProviderModel().apply {
                    `class` = "ServiceMethodMessage"
                    method = "__construct"
                    callableArgumentIndex = 0
                    argumentsArgumentIndex = 1
                },
            )
        myFixture.configureByText(
            "Call.php",
            """
            <?php
            class ServiceMethodMessage {}
            class AccountStatsManager {
                public function updateStats(${'$'}a) {}
            }
            new ServiceMethodMessage([AccountStatsManager::class, 'updateStats'], [${'$'}a]);
            """.trimIndent(),
        )
        val method = myFixture.findElementByText("updateStats", Method::class.java)!!

        val usages = processor.findUsages(buildChangeInfo(method, listOf(PhpParameterInfo(0, "a"))))

        assertEquals(1, usages.size)
        assertEquals("[\$a]", (usages.single() as PsaPhpDynamicCallUsageInfo).array.text)
    }

    // The primary method itself, conflicts, previews and default values are all left to PHP's own
    // Change Signature processor.
    fun testLeavesPrimaryMethodConflictsAndDefaultsToPhpProcessor() {
        setupCallableProvider()
        myFixture.configureByText("Call.php", "<?php\nclass A { public function m() {} }")
        val changeInfo = buildChangeInfo(myFixture.findElementByText("m", Method::class.java)!!, emptyList())

        assertFalse(processor.processPrimaryMethod(changeInfo))
        assertTrue(processor.findConflicts(changeInfo, Ref(emptyArray())).isEmpty)
        assertFalse(processor.shouldPreviewUsages(changeInfo, emptyArray()))
        assertFalse(processor.setupDefaultValues(changeInfo, Ref(emptyArray()), project))
        val snapshots = mutableListOf<ResolveSnapshotProvider.ResolveSnapshot>()
        processor.registerConflictResolvers(snapshots, mock(ResolveSnapshotProvider::class.java), emptyArray(), changeInfo)
        assertEmpty(snapshots)
    }

    private fun buildChangeInfo(
        method: Method,
        params: List<PhpParameterInfo>,
    ): PhpChangeInfo {
        val descriptor = PhpMethodDescriptor(method)
        return PhpChangeInfo(
            descriptor,
            params.toTypedArray(),
            descriptor.returnTypeText ?: "",
            descriptor.name,
            descriptor.visibility,
            emptySet(),
            false,
            false,
        )
    }

    private fun setupCallableProvider() {
        project.service<PhpPsaManager>().getSettings().methodArgumentProviders =
            arrayListOf(
                MethodArgumentProviderModel().apply {
                    `class` = "ServiceMethodMessage"
                    method = "__construct"
                    callableArgumentIndex = 0
                    argumentsArgumentIndex = 1
                },
            )
    }

    private fun setupSeparateArgsProvider() {
        project.service<PhpPsaManager>().getSettings().methodArgumentProviders =
            arrayListOf(
                MethodArgumentProviderModel().apply {
                    `class` = "QueueManager"
                    method = "executeServiceMethod"
                    classArgumentIndex = 0
                    methodArgumentIndex = 1
                    argumentsArgumentIndex = 2
                },
            )
    }

    private fun <T> assertCollectionEmpty(
        message: String,
        collection: Collection<T>,
    ) = assertTrue(message, collection.isEmpty())

    private fun <T : Any> assertInstanceOf(
        obj: Any?,
        clazz: Class<T>,
    ) = assertTrue("Expected instance of ${clazz.simpleName} but got ${obj?.javaClass?.simpleName}", clazz.isInstance(obj))
}
