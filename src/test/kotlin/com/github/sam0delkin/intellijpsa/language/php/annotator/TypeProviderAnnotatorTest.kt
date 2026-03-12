package com.github.sam0delkin.intellijpsa.language.php.annotator

import com.github.sam0delkin.intellijpsa.language.php.services.PhpPsaManager
import com.github.sam0delkin.intellijpsa.language.php.typeProvider.PSA_TYPE_PROVIDER_ANNOTATOR_KEY
import com.github.sam0delkin.intellijpsa.settings.Settings
import com.intellij.lang.annotation.HighlightSeverity
import com.intellij.openapi.components.service
import com.intellij.psi.SmartPointerManager
import com.intellij.testFramework.fixtures.BasePlatformTestCase

class TypeProviderAnnotatorTest : BasePlatformTestCase() {
    override fun setUp() {
        super.setUp()
        val settings = project.service<Settings>()
        settings.pluginEnabled = true
        settings.scriptPath = "psa.sh"

        val phpSettings = project.service<PhpPsaManager>().getSettings()
        phpSettings.enabled = true
        phpSettings.supportsTypeProviders = true
        phpSettings.debugTypeProvider = true
    }

    fun testAnnotationCreatedWhenUserDataPresent() {
        myFixture.configureByText("test.php", "<?php 'some_string';")
        val element = myFixture.findElementByText("'some_string'", com.intellij.psi.PsiElement::class.java)

        // Find the leaf element which is actually passed to annotator in many cases,
        // or just the element itself. The annotator is called for all elements.
        val pointer = SmartPointerManager.createPointer(element)
        element.putUserData(PSA_TYPE_PROVIDER_ANNOTATOR_KEY, pointer)

        val annotations = myFixture.doHighlighting()

        val psaAnnotation = annotations.find { it.text != null && it.description != null && it.description.contains("PSA Type Provider:") }
        assertNotNull(
            "Should find PSA Type Provider annotation. Found: ${annotations.joinToString { "${it.severity}: ${it.description}" }}",
            psaAnnotation,
        )
    }

    fun testNoAnnotationWhenDebugTypeProviderDisabled() {
        val phpSettings = project.service<PhpPsaManager>().getSettings()
        phpSettings.debugTypeProvider = false

        myFixture.configureByText("test.php", "<?php 'some_string';")
        val element = myFixture.findElementByText("'some_string'", com.intellij.psi.PsiElement::class.java)
        val pointer = SmartPointerManager.createPointer(element)
        element.putUserData(PSA_TYPE_PROVIDER_ANNOTATOR_KEY, pointer)

        val annotations = myFixture.doHighlighting(HighlightSeverity.WARNING)

        val psaAnnotation = annotations.find { it.text.contains("PSA Type Provider:") }
        assertNull("Should NOT find PSA Type Provider annotation when debug is disabled", psaAnnotation)
    }

    fun testNoAnnotationWhenPluginDisabled() {
        val settings = project.service<Settings>()
        settings.pluginEnabled = false

        myFixture.configureByText("test.php", "<?php 'some_string';")
        val element = myFixture.findElementByText("'some_string'", com.intellij.psi.PsiElement::class.java)
        val pointer = SmartPointerManager.createPointer(element)
        element.putUserData(PSA_TYPE_PROVIDER_ANNOTATOR_KEY, pointer)

        val annotations = myFixture.doHighlighting(HighlightSeverity.WARNING)

        val psaAnnotation = annotations.find { it.text.contains("PSA Type Provider:") }
        assertNull(psaAnnotation)
    }

    fun testNoAnnotationWhenTargetElementIsNull() {
        myFixture.configureByText("test.php", "<?php 'some_string';")
        val element = myFixture.findElementByText("'some_string'", com.intellij.psi.PsiElement::class.java)

        // Create a pointer to an element that is then "deleted" (not really possible this way, but we can mock it by having a pointer to something else)
        // Or just use a pointer to an element that's no longer valid.
        // Actually, let's just create a custom pointer that returns null element if we could.
        // But the code says: val targetElement = pointer.element. So if pointer.element is null, it should return.

        // We can simulate this by pointing to an element and then making it invalid,
        // but it's easier to just assume the check `if (null != targetElement)` works.
        // Let's try to make it null by using a pointer to a temporary element.

        var pointer: com.intellij.psi.SmartPsiElementPointer<com.intellij.psi.PsiElement>? = null
        com.intellij.openapi.application.WriteAction.run<Throwable> {
            val tempElement =
                com.jetbrains.php.lang.psi.PhpPsiElementFactory.createFromText(
                    project,
                    com.jetbrains.php.lang.psi.elements.StringLiteralExpression::class.java,
                    "'temp'",
                )
            pointer = SmartPointerManager.createPointer(tempElement!!)
            // Try to make it invalid.
            tempElement.delete()
        }

        element.putUserData(PSA_TYPE_PROVIDER_ANNOTATOR_KEY, pointer)

        val annotations = myFixture.doHighlighting()
        val psaAnnotation = annotations.find { it.description != null && it.description.contains("PSA Type Provider:") }
        assertNull(
            "Should NOT find PSA Type Provider annotation when target element is invalid. Found: ${psaAnnotation?.description}",
            psaAnnotation,
        )
    }
}
