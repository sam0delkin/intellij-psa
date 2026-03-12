package com.github.sam0delkin.intellijpsa.language.php.typeProvider

import com.github.sam0delkin.intellijpsa.language.php.services.PhpPsaManager
import com.github.sam0delkin.intellijpsa.model.psi.PsiElementPatternModel
import com.github.sam0delkin.intellijpsa.model.typeProvider.TypeProviderModel
import com.github.sam0delkin.intellijpsa.settings.Settings
import com.intellij.openapi.components.service
import com.intellij.psi.PsiElement
import com.intellij.testFramework.fixtures.BasePlatformTestCase
import com.jetbrains.php.lang.PhpLanguage
import com.jetbrains.php.lang.psi.resolve.types.PhpTypeProvider4

class PsaTypeProviderTest : BasePlatformTestCase() {
    private lateinit var typeProvider: PsaTypeProvider

    override fun setUp() {
        super.setUp()
        typeProvider = PhpTypeProvider4.EP_NAME.findExtensionOrFail(PsaTypeProvider::class.java)

        val settings = project.service<Settings>()
        settings.pluginEnabled = true
        settings.scriptPath = "psa.sh"

        val phpSettings = project.service<PhpPsaManager>().getSettings()
        phpSettings.enabled = true
        phpSettings.supportsTypeProviders = true
    }

    fun testGetTypeMatchesPattern() {
        val phpSettings = project.service<PhpPsaManager>().getSettings()
        val pattern =
            PsiElementPatternModel(
                withText = "'some_string'",
                withType = "String",
            )
        val providerModel =
            TypeProviderModel().apply {
                language = PhpLanguage.INSTANCE.id
                this.pattern = pattern
                type = "\\MyCustomType"
            }
        phpSettings.typeProviders = arrayListOf(providerModel)

        myFixture.configureByText("test.php", "<?php 'some_string';")
        val element = myFixture.findElementByText("'some_string'", PsiElement::class.java)

        val type = typeProvider.getType(element)

        assertNotNull(type)
        assertTrue(type!!.types.contains("\\MyCustomType"))
        assertEquals("\\MyCustomType", element.getUserData(PSA_TYPE_KEY))
    }

    fun testNoTypeWhenPluginDisabled() {
        val settings = project.service<Settings>()
        settings.pluginEnabled = false

        myFixture.configureByText("test.php", "<?php 'some_string';")
        val element = myFixture.findElementByText("'some_string'", PsiElement::class.java)

        val type = typeProvider.getType(element)

        assertNull(type)
    }

    fun testNoTypeWhenPhpSettingsDisabled() {
        val phpSettings = project.service<PhpPsaManager>().getSettings()
        phpSettings.enabled = false

        myFixture.configureByText("test.php", "<?php 'some_string';")
        val element = myFixture.findElementByText("'some_string'", PsiElement::class.java)

        val type = typeProvider.getType(element)

        assertNull(type)
    }

    fun testNoTypeWhenPatternDoesNotMatch() {
        val phpSettings = project.service<PhpPsaManager>().getSettings()
        val pattern =
            PsiElementPatternModel(
                withText = "'other_string'",
            )
        val providerModel =
            TypeProviderModel().apply {
                language = PhpLanguage.INSTANCE.id
                this.pattern = pattern
                type = "\\MyCustomType"
            }
        phpSettings.typeProviders = arrayListOf(providerModel)

        myFixture.configureByText("test.php", "<?php 'some_string';")
        val element = myFixture.findElementByText("'some_string'", PsiElement::class.java)

        val type = typeProvider.getType(element)

        assertNull(type)
    }
}
