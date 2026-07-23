package com.github.sam0delkin.intellijpsa.services.server

import com.intellij.testFramework.fixtures.BasePlatformTestCase

class ServerStateTest : BasePlatformTestCase() {
    fun testValues() {
        val values = ServerState.values()

        assertEquals(6, values.size)
        assertTrue(values.contains(ServerState.STOPPED))
        assertTrue(values.contains(ServerState.STARTING))
        assertTrue(values.contains(ServerState.RUNNING))
        assertTrue(values.contains(ServerState.RETRYING))
        assertTrue(values.contains(ServerState.RESTARTING))
        assertTrue(values.contains(ServerState.FAILED))
    }

    fun testValueOf() {
        assertEquals(ServerState.RUNNING, ServerState.valueOf("RUNNING"))
        assertEquals(ServerState.FAILED, ServerState.valueOf("FAILED"))
    }

    fun testName() {
        assertEquals("RUNNING", ServerState.RUNNING.name)
    }
}
