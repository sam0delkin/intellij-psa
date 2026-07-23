package com.github.sam0delkin.intellijpsa.language.php.methodArguments

import com.github.sam0delkin.intellijpsa.language.php.model.MethodArgumentProviderModel
import com.github.sam0delkin.intellijpsa.language.php.services.PhpPsaManager
import com.github.sam0delkin.intellijpsa.settings.Settings
import com.intellij.openapi.components.service
import com.intellij.psi.PsiManager
import com.intellij.psi.search.searches.ReferencesSearch
import com.intellij.psi.util.PsiTreeUtil
import com.intellij.testFramework.fixtures.BasePlatformTestCase
import com.jetbrains.php.lang.psi.elements.Parameter

class PsaPhpDynamicParameterUsageSearcherTest : BasePlatformTestCase() {
    override fun setUp() {
        super.setUp()
        project.service<Settings>().pluginEnabled = true
        project.service<Settings>().scriptPath = "psa.sh"
        project.service<PhpPsaManager>().getSettings().enabled = true
    }

    fun testParameterFindUsagesCallablePattern() {
        setupCallableProvider()

        val classFile =
            myFixture.addFileToProject(
                "AccountPaymentManager.php",
                """
                <?php
                class AccountPaymentManager {
                    public function sendDailyPayments(${'$'}account, ${'$'}date): void {}
                }
                """.trimIndent(),
            )

        myFixture.configureByText(
            "Command.php",
            """
            <?php
            class ServiceMethodMessage {}
            new ServiceMethodMessage(
                [AccountPaymentManager::class, 'sendDailyPayments'],
                [${'$'}accountObj, ${'$'}dateStr]
            );
            """.trimIndent(),
        )

        val classPsi = PsiManager.getInstance(project).findFile(classFile.virtualFile)!!
        val parameter =
            PsiTreeUtil
                .findChildrenOfType(classPsi, Parameter::class.java)
                .first { it.name == "account" }

        val refs =
            ReferencesSearch
                .search(parameter)
                .findAll()
                .filter { it is PsaPhpDynamicArgumentReference }

        assertTrue(
            "Find Usages on \$account should find the positionally-matching dynamic argument",
            refs.isNotEmpty(),
        )
        assertTrue(
            "The found element should be the first argument in the dynamic array",
            refs
                .first()
                .element.text
                .contains("accountObj"),
        )
    }

    fun testParameterFindUsagesSecondParamCallablePattern() {
        setupCallableProvider()

        val classFile =
            myFixture.addFileToProject(
                "AccountPaymentManager.php",
                """
                <?php
                class AccountPaymentManager {
                    public function sendDailyPayments(${'$'}account, ${'$'}date): void {}
                }
                """.trimIndent(),
            )

        myFixture.configureByText(
            "Command.php",
            """
            <?php
            class ServiceMethodMessage {}
            new ServiceMethodMessage(
                [AccountPaymentManager::class, 'sendDailyPayments'],
                [${'$'}accountObj, ${'$'}dateStr]
            );
            """.trimIndent(),
        )

        val classPsi = PsiManager.getInstance(project).findFile(classFile.virtualFile)!!
        val parameter =
            PsiTreeUtil
                .findChildrenOfType(classPsi, Parameter::class.java)
                .first { it.name == "date" }

        val refs =
            ReferencesSearch
                .search(parameter)
                .findAll()
                .filter { it is PsaPhpDynamicArgumentReference }

        assertTrue(
            "Find Usages on \$date should find the second dynamic argument",
            refs.isNotEmpty(),
        )
        assertTrue(
            "The found element should be the second argument in the dynamic array",
            refs
                .first()
                .element.text
                .contains("dateStr"),
        )
    }

    fun testParameterFindUsagesSeparateArgsPattern() {
        setupSeparateArgsProvider()

        val classFile =
            myFixture.addFileToProject(
                "MyService.php",
                """
                <?php
                class MyService {
                    public function doWork(${'$'}payload): void {}
                }
                """.trimIndent(),
            )

        myFixture.configureByText(
            "Call.php",
            """
            <?php
            class QueueManager {
                public function executeServiceMethod(${'$'}svc, ${'$'}method, ${'$'}args) {}
            }
            ${'$'}q = new QueueManager();
            ${'$'}q->executeServiceMethod('MyService', 'doWork', [${'$'}thePayload]);
            """.trimIndent(),
        )

        val classPsi = PsiManager.getInstance(project).findFile(classFile.virtualFile)!!
        val parameter =
            PsiTreeUtil
                .findChildrenOfType(classPsi, Parameter::class.java)
                .first { it.name == "payload" }

        val refs =
            ReferencesSearch
                .search(parameter)
                .findAll()
                .filter { it is PsaPhpDynamicArgumentReference }

        assertTrue(
            "Find Usages on \$payload should find the matching argument in the separate-args dynamic call",
            refs.isNotEmpty(),
        )
        assertTrue(
            "The found element should be the dynamic argument",
            refs
                .first()
                .element.text
                .contains("thePayload"),
        )
    }

    fun testParameterFindUsagesNoMatchForUnrelatedMethod() {
        setupCallableProvider()

        val classFile =
            myFixture.addFileToProject(
                "AccountPaymentManager.php",
                """
                <?php
                class AccountPaymentManager {
                    public function sendDailyPayments(${'$'}account, ${'$'}date): void {}
                    public function otherMethod(${'$'}x): void {}
                }
                """.trimIndent(),
            )

        myFixture.configureByText(
            "Command.php",
            """
            <?php
            class ServiceMethodMessage {}
            new ServiceMethodMessage(
                [AccountPaymentManager::class, 'sendDailyPayments'],
                [${'$'}accountObj, ${'$'}dateStr]
            );
            """.trimIndent(),
        )

        val classPsi = PsiManager.getInstance(project).findFile(classFile.virtualFile)!!
        val parameter =
            PsiTreeUtil
                .findChildrenOfType(classPsi, Parameter::class.java)
                .first { it.name == "x" }

        val refs =
            ReferencesSearch
                .search(parameter)
                .findAll()
                .filter { it is PsaPhpDynamicArgumentReference }

        assertTrue(
            "Parameter of an unrelated method should find no dynamic argument usages",
            refs.isEmpty(),
        )
    }

    fun testParameterFindUsagesDisabledWhenPluginOff() {
        project.service<Settings>().pluginEnabled = false
        setupCallableProvider()

        val classFile =
            myFixture.addFileToProject(
                "AccountPaymentManager.php",
                """
                <?php
                class AccountPaymentManager {
                    public function sendDailyPayments(${'$'}account, ${'$'}date): void {}
                }
                """.trimIndent(),
            )

        myFixture.configureByText(
            "Command.php",
            """
            <?php
            class ServiceMethodMessage {}
            new ServiceMethodMessage(
                [AccountPaymentManager::class, 'sendDailyPayments'],
                [${'$'}accountObj, ${'$'}dateStr]
            );
            """.trimIndent(),
        )

        val classPsi = PsiManager.getInstance(project).findFile(classFile.virtualFile)!!
        val parameter =
            PsiTreeUtil
                .findChildrenOfType(classPsi, Parameter::class.java)
                .first { it.name == "account" }

        val refs =
            ReferencesSearch
                .search(parameter)
                .findAll()
                .filter { it is PsaPhpDynamicArgumentReference }

        assertTrue(
            "No dynamic argument usages should be found when the plugin is disabled",
            refs.isEmpty(),
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
}
