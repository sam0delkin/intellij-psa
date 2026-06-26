package com.github.sam0delkin.intellijpsa.language.php.settings

import com.github.sam0delkin.intellijpsa.language.php.model.MethodArgumentProviderModel
import com.github.sam0delkin.intellijpsa.model.psi.PsiElementPatternModel
import com.github.sam0delkin.intellijpsa.model.typeProvider.TypeProviderModel
import com.intellij.testFramework.fixtures.BasePlatformTestCase
import com.intellij.util.xmlb.XmlSerializer

class PhpPsaSettingsTest : BasePlatformTestCase() {
    fun testStateSurvivesXmlSerializerRoundTrip() {
        val settings = PhpPsaSettings()
        settings.enabled = true
        settings.supportsTypeProviders = true
        settings.toStringValueFormatter = "return (string) \$value;"
        settings.typeProviders =
            arrayListOf(
                TypeProviderModel().apply {
                    language = "PHP"
                    type = "\\App\\Foo"
                    pattern =
                        PsiElementPatternModel().apply {
                            withType = "METHOD_REFERENCE"
                            withText = "get"
                            withOptions = mapOf("key" to "value")
                            anyParent = PsiElementPatternModel().apply { withType = "Statement" }
                        }
                },
            )
        settings.methodArgumentProviders =
            arrayListOf(
                MethodArgumentProviderModel().apply {
                    `class` = "ServiceMethodMessage"
                    method = "__construct"
                    callableArgumentIndex = 0
                    argumentsArgumentIndex = 1
                },
            )

        val element = XmlSerializer.serialize(settings.state)
        val deserialized = XmlSerializer.deserialize(element, PhpPsaSettings::class.java)

        val restored = PhpPsaSettings()
        restored.loadState(deserialized)

        assertTrue(restored.enabled)
        assertTrue(restored.supportsTypeProviders)
        assertEquals("return (string) \$value;", restored.toStringValueFormatter)

        val typeProvider = restored.typeProviders!!.single()
        assertEquals("PHP", typeProvider.language)
        assertEquals("\\App\\Foo", typeProvider.type)
        assertEquals("METHOD_REFERENCE", typeProvider.pattern!!.withType)
        assertEquals("get", typeProvider.pattern!!.withText)
        assertEquals("value", typeProvider.pattern!!.withOptions!!["key"])
        assertEquals("Statement", typeProvider.pattern!!.anyParent!!.withType)

        val argProvider = restored.methodArgumentProviders!!.single()
        assertEquals("ServiceMethodMessage", argProvider.`class`)
        assertEquals(0, argProvider.callableArgumentIndex)
        assertEquals(1, argProvider.argumentsArgumentIndex)
    }

    fun testEmptyStateRoundTripsWithoutProviders() {
        val settings = PhpPsaSettings()
        settings.enabled = true

        val element = XmlSerializer.serialize(settings.state)
        val restored = PhpPsaSettings()
        restored.loadState(XmlSerializer.deserialize(element, PhpPsaSettings::class.java))

        assertTrue(restored.enabled)
        assertNull(restored.typeProviders)
        assertNull(restored.methodArgumentProviders)
    }
}
