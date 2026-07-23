package com.github.sam0delkin.intellijpsa.services

import com.github.sam0delkin.intellijpsa.language.php.services.PhpPsaManager
import com.github.sam0delkin.intellijpsa.services.server.ServerManager
import com.github.sam0delkin.intellijpsa.services.server.ServerState
import com.github.sam0delkin.intellijpsa.settings.ExecutionMode
import com.github.sam0delkin.intellijpsa.settings.Settings
import com.intellij.openapi.components.service
import com.intellij.testFramework.fixtures.BasePlatformTestCase
import java.io.File

/**
 * Regression coverage for a pre-existing bug where `getStaticCompletions()`/`getTypeProviders()`
 * invoked the underlying script twice per logical call (once inside a cancellation-aware wrapper,
 * then again unconditionally, discarding the first result) - in both Script and Server
 * execution modes, the script/process should be invoked exactly once per call.
 */
class PsaManagerExecutionCountTest : BasePlatformTestCase() {
    override fun tearDown() {
        try {
            // ServerManager is a light service that persists across test *methods* (and across
            // test classes sharing the same light-fixture project) - must wait for the terminal
            // state here, or the next test can start while this restart is still in flight
            // (restart() redirects to a pooled thread when called from the EDT, which test
            // methods run on by default).
            project.service<ServerManager>().restart()
            waitUntil { project.service<ServerManager>().status() == ServerState.STOPPED }
        } finally {
            super.tearDown()
        }
    }

    private fun waitUntil(
        timeoutMs: Long = 5000,
        condition: () -> Boolean,
    ) {
        val deadline = System.currentTimeMillis() + timeoutMs
        while (!condition() && System.currentTimeMillis() < deadline) {
            Thread.sleep(20)
        }
    }

    private fun fixturePath(name: String): File {
        val resource = javaClass.classLoader.getResource("server/$name")!!
        val file = File(resource.toURI())
        file.setExecutable(true)

        return file
    }

    private fun counterFileFor(scriptFile: File): File {
        val counter = File(scriptFile.parentFile, scriptFile.name.removeSuffix(".php") + ".count")
        counter.delete()

        return counter
    }

    fun testGetStaticCompletionsInvokesScriptExactlyOnceInScriptMode() {
        val script = fixturePath("counting-script.php")
        val counter = counterFileFor(script)
        val settings =
            Settings().apply {
                pluginEnabled = true
                executionMode = ExecutionMode.Script
                scriptPath = script.path
            }

        project.service<PsaManager>().getStaticCompletions(settings, project)

        assertEquals(1, counter.readText().trim().toInt())
    }

    fun testGetStaticCompletionsInvokesServerExactlyOnceInServerMode() {
        val script = fixturePath("counting-server.php")
        val counter = counterFileFor(script)
        val settings =
            Settings().apply {
                pluginEnabled = true
                executionMode = ExecutionMode.Server
                scriptPath = script.path
            }

        project.service<PsaManager>().getStaticCompletions(settings, project)

        assertEquals(1, counter.readText().trim().toInt())
    }

    fun testGetTypeProvidersInvokesScriptExactlyOnceInScriptMode() {
        val script = fixturePath("counting-script.php")
        val counter = counterFileFor(script)
        val settings =
            Settings().apply {
                pluginEnabled = true
                executionMode = ExecutionMode.Script
                scriptPath = script.path
            }

        project.service<PhpPsaManager>().getTypeProviders(settings, project)

        assertEquals(1, counter.readText().trim().toInt())
    }

    fun testGetTypeProvidersInvokesServerExactlyOnceInServerMode() {
        val script = fixturePath("counting-server.php")
        val counter = counterFileFor(script)
        val settings =
            Settings().apply {
                pluginEnabled = true
                executionMode = ExecutionMode.Server
                scriptPath = script.path
            }

        project.service<PhpPsaManager>().getTypeProviders(settings, project)

        assertEquals(1, counter.readText().trim().toInt())
    }
}
