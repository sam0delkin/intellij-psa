package com.github.sam0delkin.intellijpsa.ide.presentation

import com.github.sam0delkin.intellijpsa.psi.PsaElement
import com.intellij.testFramework.fixtures.BasePlatformTestCase

class IconProviderTest : BasePlatformTestCase() {
    fun testGetIconReturnsPluginIconForPsaElement() {
        myFixture.configureByText("test.php", "<?php 'test_string';")
        val element = myFixture.findElementByText("'test_string'", com.intellij.psi.PsiElement::class.java)
        val psaElement = PsaElement(element, "test_string")

        val icon = IconProvider().getIcon(psaElement, 0)

        assertNotNull(icon)
    }

    fun testGetIconReturnsNullForOtherElements() {
        myFixture.configureByText("test.php", "<?php 'test_string';")
        val element = myFixture.findElementByText("'test_string'", com.intellij.psi.PsiElement::class.java)

        val icon = IconProvider().getIcon(element, 0)

        assertNull(icon)
    }
}
