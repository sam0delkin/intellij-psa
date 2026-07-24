package com.github.sam0delkin.intellijpsa.util

import com.github.sam0delkin.intellijpsa.settings.Settings
import com.intellij.execution.configurations.GeneralCommandLine
import com.intellij.openapi.components.service
import com.intellij.testFramework.fixtures.BasePlatformTestCase

class ExecutionUtilsTest : BasePlatformTestCase() {
    fun testInstantiation() {
        assertNotNull(ExecutionUtils())
    }

    fun testSetWorkDirectoryIfExistsDoesNotThrow() {
        // NOTE: `guessProjectDir()` does not reliably resolve to a real on-disk directory in this
        // light `BasePlatformTestCase` fixture (it returns either null or a path backed only by
        // the in-memory test VFS), so the `commandLine.setWorkDirectory(...)` branch inside
        // `setWorkDirectoryIfExists` cannot be reliably exercised here. This only asserts the call
        // is safe regardless of the outcome.
        val commandLine = GeneralCommandLine("echo")

        ExecutionUtils.setWorkDirectoryIfExists(commandLine, project)

        assertNotNull(commandLine)
    }

    fun testGetCommandLineNonWindows() {
        val settings = project.service<Settings>()
        settings.scriptPath = "psa.sh"

        val commandLine = ExecutionUtils.getCommandLine(settings, project)

        assertNotNull(commandLine)
    }
}
