package com.github.sam0delkin.intellijpsa.language.php.methodArguments

import com.intellij.codeInsight.hints.ChangeListener
import com.intellij.testFramework.fixtures.BasePlatformTestCase

class PsaPhpMethodArgumentInlayHintsProviderTest : BasePlatformTestCase() {
    fun testPreviewTextAndNameAreNotBlank() {
        val provider = PsaPhpMethodArgumentInlayHintsProvider()

        assertTrue(provider.previewText.isNotBlank())
        assertTrue(provider.name.isNotBlank())
    }

    @Suppress("UnstableApiUsage")
    fun testCreateConfigurableReturnsEmptyComponent() {
        val provider = PsaPhpMethodArgumentInlayHintsProvider()
        val configurable = provider.createConfigurable(provider.createSettings())

        val component =
            configurable.createComponent(
                object : ChangeListener {
                    override fun settingsChanged() {}
                },
            )

        assertNotNull(component)
    }
}
