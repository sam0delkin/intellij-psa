package com.github.sam0delkin.intellijpsa.psi.reference

import com.intellij.testFramework.fixtures.BasePlatformTestCase

class PsaReferenceTest : BasePlatformTestCase() {
    fun testPsaReferenceCreation() {
        myFixture.configureByText("test.php", "<?php 'test_string';")
        val element = myFixture.findElementByText("'test_string'", com.intellij.psi.PsiElement::class.java)
        val referenceElement = element

        val psaReference = PsaReference(element, referenceElement)

        assertNotNull(psaReference)
        assertEquals(element, psaReference.element)
        assertEquals(referenceElement, psaReference.reference)
    }

    fun testPsaReferenceWithStaticCompletionName() {
        myFixture.configureByText("test.php", "<?php 'test_string';")
        val element = myFixture.findElementByText("'test_string'", com.intellij.psi.PsiElement::class.java)
        val referenceElement = element

        val psaReference = PsaReference(element, referenceElement, "static_completion")

        assertNotNull(psaReference)
        assertEquals("static_completion", psaReference.staticCompletionName)
    }

    fun testPsaReferenceResolve() {
        myFixture.configureByText("test.php", "<?php 'test_string';")
        val element = myFixture.findElementByText("'test_string'", com.intellij.psi.PsiElement::class.java)
        val referenceElement = element

        val psaReference = PsaReference(element, referenceElement)

        val resolved = psaReference.resolve()

        assertNotNull(resolved)
        assertEquals(referenceElement, resolved)
    }

    fun testPsaReferenceDefaultRangeInElement() {
        myFixture.configureByText("test.php", "<?php 'test_string';")
        val element = myFixture.findElementByText("'test_string'", com.intellij.psi.PsiElement::class.java)
        val referenceElement = element

        val psaReference = PsaReference(element, referenceElement)

        val range = psaReference.rangeInElement

        assertNotNull(range)
    }

    fun testPsaReferenceIsReferenceTo() {
        myFixture.configureByText("test.php", "<?php 'test_string';")
        val element = myFixture.findElementByText("'test_string'", com.intellij.psi.PsiElement::class.java)
        val referenceElement = element

        val psaReference = PsaReference(element, referenceElement)

        assertTrue(psaReference.isReferenceTo(element))

        val otherElement = myFixture.findElementByText("<?php", com.intellij.psi.PsiElement::class.java)
        assertTrue(psaReference.isReferenceTo(otherElement))
    }

    fun testPsaReferenceHandleElementRename() {
        myFixture.configureByText("test.php", "<?php 'test_string';")
        val element = myFixture.findElementByText("'test_string'", com.intellij.psi.PsiElement::class.java)
        val referenceElement = element

        val psaReference = PsaReference(element, referenceElement)

        val result = psaReference.handleElementRename("new_name")

        assertNotNull(result)
    }

    fun testPsaReferenceWithValueElement() {
        myFixture.configureByText(
            "test.php",
            """
            <?php
            class MyClass {
                public function getName(): string {
                    return 'MyClass';
                }
            }
            """.trimIndent(),
        )
        val element = myFixture.findElementByText("'MyClass'", com.intellij.psi.PsiElement::class.java)

        val psaReference = PsaReference(element, element, "MyClass")

        assertNotNull(psaReference)
        assertEquals("MyClass", psaReference.staticCompletionName)

        val resolved = psaReference.resolve()
        assertNotNull(resolved)
    }

    fun testPsaReferenceWithDifferentElements() {
        myFixture.configureByText("test.php", "<?php function myFunc() {}")
        val element = myFixture.findElementByText("myFunc", com.intellij.psi.PsiElement::class.java)
        val referenceElement = element

        val psaReference = PsaReference(element, referenceElement)

        assertNotNull(psaReference)

        val resolved = psaReference.resolve()
        assertNotNull(resolved)

        assertTrue(psaReference.isReferenceTo(element))
    }

    fun testPsaReferenceTextRange() {
        myFixture.configureByText("test.php", "<?php 'test';")
        val element = myFixture.findElementByText("'test'", com.intellij.psi.PsiElement::class.java)

        val psaReference = PsaReference(element, element)

        val range = psaReference.rangeInElement

        assertNotNull(range)
        assertTrue(range.startOffset >= 0)
        assertTrue(range.endOffset <= element.textLength)
        assertEquals(element.textLength, range.length + 2)
    }
}
