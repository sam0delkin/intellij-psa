package com.github.sam0delkin.intellijpsa.util

import com.intellij.testFramework.fixtures.BasePlatformTestCase

class PsiUtilsTest : BasePlatformTestCase() {
    fun testNormalizeElementTextWithSingleQuotes() {
        val text = "'test_string'"
        val normalized = PsiUtils.normalizeElementText(text)

        assertEquals("test_string", normalized)
    }

    fun testNormalizeElementTextWithDoubleQuotes() {
        val text = "\"test_string\""
        val normalized = PsiUtils.normalizeElementText(text)

        assertEquals("test_string", normalized)
    }

    fun testNormalizeElementTextWithoutQuotes() {
        val text = "test_string"
        val normalized = PsiUtils.normalizeElementText(text)

        assertEquals("test_string", normalized)
    }

    fun testNormalizeElementTextWithEmptyString() {
        val text = ""
        val normalized = PsiUtils.normalizeElementText(text)

        assertEquals("", normalized)
    }

    fun testNormalizeElementTextWithSingleChar() {
        val text = "a"
        val normalized = PsiUtils.normalizeElementText(text)

        assertEquals("a", normalized)
    }

    fun testNormalizeElementTextWithSingleQuoteChar() {
        val text = "''"
        val normalized = PsiUtils.normalizeElementText(text)

        assertEquals("", normalized)
    }

    fun testNormalizeElementTextWithDoubleQuoteChar() {
        val text = "\"\""
        val normalized = PsiUtils.normalizeElementText(text)

        assertEquals("", normalized)
    }

    fun testNormalizeElementTextWithNestedQuotes() {
        val text = "'\"nested\"'"
        val normalized = PsiUtils.normalizeElementText(text)

        assertEquals("nested", normalized)
    }

    fun testNormalizeElementTextWithPartialQuotes() {
        val text = "'test"
        val normalized = PsiUtils.normalizeElementText(text)

        assertEquals("'test", normalized)
    }

    fun testGetPrevElementByType() {
        myFixture.configureByText("test.php", "<?php \$var = 'value';")

        val assignmentElement = myFixture.findElementByText("value", com.intellij.psi.PsiElement::class.java)
        val prevElement = PsiUtils.getPrevElementByType(assignmentElement, "VARIABLE")

        assertNotNull(prevElement)
        assertEquals("\$var", prevElement!!.text)
    }

    fun testGetPrevElementByTypeNotFound() {
        myFixture.configureByText("test.php", "<?php 'value';")

        val stringElement = myFixture.findElementByText("'value'", com.intellij.psi.PsiElement::class.java)
        val prevElement = PsiUtils.getPrevElementByType(stringElement, "NON_EXISTENT_TYPE")

        assertNull(prevElement)
    }

    fun testGetPrevElementByTypeWithNullElement() {
        val prevElement = PsiUtils.getPrevElementByType(null, "VARIABLE")

        assertNull(prevElement)
    }

    fun testNormalizeElementTextWithWhitespace() {
        val text = "'  spaces  '"
        val normalized = PsiUtils.normalizeElementText(text)

        assertEquals("  spaces  ", normalized)
    }

    fun testNormalizeElementTextWithNumbers() {
        val text = "'12345'"
        val normalized = PsiUtils.normalizeElementText(text)

        assertEquals("12345", normalized)
    }

    fun testNormalizeElementTextWithSpecialChars() {
        val text = "'!@#\$%^&*()'"
        val normalized = PsiUtils.normalizeElementText(text)

        assertEquals("!@#\$%^&*()", normalized)
    }
}
