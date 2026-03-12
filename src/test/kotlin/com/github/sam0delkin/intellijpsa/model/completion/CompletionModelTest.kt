package com.github.sam0delkin.intellijpsa.model.completion

import com.intellij.testFramework.fixtures.BasePlatformTestCase

class CompletionModelTest : BasePlatformTestCase() {
    fun testCompletionModelCreation() {
        val model =
            CompletionModel().apply {
                text = "My Completion"
                bold = true
                presentableText = "Presentable"
                tailText = "Tail"
                type = "MyType"
                priority = 123.0
                link = "/path/to/file.php:10:20"
            }

        assertEquals("My Completion", model.text)
        assertEquals(true, model.bold)
        assertEquals("Presentable", model.presentableText)
        assertEquals("Tail", model.tailText)
        assertEquals("MyType", model.type)
        assertEquals(123.0, model.priority)
        assertEquals("/path/to/file.php:10:20", model.link)
    }

    fun testCompletionModelDefaultValues() {
        val model = CompletionModel()

        assertNull(model.text)
        assertNull(model.bold)
        assertNull(model.presentableText)
        assertNull(model.tailText)
        assertNull(model.type)
        assertNull(model.priority)
        assertNull(model.link)
    }

    fun testNotificationModel() {
        val notification =
            NotificationModel().apply {
                type = "info"
                text = "Hello from PSA"
            }

        assertEquals("info", notification.type)
        assertEquals("Hello from PSA", notification.text)
    }

    fun testCompletionsModel() {
        val completions =
            CompletionsModel().apply {
                completions =
                    listOf(
                        CompletionModel().apply {
                            text = "Completion 1"
                            type = "Type1"
                        },
                        CompletionModel().apply {
                            text = "Completion 2"
                            type = "Type2"
                        },
                    )
                notifications =
                    listOf(
                        NotificationModel().apply {
                            type = "info"
                            text = "Info message"
                        },
                    )
            }

        assertEquals(2, completions.completions?.size)
        assertEquals(1, completions.notifications?.size)
        assertEquals("Completion 1", completions.completions?.get(0)?.text)
        assertEquals("Info message", completions.notifications?.get(0)?.text)
    }

    fun testCompletionsModelEmptyLists() {
        val model = CompletionsModel()

        assertNull(model.completions)
        assertNull(model.notifications)
    }
}
