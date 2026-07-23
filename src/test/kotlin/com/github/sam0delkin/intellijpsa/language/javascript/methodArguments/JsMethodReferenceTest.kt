package com.github.sam0delkin.intellijpsa.language.javascript.methodArguments

import com.github.sam0delkin.intellijpsa.language.javascript.settings.JsPsaSettings
import com.github.sam0delkin.intellijpsa.settings.Settings
import com.intellij.lang.javascript.psi.JSFunction
import com.intellij.lang.javascript.psi.JSLiteralExpression
import com.intellij.openapi.command.WriteCommandAction
import com.intellij.openapi.components.service
import com.intellij.testFramework.fixtures.BasePlatformTestCase

class JsMethodReferenceTest : BasePlatformTestCase() {
    override fun setUp() {
        super.setUp()
        project.service<Settings>().pluginEnabled = true
        project.service<Settings>().scriptPath = "psa.sh"
        project.service<JsPsaSettings>().enabled = true
        project.service<JsPsaSettings>().methodArgumentProviders =
            arrayListOf(
                com.github.sam0delkin.intellijpsa.language.javascript.model.JsMethodArgumentProviderModel().apply {
                    referenceName = "advanceLender"
                    `class` = "AdvanceLender"
                    methodArgumentIndex = 0
                    argumentsOffset = 1
                },
            )
    }

    private fun createReference(): JsMethodReference {
        myFixture.configureByText(
            "plugin.js",
            """
            function AdvanceLender(lender, options) {}
            AdvanceLender.prototype.updateTrancheReferrers = function (${'$'}tranche) {};
            foo.advanceLender('updateTrancheReferrers', bar);
            """.trimIndent(),
        )
        val literal = myFixture.findElementByText("'updateTrancheReferrers'", JSLiteralExpression::class.java)!!
        return literal.references.filterIsInstance<JsMethodReference>().first()
    }

    fun testIsReferenceToTargetFunction() {
        val ref = createReference()
        val resolved = ref.resolve() as JSFunction

        assertTrue(ref.isReferenceTo(resolved))
        assertTrue(ref.isReferenceTo(resolved.nameIdentifier!!))
    }

    fun testIsReferenceToFalseForOtherElement() {
        val ref = createReference()

        assertFalse(ref.isReferenceTo(ref.element))
    }

    fun testCalculateDefaultRangeInElementForQuotedString() {
        val ref = createReference()

        val range = ref.rangeInElement

        assertEquals(1, range.startOffset)
        assertEquals(ref.element.textLength - 1, range.endOffset)
    }

    fun testHandleElementRenameUpdatesStringContent() {
        val ref = createReference()

        val renamed =
            WriteCommandAction.runWriteCommandAction<com.intellij.psi.PsiElement>(project) {
                ref.handleElementRename("newMethodName")
            }

        assertTrue(renamed.text.contains("newMethodName"))
    }
}
