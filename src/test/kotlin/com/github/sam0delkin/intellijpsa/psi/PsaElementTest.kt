package com.github.sam0delkin.intellijpsa.psi

import com.github.sam0delkin.intellijpsa.icons.Icons
import com.intellij.testFramework.fixtures.BasePlatformTestCase

class PsaElementTest : BasePlatformTestCase() {
    fun testPsaElementCreation() {
        myFixture.configureByText("test.php", "<?php 'test_string';")
        val element = myFixture.findElementByText("'test_string'", com.intellij.psi.PsiElement::class.java)

        val psaElement = PsaElement(element, "test_string")

        assertNotNull(psaElement)
        assertEquals(element, psaElement.getOriginalPsiElement())
        assertEquals(element, psaElement.originalElement)
    }

    fun testPsaElementParent() {
        myFixture.configureByText("test.php", "<?php 'test_string';")
        val element = myFixture.findElementByText("'test_string'", com.intellij.psi.PsiElement::class.java)

        val psaElement = PsaElement(element, "test_string")

        assertNotNull(psaElement.parent)
        assertEquals(element.parent, psaElement.parent)
    }

    fun testPsaElementPresentation() {
        myFixture.configureByText("test.php", "<?php 'test_string';")
        val element = myFixture.findElementByText("'test_string'", com.intellij.psi.PsiElement::class.java)

        val psaElement = PsaElement(element, "test_string")

        val presentation = psaElement.presentation
        assertNotNull(presentation)

        val presentableText = presentation.presentableText
        assertNotNull(presentableText)
    }

    fun testPsaElementLocationString() {
        myFixture.configureByText("test.php", "<?php\n'test_string';")
        val element = myFixture.findElementByText("'test_string'", com.intellij.psi.PsiElement::class.java)

        val psaElement = PsaElement(element, "test_string")

        val presentation = psaElement.presentation
        val locationString = presentation.locationString

        assertNotNull(locationString)
        assertTrue(locationString!!.contains("test.php"))
    }

    fun testPsaElementIcon() {
        myFixture.configureByText("test.php", "<?php 'test_string';")
        val element = myFixture.findElementByText("'test_string'", com.intellij.psi.PsiElement::class.java)

        val psaElement = PsaElement(element, "test_string")

        val presentation = psaElement.presentation
        val icon = presentation.getIcon(false)

        assertEquals(Icons.PluginIcon, icon)
    }

    fun testPsaElementToString() {
        myFixture.configureByText("test.php", "<?php 'test_string';")
        val element = myFixture.findElementByText("'test_string'", com.intellij.psi.PsiElement::class.java)

        val psaElement = PsaElement(element, "test_string")

        val str = psaElement.toString()

        assertTrue(str.contains("test_string"))
        assertTrue(str.contains("test.php"))
    }

    fun testPsaElementNavigationElement() {
        myFixture.configureByText("test.php", "<?php 'test_string';")
        val element = myFixture.findElementByText("'test_string'", com.intellij.psi.PsiElement::class.java)

        val psaElement = PsaElement(element, "test_string")

        val navigationElement = psaElement.navigationElement

        assertNotNull(navigationElement)
        assertEquals(element, navigationElement)
    }

    fun testPsaElementIsValid() {
        myFixture.configureByText("test.php", "<?php 'test_string';")
        val element = myFixture.findElementByText("'test_string'", com.intellij.psi.PsiElement::class.java)

        val psaElement = PsaElement(element, "test_string")

        assertTrue(psaElement.isValid)
    }

    fun testPsaElementNode() {
        myFixture.configureByText("test.php", "<?php 'test_string';")
        val element = myFixture.findElementByText("'test_string'", com.intellij.psi.PsiElement::class.java)

        val psaElement = PsaElement(element, "test_string")

        val node = psaElement.node

        assertNotNull(node)
        assertEquals(element.node, node)
    }

    fun testPsaElementGetOriginalElement() {
        myFixture.configureByText("test.php", "<?php 'test_string';")
        val element = myFixture.findElementByText("'test_string'", com.intellij.psi.PsiElement::class.java)

        val psaElement = PsaElement(element, "test_string")

        val originalElement = psaElement.originalElement

        assertNotNull(originalElement)
        assertEquals(element, originalElement)
    }
}
