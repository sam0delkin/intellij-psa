package com.github.sam0delkin.intellijpsa.language.php.model

import com.intellij.testFramework.fixtures.BasePlatformTestCase

class PhpInfoModelTest : BasePlatformTestCase() {
    fun testPhpInfoModelDefaultValues() {
        val model = PhpInfoModel()

        assertEquals(0, model.supportedLanguages.size)
        assertNull(model.goToElementFilter)
        assertNull(model.supportsBatch)
        assertNull(model.supportsTypeProviders)
        assertNull(model.toStringValueFormatter)
    }

    fun testPhpInfoModel() {
        val model =
            PhpInfoModel().apply {
                supportedLanguages.addAll(listOf("PHP"))
            }

        assertEquals(1, model.supportedLanguages.size)
        assertEquals("PHP", model.supportedLanguages[0])
    }

    fun testPhpInfoModelInheritsFromInfoModel() {
        val model =
            PhpInfoModel().apply {
                supportedLanguages.addAll(listOf("PHP", "JavaScript"))
            }

        assertEquals(2, model.supportedLanguages.size)
        assertTrue(model.supportedLanguages.contains("PHP"))
        assertTrue(model.supportedLanguages.contains("JavaScript"))
    }

    fun testPhpInfoModelWithEditorActions() {
        val model = PhpInfoModel()

        assertNotNull(model)
        assertEquals(0, model.supportedLanguages.size)
    }
}
