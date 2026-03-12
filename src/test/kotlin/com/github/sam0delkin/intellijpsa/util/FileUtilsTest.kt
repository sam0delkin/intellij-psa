package com.github.sam0delkin.intellijpsa.util

import com.intellij.testFramework.fixtures.BasePlatformTestCase
import java.io.File

class FileUtilsTest : BasePlatformTestCase() {
    fun testWriteToTmpFile() {
        val content = "test content"
        val filePath = FileUtils.writeToTmpFile("test", content)

        assertNotNull(filePath)
        assertTrue(filePath.contains("test"))
        assertTrue(filePath.endsWith(".tmp"))

        val file = File(filePath)
        assertTrue(file.exists())
        assertEquals(content, file.readText())

        file.delete()
    }

    fun testWriteToTmpFileWithDifferentContent() {
        val content1 = "content 1"
        val content2 = "content 2"

        val filePath1 = FileUtils.writeToTmpFile("test1", content1)
        val filePath2 = FileUtils.writeToTmpFile("test2", content2)

        val file1 = File(filePath1)
        val file2 = File(filePath2)

        assertEquals(content1, file1.readText())
        assertEquals(content2, file2.readText())

        file1.delete()
        file2.delete()
    }

    fun testWriteToTmpFileWithEmptyContent() {
        val content = ""
        val filePath = FileUtils.writeToTmpFile("test_empty", content)

        val file = File(filePath)
        assertTrue(file.exists())
        assertEquals(content, file.readText())

        file.delete()
    }

    fun testWriteToTmpFileWithMultilineContent() {
        val content =
            """
            line 1
            line 2
            line 3
            """.trimIndent()

        val filePath = FileUtils.writeToTmpFile("test_multiline", content)

        val file = File(filePath)
        assertTrue(file.exists())
        assertEquals(content, file.readText())

        file.delete()
    }

    fun testWriteToTmpFileWithSpecialCharacters() {
        val content = "Special chars: !@#\$%^&*()_+-=[]{}|;':\",./<>?"

        val filePath = FileUtils.writeToTmpFile("test_special", content)

        val file = File(filePath)
        assertTrue(file.exists())
        assertEquals(content, file.readText())

        file.delete()
    }

    fun testWriteToTmpFileWithJsonContent() {
        val content =
            """
            {
                "key": "value",
                "array": [1, 2, 3],
                "nested": {
                    "inner": "data"
                }
            }
            """.trimIndent()

        val filePath = FileUtils.writeToTmpFile("test_json", content)

        val file = File(filePath)
        assertTrue(file.exists())
        assertEquals(content, file.readText())

        file.delete()
    }

    fun testWriteToTmpFileWithUnicodeContent() {
        val content = "Unicode: Привет мир! 你好世界！こんにちは世界！"

        val filePath = FileUtils.writeToTmpFile("test_unicode", content)

        val file = File(filePath)
        assertTrue(file.exists())
        assertEquals(content, file.readText())

        file.delete()
    }

    fun testWriteToTmpFileCreatesUniqueFiles() {
        val content = "same content"

        val filePath1 = FileUtils.writeToTmpFile("test", content)
        val filePath2 = FileUtils.writeToTmpFile("test", content)

        assertFalse(filePath1 == filePath2)

        val file1 = File(filePath1)
        val file2 = File(filePath2)

        assertEquals(content, file1.readText())
        assertEquals(content, file2.readText())

        file1.delete()
        file2.delete()
    }

    fun testWriteToTmpFileInTempDirectory() {
        val content = "test"
        val filePath = FileUtils.writeToTmpFile("test", content)

        val tempDir = System.getProperty("java.io.tmpdir")
        assertTrue(filePath.startsWith(tempDir))

        File(filePath).delete()
    }

    fun testWriteToTmpFileWithLargeContent() {
        val content = "x".repeat(10000)

        val filePath = FileUtils.writeToTmpFile("test_large", content)

        val file = File(filePath)
        assertTrue(file.exists())
        assertEquals(content, file.readText())
        assertEquals(10000, file.length().toInt())

        file.delete()
    }

    fun testWriteToTmpFileWithPhpCode() {
        val content =
            """
            <?php
            class MyClass {
                public function myMethod(): string {
                    return 'Hello World';
                }
            }
            """.trimIndent()

        val filePath = FileUtils.writeToTmpFile("test_php", content)

        val file = File(filePath)
        assertTrue(file.exists())
        assertEquals(content, file.readText())

        file.delete()
    }
}
