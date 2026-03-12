package com.github.sam0delkin.intellijpsa.language.php.xdebugger.value.presentation

import com.intellij.testFramework.fixtures.BasePlatformTestCase
import com.intellij.xdebugger.frame.presentation.XValuePresentation

class PsaPhpValuePresentationTest : BasePlatformTestCase() {
    fun testRenderValue() {
        val presentation = PsaPhpValuePresentation("custom_value", "string")
        var renderedValue: String? = null

        presentation.renderValue(
            object : XValuePresentation.XValueTextRenderer {
                override fun renderValue(value: String) {
                    renderedValue = value
                }

                override fun renderValue(
                    value: String,
                    key: com.intellij.openapi.editor.colors.TextAttributesKey,
                ) {
                    renderedValue = value
                }

                override fun renderStringValue(value: String) {
                    renderedValue = value
                }

                override fun renderStringValue(
                    value: String,
                    additionalSpecialCharsToHighlight: String?,
                    maxLength: Int,
                ) {
                    renderedValue = value
                }

                override fun renderNumericValue(value: String) {
                    renderedValue = value
                }

                override fun renderKeywordValue(value: String) {
                    renderedValue = value
                }

                override fun renderComment(value: String) {
                    renderedValue = value
                }

                override fun renderSpecialSymbol(value: String) {
                    renderedValue = value
                }

                override fun renderError(value: String) {
                    renderedValue = value
                }
            },
        )

        assertEquals("custom_value", renderedValue)
    }
}
