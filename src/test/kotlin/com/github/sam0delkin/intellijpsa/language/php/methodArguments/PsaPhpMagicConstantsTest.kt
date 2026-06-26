package com.github.sam0delkin.intellijpsa.language.php.methodArguments

import com.github.sam0delkin.intellijpsa.language.php.model.MethodArgumentProviderModel
import com.github.sam0delkin.intellijpsa.language.php.services.PhpPsaManager
import com.github.sam0delkin.intellijpsa.settings.Settings
import com.intellij.openapi.components.service
import com.intellij.testFramework.fixtures.BasePlatformTestCase

class PsaPhpMagicConstantsTest : BasePlatformTestCase() {
    override fun setUp() {
        super.setUp()
        project.service<Settings>().pluginEnabled = true
        project.service<Settings>().scriptPath = "psa.sh"
        project.service<PhpPsaManager>().getSettings().enabled = true

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

    fun testMagicConstantsSupport() {
        myFixture.configureByText(
            "Call.php",
            """
            <?php
            class QueueManager {
                public function executeServiceMethod(${'$'}svc, ${'$'}method, ${'$'}args) {}
            }
            class MyService {
                public function doWork(${'$'}x) {
                    ${'$'}q = new QueueManager();
                    ${'$'}q->executeServiceMethod(__CLASS__, __METHOD__, [1]);
                }
            }
            """.trimIndent(),
        )

        // Find __METHOD__
        val call = myFixture.findElementByText("__METHOD__", com.jetbrains.php.lang.psi.elements.ConstantReference::class.java)
        assertNotNull(call)

        // The arguments array should have parameter info or the provider should resolve the method.
        // Let's check if the arguments array [1] is recognized by PsaPhpMethodArgumentHelper
        val array = myFixture.findElementByText("[1]", com.jetbrains.php.lang.psi.elements.ArrayCreationExpression::class.java)
        assertNotNull(array)

        val allProviders = project.service<PhpPsaManager>().getSettings().methodArgumentProviders ?: emptyList()
        val match = PsaPhpMethodArgumentHelper.findProviderForArgumentsArray(array, allProviders)

        assertNotNull("Provider should match arguments array even with magic constants", match)
        assertEquals("doWork", match!!.method.name)

        // Test provider match on __METHOD__
        val matchRef = PsaPhpMethodArgumentHelper.findProviderForMethodNameElement(call, allProviders)
        assertNotNull("Provider match should be found for __METHOD__", matchRef)
        assertEquals("doWork", matchRef!!.method.name)
    }
}
