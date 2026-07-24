package com.github.sam0delkin.intellijpsa.language.php.methodArguments

import com.github.sam0delkin.intellijpsa.language.php.model.MethodArgumentProviderModel
import com.github.sam0delkin.intellijpsa.language.php.services.PhpPsaManager
import com.github.sam0delkin.intellijpsa.settings.Settings
import com.intellij.openapi.components.service
import com.intellij.testFramework.fixtures.BasePlatformTestCase
import com.jetbrains.php.lang.psi.elements.Method
import com.jetbrains.php.lang.psi.elements.StringLiteralExpression

class PsaPhpMethodArgumentReferenceContributorTest : BasePlatformTestCase() {
    override fun setUp() {
        super.setUp()
        project.service<Settings>().pluginEnabled = true
        project.service<Settings>().scriptPath = "psa.sh"
        project.service<PhpPsaManager>().getSettings().enabled = true
    }

    fun testReferenceCreatedCallablePattern() {
        setupCallableProvider()
        myFixture.configureByText(
            "Call.php",
            """
            <?php
            class ServiceMethodMessage {}
            class AccountStatsManager {
                public function updateStats(${'$'}a) {}
            }
            new ServiceMethodMessage([AccountStatsManager::class, 'updateStats'], []);
            """.trimIndent(),
        )

        val element = myFixture.findElementByText("'updateStats'", StringLiteralExpression::class.java)
        assertNotNull(element)

        val ref = element!!.references.filterIsInstance<PsaPhpMethodReference>().firstOrNull()
        assertNotNull("PsaPhpMethodReference should be created for method name string", ref)
    }

    fun testReferenceResolvesToPhpMethodCallablePattern() {
        setupCallableProvider()
        myFixture.configureByText(
            "Call.php",
            """
            <?php
            class ServiceMethodMessage {}
            class AccountStatsManager {
                public function updateStats(${'$'}a) {}
            }
            new ServiceMethodMessage([AccountStatsManager::class, 'updateStats'], []);
            """.trimIndent(),
        )

        val element = myFixture.findElementByText("'updateStats'", StringLiteralExpression::class.java)
        assertNotNull(element)

        val ref = element!!.references.filterIsInstance<PsaPhpMethodReference>().firstOrNull()
        assertNotNull(ref)

        val resolved = ref!!.resolve()
        assertNotNull("Reference should resolve to a PHP method", resolved)
        assertInstanceOf(resolved, Method::class.java)
        assertEquals("updateStats", (resolved as Method).name)
    }

    fun testIsReferenceToCallablePatternTrueForTargetMethod() {
        setupCallableProvider()
        myFixture.configureByText(
            "Call.php",
            """
            <?php
            class ServiceMethodMessage {}
            class AccountStatsManager {
                public function updateStats(${'$'}a) {}
            }
            new ServiceMethodMessage([AccountStatsManager::class, 'updateStats'], []);
            """.trimIndent(),
        )

        val element = myFixture.findElementByText("'updateStats'", StringLiteralExpression::class.java)
        assertNotNull(element)
        val ref = element!!.references.filterIsInstance<PsaPhpMethodReference>().first()

        val method = myFixture.findElementByText("updateStats", Method::class.java)
        assertNotNull(method)
        assertTrue("isReferenceTo should be true for the target method", ref.isReferenceTo(method!!))
    }

    fun testIsReferenceToCallablePatternFalseForOtherMethod() {
        setupCallableProvider()
        myFixture.configureByText(
            "Call.php",
            """
            <?php
            class ServiceMethodMessage {}
            class AccountStatsManager {
                public function updateStats(${'$'}a) {}
                public function otherMethod() {}
            }
            new ServiceMethodMessage([AccountStatsManager::class, 'updateStats'], []);
            """.trimIndent(),
        )

        val element = myFixture.findElementByText("'updateStats'", StringLiteralExpression::class.java)
        assertNotNull(element)
        val ref = element!!.references.filterIsInstance<PsaPhpMethodReference>().first()

        val otherMethod = myFixture.findElementByText("otherMethod", Method::class.java)
        assertNotNull(otherMethod)
        assertFalse("isReferenceTo should be false for a different method", ref.isReferenceTo(otherMethod!!))
    }

