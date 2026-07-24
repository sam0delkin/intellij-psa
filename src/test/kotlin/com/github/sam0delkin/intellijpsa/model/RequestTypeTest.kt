package com.github.sam0delkin.intellijpsa.model

import com.intellij.testFramework.fixtures.BasePlatformTestCase

class RequestTypeTest : BasePlatformTestCase() {
    fun testValues() {
        val values = RequestType.values()

        assertEquals(9, values.size)
        assertTrue(values.contains(RequestType.Completion))
        assertTrue(values.contains(RequestType.BatchCompletion))
        assertTrue(values.contains(RequestType.GoTo))
        assertTrue(values.contains(RequestType.BatchGoTo))
        assertTrue(values.contains(RequestType.Info))
        assertTrue(values.contains(RequestType.GenerateFileFromTemplate))
        assertTrue(values.contains(RequestType.GetStaticCompletions))
        assertTrue(values.contains(RequestType.PerformEditorAction))
        assertTrue(values.contains(RequestType.StartServer))
    }

    fun testValueOf() {
        assertEquals(RequestType.StartServer, RequestType.valueOf("StartServer"))
        assertEquals(RequestType.Info, RequestType.valueOf("Info"))
    }

    fun testName() {
        assertEquals("StartServer", RequestType.StartServer.name)
    }
}
