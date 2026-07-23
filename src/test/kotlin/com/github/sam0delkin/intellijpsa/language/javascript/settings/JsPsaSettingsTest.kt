package com.github.sam0delkin.intellijpsa.language.javascript.settings

import com.github.sam0delkin.intellijpsa.language.javascript.model.JsMethodArgumentProviderModel
import com.intellij.testFramework.fixtures.BasePlatformTestCase
import com.intellij.util.xmlb.XmlSerializer

class JsPsaSettingsTest : BasePlatformTestCase() {
    fun testStateSurvivesXmlSerializerRoundTrip() {
        val settings = JsPsaSettings()
        settings.enabled = true
        settings.methodArgumentProvidersInspectionsEnabled = true
        settings.methodArgumentProviders =
            arrayListOf(
                JsMethodArgumentProviderModel().apply {
                    referenceName = "advanceLender"
                    `class` = "AdvanceLender"
                    methodArgumentIndex = 0
                    argumentsOffset = 1
                },
            )

        val element = XmlSerializer.serialize(settings.state)
        val deserialized = XmlSerializer.deserialize(element, JsPsaSettings::class.java)

        val restored = JsPsaSettings()
        restored.loadState(deserialized)

        assertTrue(restored.enabled)
        assertTrue(restored.methodArgumentProvidersInspectionsEnabled)

        val provider = restored.methodArgumentProviders!!.single()
        assertEquals("advanceLender", provider.referenceName)
        assertEquals("AdvanceLender", provider.`class`)
    }

    fun testEmptyStateRoundTripsWithoutProviders() {
        val settings = JsPsaSettings()
        settings.enabled = true

        val element = XmlSerializer.serialize(settings.state)
        val restored = JsPsaSettings()
        restored.loadState(XmlSerializer.deserialize(element, JsPsaSettings::class.java))

        assertTrue(restored.enabled)
        assertNull(restored.methodArgumentProviders)
    }
}
