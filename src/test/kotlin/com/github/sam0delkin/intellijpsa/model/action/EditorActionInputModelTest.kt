package com.github.sam0delkin.intellijpsa.model.action

import com.intellij.testFramework.fixtures.BasePlatformTestCase

class EditorActionInputModelTest : BasePlatformTestCase() {
    fun testEditorActionInputModelConstructor() {
        val model =
            EditorActionInputModel(
                actionName = "my_action",
                fileName = "/path/to/file.php",
            )

        assertEquals("my_action", model.actionName)
        assertEquals("/path/to/file.php", model.fileName)
        assertNull(model.text)
    }

    fun testEditorActionInputModelWithText() {
        val model =
            EditorActionInputModel(
                actionName = "my_action",
                fileName = "/path/to/file.php",
                text = "some code here",
            )

        assertEquals("my_action", model.actionName)
        assertEquals("/path/to/file.php", model.fileName)
        assertEquals("some code here", model.text)
    }

    fun testEditorActionInputModelCopy() {
        val original =
            EditorActionInputModel(
                actionName = "action",
                fileName = "/file.php",
                text = "text",
            )

        val copy =
            EditorActionInputModel(
                actionName = original.actionName,
                fileName = original.fileName,
                text = original.text,
            )

        assertEquals(original.actionName, copy.actionName)
        assertEquals(original.fileName, copy.fileName)
        assertEquals(original.text, copy.text)
    }
}
