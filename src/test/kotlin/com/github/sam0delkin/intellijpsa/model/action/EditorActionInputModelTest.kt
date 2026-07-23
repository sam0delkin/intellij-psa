package com.github.sam0delkin.intellijpsa.model.action

import com.intellij.testFramework.fixtures.BasePlatformTestCase
import kotlinx.serialization.json.Json

class EditorActionInputModelTest : BasePlatformTestCase() {
    fun testEditorActionInputModelSerialization() {
        val model =
            EditorActionInputModel(
                actionName = "my_action",
                fileName = "/path/to/file.php",
                text = "some code",
            )

        val encoded = Json.encodeToString(EditorActionInputModel.serializer(), model)
        val decoded = Json.decodeFromString(EditorActionInputModel.serializer(), encoded)

        assertEquals(model.actionName, decoded.actionName)
        assertEquals(model.fileName, decoded.fileName)
        assertEquals(model.text, decoded.text)
    }

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
