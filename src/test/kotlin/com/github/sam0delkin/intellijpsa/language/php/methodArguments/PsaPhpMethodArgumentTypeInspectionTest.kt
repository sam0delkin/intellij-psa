package com.github.sam0delkin.intellijpsa.language.php.methodArguments

import com.github.sam0delkin.intellijpsa.language.php.model.MethodArgumentProviderModel
import com.github.sam0delkin.intellijpsa.language.php.services.PhpPsaManager
import com.github.sam0delkin.intellijpsa.settings.Settings
import com.intellij.codeInsight.daemon.impl.HighlightInfo
import com.intellij.lang.annotation.HighlightSeverity
import com.intellij.openapi.components.service
import com.intellij.testFramework.fixtures.BasePlatformTestCase

class PsaPhpMethodArgumentTypeInspectionTest : BasePlatformTestCase() {

    override fun setUp() {
        super.setUp()
        project.service<Settings>().pluginEnabled = true
        project.service<Settings>().scriptPath = "psa.sh"
        project.service<PhpPsaManager>().getSettings().enabled = true
        setupCallableProvider()
        myFixture.enableInspections(PsaPhpMethodArgumentTypeInspection())
    }

    fun testWrongClassArgumentIsHighlighted() {
        configure(parameterType = "Account ", argument = "5", strict = false)

        val highlight = parameterTypeHighlights().firstOrNull()
        assertNotNull(
            "An int passed where a class is required is a hard mismatch, flagged even without strict_types",
            highlight,
        )
        assertEquals(
            "Target method's file is not strict, so the mismatch is a warning",
            HighlightSeverity.WARNING,
            highlight!!.severity,
        )
    }

    fun testCompatibleClassArgumentIsNotHighlighted() {
        configure(parameterType = "Account ", argument = "new Account()", strict = false)

        assertFalse(
            "A matching class argument must not be flagged",
            hasParameterTypeWarning(),
        )
    }

    fun testScalarCoercionFlaggedAsErrorUnderStrictTypes() {
        configure(parameterType = "bool ", argument = "5", strict = true)

        val highlight = parameterTypeHighlights().firstOrNull()
        assertNotNull(
            "Under declare(strict_types=1) an int is not a valid bool and must be flagged",
            highlight,
        )
        assertEquals(
            "Target method's file is strict, so the mismatch is an error",
            HighlightSeverity.ERROR,
            highlight!!.severity,
        )
    }

    fun testSeverityFollowsTargetMethodFileNotCaller() {
        myFixture.addFileToProject(
            "Manager.php",
            """
            <?php
            declare(strict_types=1);
            class Account {}
            class AccountStatsManager {
                public function updateStats(Account ${'$'}account) {}
            }
            """.trimIndent(),
        )
        myFixture.configureByText(
            "Call.php",
            """
            <?php
            class ServiceMethodMessage {
                public function __construct(array ${'$'}callable, array ${'$'}arguments) {}
            }
            new ServiceMethodMessage([AccountStatsManager::class, 'updateStats'], [5]);
            """.trimIndent(),
        )

        val highlight = parameterTypeHighlights().firstOrNull()
        assertNotNull("Wrong class argument must be flagged across files", highlight)
        assertEquals(
            "Severity is driven by the target method's strict file, not the non-strict caller",
            HighlightSeverity.ERROR,
            highlight!!.severity,
        )
    }

    fun testScalarCoercionFlaggedAsWarningWithoutStrictTypes() {
        configure(parameterType = "bool ", argument = "5", strict = false)

        val highlight = parameterTypeHighlights().firstOrNull()
        assertNotNull(
            "An int is not a valid bool, so it is flagged even without strict_types",
            highlight,
        )
        assertEquals(
            "Without strict_types in the target method's file the mismatch is reported as a warning",
            HighlightSeverity.WARNING,
            highlight!!.severity,
        )
    }

    fun testUntypedParameterIsNotChecked() {
        configure(parameterType = "", argument = "5", strict = true)

        assertFalse(
            "Parameters without a declared type are never flagged",
            hasParameterTypeWarning(),
        )
    }

    fun testNoWarningWhenPluginDisabled() {
        project.service<Settings>().pluginEnabled = false
        configure(parameterType = "bool ", argument = "5", strict = true)

        assertFalse("Inspection must be inactive when the plugin is disabled", hasParameterTypeWarning())
    }

    fun testNoWarningWhenPhpExtensionDisabled() {
        project.service<PhpPsaManager>().getSettings().enabled = false
        configure(parameterType = "bool ", argument = "5", strict = true)

        assertFalse("Inspection must be inactive when the PHP extension is disabled", hasParameterTypeWarning())
    }

    private fun configure(
        parameterType: String,
        argument: String,
        strict: Boolean,
    ) {
        val declare = if (strict) "declare(strict_types=1);" else ""
        myFixture.configureByText(
            "Call.php",
            """
            <?php
            $declare
            class ServiceMethodMessage {
                public function __construct(array ${'$'}callable, array ${'$'}arguments) {}
            }
            class Account {}
            class AccountStatsManager {
                public function updateStats($parameterType${'$'}account) {}
            }
            new ServiceMethodMessage([AccountStatsManager::class, 'updateStats'], [$argument]);
            """.trimIndent(),
        )
    }

    private fun parameterTypeHighlights(): List<HighlightInfo> =
        myFixture.doHighlighting().filter { it.description?.contains("Expected parameter of type") == true }

    private fun hasParameterTypeWarning(): Boolean = parameterTypeHighlights().isNotEmpty()

    private fun setupCallableProvider() {
        project.service<PhpPsaManager>().getSettings().methodArgumentProviders = arrayListOf(
            MethodArgumentProviderModel().apply {
                `class` = "ServiceMethodMessage"
                method = "__construct"
                callableArgumentIndex = 0
                argumentsArgumentIndex = 1
            },
        )
    }
}
