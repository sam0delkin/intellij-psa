package com.github.sam0delkin.intellijpsa.language.php.methodArguments

import com.intellij.psi.PsiElement
import com.intellij.testFramework.fixtures.BasePlatformTestCase
import com.jetbrains.php.lang.psi.elements.ConstantReference
import com.jetbrains.php.lang.psi.elements.Method

class PsaPhpMethodReferenceTest : BasePlatformTestCase() {
    fun testCalculateDefaultRangeInElementForConstantReference() {
        myFixture.configureByText(
            "test.php",
            """
            <?php
            class MyClass {
                public function doWork() {}
            }
            ${'$'}x = SOME_CONST;
            """.trimIndent(),
        )

        val constRef = myFixture.findElementByText("SOME_CONST", ConstantReference::class.java)!!
        val method = myFixture.findElementByText("doWork", Method::class.java)!!

        val reference = PsaPhpMethodReference(constRef, method)
        val range = reference.rangeInElement

        assertNotNull(range)
        assertEquals(0, range.startOffset)
        assertEquals(constRef.textLength, range.endOffset)
    }

    fun testCalculateDefaultRangeInElementFallsBackForOtherElementTypes() {
        myFixture.configureByText(
            "test.php",
            """
            <?php
            class MyClass {
                public function doWork() {}
            }
            """.trimIndent(),
        )

        val classElement = myFixture.findElementByText("MyClass", PsiElement::class.java)!!
        val method = myFixture.findElementByText("doWork", Method::class.java)!!

        val reference = PsaPhpMethodReference(classElement, method)
        val range = reference.rangeInElement

        assertNotNull(range)
    }
}
