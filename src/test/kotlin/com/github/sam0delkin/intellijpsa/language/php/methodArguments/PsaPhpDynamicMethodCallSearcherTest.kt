package com.github.sam0delkin.intellijpsa.language.php.methodArguments

import com.github.sam0delkin.intellijpsa.language.php.model.MethodArgumentProviderModel
import com.github.sam0delkin.intellijpsa.language.php.services.PhpPsaManager
import com.github.sam0delkin.intellijpsa.settings.Settings
import com.intellij.openapi.components.service
import com.intellij.psi.PsiManager
import com.intellij.psi.search.searches.ReferencesSearch
import com.intellij.psi.util.PsiTreeUtil
import com.intellij.testFramework.fixtures.BasePlatformTestCase
import com.jetbrains.php.lang.psi.elements.Method

class PsaPhpDynamicMethodCallSearcherTest : BasePlatformTestCase() {
    override fun setUp() {
        super.setUp()
        project.service<Settings>().pluginEnabled = true
        project.service<Settings>().scriptPath = "psa.sh"
        project.service<PhpPsaManager>().getSettings().enabled = true
    }

    fun testFindUsagesAcrossFilesCallablePattern() {
        setupCallableProvider()

        val classFile =
            myFixture.addFileToProject(
                "AccountStatsManager.php",
                """
                <?php
                class AccountStatsManager {
                    public function updateStats(${'$'}a) {}
                }
                """.trimIndent(),
            )

        myFixture.configureByText(
            "Call.php",
            """
            <?php
            class ServiceMethodMessage {}
            new ServiceMethodMessage([AccountStatsManager::class, 'updateStats'], []);
            """.trimIndent(),
        )

        val classPsi = PsiManager.getInstance(project).findFile(classFile.virtualFile)!!
        val method =
            PsiTreeUtil
                .findChildrenOfType(classPsi, Method::class.java)
                .first { it.name == "updateStats" }

        val refs =
            ReferencesSearch
                .search(method)
                .findAll()
                .filter { it is PsaPhpMethodReference }

        assertTrue(
            "Find Usages on a PHP method should find the cross-file dynamic dispatch string literal",
            refs.isNotEmpty(),
        )
    }

    fun testFindUsagesAcrossFilesSeparateArgsPattern() {
        setupSeparateArgsProvider()

        val classFile =
            myFixture.addFileToProject(
                "MyService.php",
                """
                <?php
                class MyService {
                    public function doWork(${'$'}x) {}
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
            ${'$'}q->executeServiceMethod('MyService', 'doWork', [1]);
            """.trimIndent(),
        )

        val classPsi = PsiManager.getInstance(project).findFile(classFile.virtualFile)!!
        val method =
            PsiTreeUtil
                .findChildrenOfType(classPsi, Method::class.java)
                .first { it.name == "doWork" }

        val refs =
            ReferencesSearch
                .search(method)
                .findAll()
                .filter { it is PsaPhpMethodReference }

        assertTrue(
            "Find Usages on a PHP method should find the cross-file dynamic dispatch string literal",
            refs.isNotEmpty(),
        )
    }

    fun testFindUsagesDoesNotMatchUnrelatedMethodCallablePattern() {
        setupCallableProvider()

        val classFile =
            myFixture.addFileToProject(
                "AccountStatsManager.php",
                """
                <?php
                class AccountStatsManager {
                    public function updateStats(${'$'}a) {}
                    public function otherMethod() {}
                }
                """.trimIndent(),
            )

        myFixture.configureByText(
            "Call.php",
            """
            <?php
            class ServiceMethodMessage {}
            new ServiceMethodMessage([AccountStatsManager::class, 'updateStats'], []);
            """.trimIndent(),
        )

        val classPsi = PsiManager.getInstance(project).findFile(classFile.virtualFile)!!
        val otherMethod =
            PsiTreeUtil
                .findChildrenOfType(classPsi, Method::class.java)
                .first { it.name == "otherMethod" }

        val refs =
            ReferencesSearch
                .search(otherMethod)
                .findAll()
                .filter { it is PsaPhpMethodReference }

        assertTrue(
            "Find Usages on an unrelated method should not match the dynamic dispatch string literal",
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
