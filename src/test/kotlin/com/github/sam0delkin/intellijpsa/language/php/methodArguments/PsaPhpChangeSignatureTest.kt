package com.github.sam0delkin.intellijpsa.language.php.methodArguments

import com.github.sam0delkin.intellijpsa.language.php.model.MethodArgumentProviderModel
import com.github.sam0delkin.intellijpsa.language.php.services.PhpPsaManager
import com.github.sam0delkin.intellijpsa.settings.Settings
import com.intellij.openapi.components.service
import com.intellij.testFramework.fixtures.BasePlatformTestCase
import com.jetbrains.php.lang.psi.elements.Method
import com.jetbrains.php.refactoring.changeSignature.PhpChangeInfo
import com.jetbrains.php.refactoring.changeSignature.PhpChangeSignatureProcessor
import com.jetbrains.php.refactoring.changeSignature.PhpMethodDescriptor
import com.jetbrains.php.refactoring.changeSignature.PhpParameterInfo

class PsaPhpChangeSignatureTest : BasePlatformTestCase() {
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

    fun testChangeSignatureUpdatesDynamicCall() {
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
                    ${'$'}q->executeServiceMethod(__CLASS__, 'doWork', [${'$'}x]);
                }
            }
            """.trimIndent(),
        )

        val method = myFixture.findElementByText("doWork", Method::class.java)
        assertNotNull(method)

        // Add a new parameter
        val descriptor = PhpMethodDescriptor(method!!)
        val newParams =
            arrayOf(
                PhpParameterInfo(0, "x"),
                PhpParameterInfo(-1, "y").apply { defaultValue = "false" },
            )

        val changeInfo =
            PhpChangeInfo(
                descriptor,
                newParams,
                descriptor.returnTypeText ?: "",
                descriptor.name,
                descriptor.visibility,
                emptySet<com.jetbrains.php.lang.psi.elements.Function>(),
                false,
                false,
            )

        val processor = PhpChangeSignatureProcessor(project, changeInfo)
        processor.run()

        myFixture.checkResult(
            """
            <?php
            class QueueManager {
                public function executeServiceMethod(${'$'}svc, ${'$'}method, ${'$'}args) {}
            }
            class MyService {
                function doWork(${'$'}x, ${'$'}y) {
                    ${'$'}q = new QueueManager();
                    ${'$'}q->executeServiceMethod(__CLASS__, 'doWork', [${'$'}x, false]);
                }
            }
            """.trimIndent(),
        )
    }

    fun testChangeSignatureWithMagicConstants() {
        myFixture.configureByText(
            "CallMagic.php",
            """
            <?php
            class QueueManager {
                public function executeServiceMethod(${'$'}svc, ${'$'}method, ${'$'}args) {}
            }
            class MyService {
                public function doWork(${'$'}x) {
                    ${'$'}q = new QueueManager();
                    ${'$'}q->executeServiceMethod(__CLASS__, __METHOD__, [${'$'}x]);
                }
            }
            """.trimIndent(),
        )

        val method = myFixture.findElementByText("doWork", Method::class.java)
        assertNotNull(method)

        // Add a new parameter
        val descriptor = PhpMethodDescriptor(method!!)
        val newParams =
            arrayOf(
                PhpParameterInfo(0, "x"),
                PhpParameterInfo(-1, "y").apply { defaultValue = "false" },
            )

        val changeInfo =
            PhpChangeInfo(
                descriptor,
                newParams,
                descriptor.returnTypeText ?: "",
                descriptor.name,
                descriptor.visibility,
                emptySet<com.jetbrains.php.lang.psi.elements.Function>(),
                false,
                false,
            )

        val processor = PhpChangeSignatureProcessor(project, changeInfo)
        processor.run()

        myFixture.checkResult(
            """
            <?php
            class QueueManager {
                public function executeServiceMethod(${'$'}svc, ${'$'}method, ${'$'}args) {}
            }
            class MyService {
                function doWork(${'$'}x, ${'$'}y) {
                    ${'$'}q = new QueueManager();
                    ${'$'}q->executeServiceMethod(__CLASS__, __METHOD__, [${'$'}x, false]);
                }
            }
            """.trimIndent(),
        )
    }
}
