package com.github.sam0delkin.intellijpsa.psi

import com.intellij.testFramework.fixtures.BasePlatformTestCase

class PsaElementDocumentationProviderTest : BasePlatformTestCase() {
    fun testGetQuickNavigateInfoReturnsNullForNonPsaElement() {
        myFixture.configureByText("test.php", "<?php 'test_string';")
        val element = myFixture.findElementByText("'test_string'", com.intellij.psi.PsiElement::class.java)

        assertNull(PsaElementDocumentationProvider().getQuickNavigateInfo(element, null))
    }

    fun testGenerateDocReturnsNullForNonPsaElement() {
        myFixture.configureByText("test.php", "<?php 'test_string';")
        val element = myFixture.findElementByText("'test_string'", com.intellij.psi.PsiElement::class.java)

        assertNull(PsaElementDocumentationProvider().generateDoc(element, null))
    }

    fun testGetQuickNavigateInfoReturnsNullWithoutDeclarationParent() {
        myFixture.configureByText("test.php", "<?php 'test_string';")
        val element = myFixture.findElementByText("'test_string'", com.intellij.psi.PsiElement::class.java)
        val psaElement = PsaElement(element, "'test_string'")

        assertNull(PsaElementDocumentationProvider().getQuickNavigateInfo(psaElement, null))
    }

    fun testGetQuickNavigateInfoDelegatesToLanguageProviderForPhpMethod() {
        myFixture.configureByText(
            "test.php",
            """
            <?php
            class MyClass {
                public function myMethod() {
                    'test_string';
                }
            }
            """.trimIndent(),
        )
        val element = myFixture.findElementByText("'test_string'", com.intellij.psi.PsiElement::class.java)
        val psaElement = PsaElement(element, "'test_string'")

        val info = PsaElementDocumentationProvider().getQuickNavigateInfo(psaElement, element)

        assertNotNull(info)
        assertTrue(info!!.contains("myMethod"))
    }

    fun testGenerateDocDelegatesToLanguageProviderForPhpMethod() {
        myFixture.configureByText(
            "test.php",
            """
            <?php
            class MyClass {
                public function myMethod() {
                    'test_string';
                }
            }
            """.trimIndent(),
        )
        val element = myFixture.findElementByText("'test_string'", com.intellij.psi.PsiElement::class.java)
        val psaElement = PsaElement(element, "'test_string'")

        val doc = PsaElementDocumentationProvider().generateDoc(psaElement, element)

        assertNotNull(doc)
    }
}
