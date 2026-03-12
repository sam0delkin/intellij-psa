package com.github.sam0delkin.intellijpsa.model.typeProvider

import com.github.sam0delkin.intellijpsa.model.psi.PsiElementPatternModel
import com.intellij.testFramework.fixtures.BasePlatformTestCase

class TypeProviderModelTest : BasePlatformTestCase() {
    fun testTypeProviderModelDefaultValues() {
        val model = TypeProviderModel()

        assertEquals("", model.language)
        assertNull(model.pattern)
        assertEquals("", model.type)
    }

    fun testTypeProviderModel() {
        val pattern =
            PsiElementPatternModel(
                withText = "'test'",
                withType = "STRING_LITERAL",
            )

        val model =
            TypeProviderModel().apply {
                language = "PHP"
                this.pattern = pattern
                type = "\\MyCustomType"
            }

        assertEquals("PHP", model.language)
        assertNotNull(model.pattern)
        assertEquals("'test'", model.pattern?.withText)
        assertEquals("STRING_LITERAL", model.pattern?.withType)
        assertEquals("\\MyCustomType", model.type)
    }

    fun testTypeProvidersModel() {
        val model = TypeProvidersModel()

        assertNull(model.providers)
    }
}