    fun testReferenceCreatedSeparateArgsPattern() {
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

        val element = myFixture.findElementByText("'doWork'", StringLiteralExpression::class.java)
        assertNotNull(element)

        val ref = element!!.references.filterIsInstance<PsaPhpMethodReference>().firstOrNull()
        assertNotNull("PsaPhpMethodReference should be created for method name string", ref)
        assertEquals("doWork", (ref!!.resolve() as? Method)?.name)
    }

    fun testNoReferenceWhenPluginDisabled() {
        project.service<Settings>().pluginEnabled = false
        setupCallableProvider()

        myFixture.configureByText(
            "Call.php",
            """
            <?php
            class ServiceMethodMessage {}
            class AccountStatsManager { public function updateStats() {} }
            new ServiceMethodMessage([AccountStatsManager::class, 'updateStats'], []);
            """.trimIndent(),
        )

        val element = myFixture.findElementByText("'updateStats'", StringLiteralExpression::class.java)
        assertNotNull(element)
        val psaRefs = element!!.references.filterIsInstance<PsaPhpMethodReference>()
        assertCollectionEmpty("No PsaPhpMethodReference when plugin is disabled", psaRefs)
    }

    fun testNoReferenceWhenPhpExtensionDisabled() {
        project.service<PhpPsaManager>().getSettings().enabled = false
        setupCallableProvider()

        myFixture.configureByText(
            "Call.php",
            """
            <?php
            class ServiceMethodMessage {}
            class AccountStatsManager { public function updateStats() {} }
            new ServiceMethodMessage([AccountStatsManager::class, 'updateStats'], []);
            """.trimIndent(),
        )

        val element = myFixture.findElementByText("'updateStats'", StringLiteralExpression::class.java)
        assertNotNull(element)
        val psaRefs = element!!.references.filterIsInstance<PsaPhpMethodReference>()
        assertCollectionEmpty("No PsaPhpMethodReference when PHP extension is disabled", psaRefs)
    }

    fun testNoReferenceForUnrelatedStringLiteral() {
        setupCallableProvider()
        myFixture.configureByText(
            "Call.php",
            """
            <?php
            class ServiceMethodMessage {}
            class AccountStatsManager {
                public function updateStats(${'$'}a) {}
            }
            ${'$'}unrelated = 'not_a_dispatch_call';
            new ServiceMethodMessage([AccountStatsManager::class, 'updateStats'], []);
            """.trimIndent(),
        )

        val element = myFixture.findElementByText("'not_a_dispatch_call'", StringLiteralExpression::class.java)
        assertNotNull(element)
        val psaRefs = element!!.references.filterIsInstance<PsaPhpMethodReference>()
        assertCollectionEmpty("No PsaPhpMethodReference for a string literal unrelated to any dispatch call", psaRefs)
    }

    fun testNoReferenceWhenNoProviders() {
        project.service<PhpPsaManager>().getSettings().methodArgumentProviders = null

        myFixture.configureByText(
            "Call.php",
            """
            <?php
            class ServiceMethodMessage {}
            class AccountStatsManager { public function updateStats() {} }
            new ServiceMethodMessage([AccountStatsManager::class, 'updateStats'], []);
            """.trimIndent(),
        )

        val element = myFixture.findElementByText("'updateStats'", StringLiteralExpression::class.java)
        assertNotNull(element)
        val psaRefs = element!!.references.filterIsInstance<PsaPhpMethodReference>()
        assertCollectionEmpty("No PsaPhpMethodReference when no providers configured", psaRefs)
    }

    fun testMethodRenameCallablePatternUpdatesStringLiteral() {
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

        myFixture.renameElement(method!!, "recalcStats")

        assertFalse(
            "Original string 'updateStats' should be gone after rename",
            myFixture.file.text.contains("'updateStats'"),
        )
        assertTrue(
            "String should be updated to 'recalcStats'",
            myFixture.file.text.contains("'recalcStats'"),
        )
    }

    fun testMethodRenameSeparateArgsPatternUpdatesStringLiteral() {
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

        myFixture.renameElement(method!!, "processWork")

        assertFalse(myFixture.file.text.contains("'doWork'"))
        assertTrue(myFixture.file.text.contains("'processWork'"))
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
    ) {
        assertTrue(message, collection.isEmpty())
    }

    private fun <T> assertInstanceOf(
        obj: Any?,
        clazz: Class<T>,
    ) {
        assertTrue("Expected instance of ${clazz.simpleName}", clazz.isInstance(obj))
    }
}
