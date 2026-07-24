package com.github.sam0delkin.intellijpsa.psi

import com.intellij.psi.ElementDescriptionLocation
import com.intellij.psi.ElementDescriptionProvider
import com.intellij.testFramework.fixtures.BasePlatformTestCase
import com.intellij.usageView.UsageViewLongNameLocation
import com.intellij.usageView.UsageViewNodeTextLocation
import com.intellij.usageView.UsageViewTypeLocation

class PsaElementDescriptionProviderTest : BasePlatformTestCase() {
    private fun createPsaElement(): PsaElement {
        myFixture.configureByText("test.php", "<?php 'test_string';")
        val element = myFixture.findElementByText("'test_string'", com.intellij.psi.PsiElement::class.java)
        return PsaElement(element, "test_string")
    }

    fun testReturnsNullForNonPsaElement() {
        myFixture.configureByText("test.php", "<?php 'test_string';")
        val element = myFixture.findElementByText("'test_string'", com.intellij.psi.PsiElement::class.java)

        val description = PsaElementDescriptionProvider().getElementDescription(element, UsageViewTypeLocation.INSTANCE)

        assertNull(description)
    }

    fun testReturnsPsaForTypeLocation() {
        val psaElement = createPsaElement()

        val description = PsaElementDescriptionProvider().getElementDescription(psaElement, UsageViewTypeLocation.INSTANCE)

        assertEquals("PSA", description)
    }

    fun testReturnsPresentableTextForLongNameLocation() {
        val psaElement = createPsaElement()

        val description =
            PsaElementDescriptionProvider().getElementDescription(psaElement, UsageViewLongNameLocation.INSTANCE)

        assertEquals(psaElement.presentation.presentableText, description)
    }

    fun testReturnsPresentableTextForNodeTextLocation() {
        val psaElement = createPsaElement()

        val description =
            PsaElementDescriptionProvider().getElementDescription(psaElement, UsageViewNodeTextLocation.INSTANCE)

        assertEquals(psaElement.presentation.presentableText, description)
    }

    fun testReturnsNullForOtherLocations() {
        val psaElement = createPsaElement()
        val otherLocation =
            object : ElementDescriptionLocation() {
                override fun getDefaultProvider(): ElementDescriptionProvider? = null
            }

        val description = PsaElementDescriptionProvider().getElementDescription(psaElement, otherLocation)

        assertNull(description)
    }
}
