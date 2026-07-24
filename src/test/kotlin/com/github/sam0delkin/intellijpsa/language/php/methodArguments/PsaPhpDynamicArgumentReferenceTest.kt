package com.github.sam0delkin.intellijpsa.language.php.methodArguments

import com.intellij.testFramework.fixtures.BasePlatformTestCase
import com.jetbrains.php.lang.psi.elements.Parameter
import com.jetbrains.php.lang.psi.elements.StringLiteralExpression

class PsaPhpDynamicArgumentReferenceTest : BasePlatformTestCase() {
    fun testIsReferenceToTargetParameter() {
        myFixture.configureByText(
            "Call.php",
            """
            <?php
            class MyService {
                public function doWork(${'$'}payload) {}
            }
            ${'$'}x = 'payload';
            """.trimIndent(),
        )

        val parameter = myFixture.findElementByText("${'$'}payload", Parameter::class.java)
        val argElement = myFixture.findElementByText("'payload'", StringLiteralExpression::class.java)
        assertNotNull(parameter)
        assertNotNull(argElement)

        val reference = PsaPhpDynamicArgumentReference(argElement!!, parameter!!)

        assertSame(parameter, reference.resolve())
        assertTrue(reference.isReferenceTo(parameter))
    }

    fun testIsReferenceToFalseForOtherElement() {
        myFixture.configureByText(
            "Call.php",
            """
            <?php
            class MyService {
                public function doWork(${'$'}payload, ${'$'}other) {}
            }
            ${'$'}x = 'payload';
            """.trimIndent(),
        )

        val parameter = myFixture.findElementByText("${'$'}payload", Parameter::class.java)
        val otherParameter = myFixture.findElementByText("${'$'}other", Parameter::class.java)
        val argElement = myFixture.findElementByText("'payload'", StringLiteralExpression::class.java)
        assertNotNull(parameter)
        assertNotNull(otherParameter)
        assertNotNull(argElement)

        val reference = PsaPhpDynamicArgumentReference(argElement!!, parameter!!)

        assertFalse(reference.isReferenceTo(otherParameter!!))
    }
}
