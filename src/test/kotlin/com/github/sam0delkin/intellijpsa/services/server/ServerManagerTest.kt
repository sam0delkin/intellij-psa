package com.github.sam0delkin.intellijpsa.services.server

import com.github.sam0delkin.intellijpsa.settings.ExecutionMode
import com.github.sam0delkin.intellijpsa.settings.Settings
import com.intellij.openapi.components.service
import com.intellij.testFramework.fixtures.BasePlatformTestCase
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.jsonPrimitive
import java.io.File

class ServerManagerTest : BasePlatformTestCase() {
    private lateinit var manager: ServerManager
    private lateinit var settings: Settings

    override fun setUp() {
        super.setUp()
        manager = project.service<ServerManager>()
        settings =
            Settings().apply {
                scriptPath = fixturePath()
                executionTimeout = 3000
            }
    }

    override fun tearDown() {
        try {
            manager.restart()
            waitUntil { manager.status() == ServerState.STOPPED }
            manager.maxRetries = 5
            manager.initialBackoffMs = 1000L
            manager.maxBackoffMs = 30000L
            manager.fileChangeDebounceMs = 1000L
            manager.killTimeoutMs = 500L
        } finally {
            super.tearDown()
        }
    }

    private fun fixturePath(): String {
        val resource = javaClass.classLoader.getResource("server/fixture-server.php")!!
        val file = File(resource.toURI())
        file.setExecutable(true)

        return file.path
    }

    private fun crashOnStartFixturePath(): String {
        val resource = javaClass.classLoader.getResource("server/crash-on-start.php")!!
        val file = File(resource.toURI())
        file.setExecutable(true)

        return file.path
    }

    private fun fixturePath(name: String): String {
        val resource = javaClass.classLoader.getResource("server/$name")!!
        val file = File(resource.toURI())
        file.setExecutable(true)

        return file.path
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

    private fun pidOf(response: ServerResponse): Long {
        val result = response.result as JsonObject

        return result["pid"]!!.jsonPrimitive.content.toLong()
    }

    fun testSingleRequestResponseRoundTrip() {
        val response = manager.sendRequest(settings, "Ping", null, false, null, null)

        assertNull(response.error)
        assertNotNull(response.result)
        assertEquals("Ping", (response.result as JsonObject)["type"]!!.jsonPrimitive.content)
    }

    fun testProcessIsLaunchedWithStartServerPsaType() {
        val response = manager.sendRequest(settings, "Ping", null, false, null, null)

        assertNull(response.error)
        val startupPsaType = (response.result as JsonObject)["startup_psa_type"]!!.jsonPrimitive.content
        assertEquals("StartServer", startupPsaType)
    }

    fun testMultipleSequentialRequestsReuseTheSameProcess() {
        val first = manager.sendRequest(settings, "Ping", null, false, null, null)
        val second = manager.sendRequest(settings, "Ping", null, false, null, null)
        val third = manager.sendRequest(settings, "Ping", null, false, null, null)

        assertNull(first.error)
        assertNull(second.error)
        assertNull(third.error)
        assertEquals(pidOf(first), pidOf(second))
        assertEquals(pidOf(second), pidOf(third))
    }

    fun testCrashThenRestartRecoversWithAFreshProcess() {
        val before = manager.sendRequest(settings, "Ping", null, false, null, null)
        assertNull(before.error)

        val crashResponse = manager.sendRequest(settings, "Crash", null, false, null, null)
        assertNotNull(crashResponse.error)

        val after = manager.sendRequest(settings, "Ping", null, false, null, null)
        assertNull(after.error)
        assertTrue(pidOf(before) != pidOf(after))
    }

    fun testSlowRequestTimesOutInsteadOfHanging() {
        val shortTimeoutSettings =
            Settings().apply {
                scriptPath = settings.scriptPath
                executionTimeout = 200
            }

        val response =
            manager.sendRequest(
                shortTimeoutSettings,
                "Sleep",
                null,
                false,
                null,
                Json.parseToJsonElement("""{"ms": 5000}"""),
            )

        assertNotNull(response.error)
        assertNull(response.result)
        assertTrue(response.error!!.contains("timed out"))
    }

    fun testRapidRestartAndRequestDoesNotCorruptState() {
        repeat(5) {
            manager.restart()
            waitUntil { manager.status() == ServerState.STOPPED }
            val response = manager.sendRequest(settings, "Ping", null, false, null, null)

            assertNull(response.error)
            assertNotNull(response.result)
        }
    }

    fun testStartTransitionsToRunning() {
        assertEquals(ServerState.STOPPED, manager.status())

        manager.start(settings)

        waitUntil { manager.status() == ServerState.RUNNING }
        assertEquals(ServerState.RUNNING, manager.status())
    }

    fun testAutoRestartAfterCrashReachesRunningAgainWithoutAnyExplicitRequest() {
        manager.initialBackoffMs = 20
        manager.maxBackoffMs = 100
        manager.start(settings)
        waitUntil { manager.status() == ServerState.RUNNING }
        assertEquals(ServerState.RUNNING, manager.status())

        val before = manager.sendRequest(settings, "Ping", null, false, null, null)
        assertNull(before.error)

        manager.sendRequest(settings, "Crash", null, false, null, null)
        waitUntil { manager.status() == ServerState.RUNNING }
        assertEquals(ServerState.RUNNING, manager.status())

        val after = manager.sendRequest(settings, "Ping", null, false, null, null)
        assertNull(after.error)
        assertTrue(pidOf(before) != pidOf(after))
    }

    fun testExceedsMaxRetriesEntersFailedStateThenRecoversViaExplicitStart() {
        manager.maxRetries = 2
        manager.initialBackoffMs = 20
        manager.maxBackoffMs = 100

        val crashingSettings =
            Settings().apply {
                scriptPath = crashOnStartFixturePath()
                executionTimeout = 1000
            }

        manager.start(crashingSettings)
        waitUntil { manager.status() == ServerState.FAILED }
        assertEquals(ServerState.FAILED, manager.status())

        manager.start(settings)
        waitUntil { manager.status() == ServerState.RUNNING }
        assertEquals(ServerState.RUNNING, manager.status())
    }

    fun testRestartWithServerModeRespawnsImmediately() {
        manager.start(settings)
        waitUntil { manager.status() == ServerState.RUNNING }
        assertEquals(ServerState.RUNNING, manager.status())

        settings.executionMode = ExecutionMode.Server
        manager.restart(settings)

        waitUntil { manager.status() == ServerState.RUNNING }
        assertEquals(ServerState.RUNNING, manager.status())
    }

    fun testRestartWithScriptModeDoesNotRespawn() {
        manager.start(settings)
        waitUntil { manager.status() == ServerState.RUNNING }
        assertEquals(ServerState.RUNNING, manager.status())

        settings.executionMode = ExecutionMode.Script
        manager.restart(settings)

        waitUntil { manager.status() == ServerState.STOPPED }
        assertEquals(ServerState.STOPPED, manager.status())
    }

    fun testRestartStopsServerWhenDebugIsEnabled() {
        settings.executionMode = ExecutionMode.Server
        manager.start(settings)
        waitUntil { manager.status() == ServerState.RUNNING }
        assertEquals(ServerState.RUNNING, manager.status())

        settings.debug = true
        manager.restart(settings)

        waitUntil { manager.status() == ServerState.STOPPED }
        assertEquals(ServerState.STOPPED, manager.status())
    }

    fun testRestartRespawnsServerWhenDebugIsDisabledAgain() {
        settings.executionMode = ExecutionMode.Server
        settings.debug = true
        manager.restart(settings)
        waitUntil { manager.status() == ServerState.STOPPED }

        settings.debug = false
        manager.restart(settings)

        waitUntil { manager.status() == ServerState.RUNNING }
        assertEquals(ServerState.RUNNING, manager.status())
    }

    fun testRestartKillsTheOldProcessBeforeReturning() {
        val before = manager.sendRequest(settings, "Ping", null, false, null, null)
        assertNull(before.error)
        val oldPid = pidOf(before)

        manager.restart(settings)

        waitUntil { ProcessHandle.of(oldPid).map { it.isAlive }.orElse(false) != true }
        val stillAlive = ProcessHandle.of(oldPid).map { it.isAlive }.orElse(false) == true
        assertFalse(stillAlive)
    }

    fun testScheduleRestartOnFileChangeDebouncesRapidCalls() {
        manager.fileChangeDebounceMs = 100
        settings.executionMode = ExecutionMode.Server
        val before = manager.sendRequest(settings, "Ping", null, false, null, null)
        assertNull(before.error)
        val oldPid = pidOf(before)

        repeat(5) {
            manager.scheduleRestartOnFileChange(settings)
            Thread.sleep(20)
        }

        Thread.sleep(manager.fileChangeDebounceMs + 1000)
        waitUntil { manager.status() == ServerState.RUNNING }
        val after = manager.sendRequest(settings, "Ping", null, false, null, null)
        assertNull(after.error)
        assertTrue(pidOf(before) != pidOf(after))
        assertTrue(ProcessHandle.of(oldPid).map { it.isAlive }.orElse(false) != true)
    }

    fun testScheduleRestartOnFileChangeIsNoopOutsideServerMode() {
        manager.start(settings)
        waitUntil { manager.status() == ServerState.RUNNING }
        val running = manager.status()
        settings.executionMode = ExecutionMode.Script

        manager.scheduleRestartOnFileChange(settings)
        Thread.sleep(200)

        assertEquals(running, manager.status())
    }

    fun testConsumeRestartWarningFalseWhenRunning() {
        manager.start(settings)
        waitUntil { manager.status() == ServerState.RUNNING }

        assertFalse(manager.consumeRestartWarning())
    }

    fun testConsumeRestartWarningFiresOncePerRetryCycle() {
        manager.initialBackoffMs = 1000
        manager.maxBackoffMs = 2000
        manager.start(settings)

        manager.sendRequest(settings, "Crash", null, false, null, null)
        waitUntil { manager.status() == ServerState.RETRYING }
        assertEquals(ServerState.RETRYING, manager.status())

        assertTrue(manager.consumeRestartWarning())
        assertFalse(manager.consumeRestartWarning())

        waitUntil(4000) { manager.status() == ServerState.RUNNING }
        assertEquals(ServerState.RUNNING, manager.status())
    }

    fun testSendRequestFailsGracefullyWhenStdinWriteFails() {
        val closedStdinSettings =
            Settings().apply {
                scriptPath = fixturePath("closed-stdin.php")
                executionTimeout = 3000
            }

        // First request (the health-check "Info" the manager sends internally) succeeds and the
        // fixture then closes its own stdin read end while staying alive.
        waitUntil { manager.status() == ServerState.STOPPED }
        manager.start(closedStdinSettings)
        waitUntil { manager.status() == ServerState.RUNNING }

        val response = manager.sendRequest(closedStdinSettings, "Ping", null, false, null, null)

        assertNotNull(response.error)
        assertTrue(response.error!!.contains("Failed to write to server stdin"))
    }

    fun testConfirmHealthyFailureKillsProcessAndSchedulesRetryThenFails() {
        manager.maxRetries = 1
        manager.initialBackoffMs = 20
        manager.maxBackoffMs = 100
        val neverHealthySettings =
            Settings().apply {
                scriptPath = fixturePath("server-info-error.php")
                executionTimeout = 1000
            }

        manager.start(neverHealthySettings)

        waitUntil(5000) { manager.status() == ServerState.FAILED }
        assertEquals(ServerState.FAILED, manager.status())
    }

    fun testScheduledRetryThrowingIsCaughtAndReschedules() {
        val tempScript = File.createTempFile("psa-retry-fixture", ".php")
        File(crashOnStartFixturePath()).copyTo(tempScript, overwrite = true)
        tempScript.setExecutable(true)

        manager.maxRetries = 2
        manager.initialBackoffMs = 300
        manager.maxBackoffMs = 400
        val vanishingScriptSettings =
            Settings().apply {
                scriptPath = tempScript.path
                executionTimeout = 1000
            }

        manager.start(vanishingScriptSettings)
        waitUntil { manager.status() == ServerState.RETRYING }

        // Delete the script before the scheduled retry fires, so ensureStarted() throws
        // when the alarm callback re-invokes it - exercising the retry-throws-and-reschedules
        // catch inside scheduleAutoRestart().
        tempScript.delete()

        waitUntil(5000) { manager.status() == ServerState.FAILED }
        assertEquals(ServerState.FAILED, manager.status())
    }

    fun testHandleLineIgnoresMalformedNoiseOnStdout() {
        val noisySettings =
            Settings().apply {
                scriptPath = fixturePath("noisy-stdout.php")
                executionTimeout = 3000
            }

        val response = manager.sendRequest(noisySettings, "Ping", null, false, null, null)

        assertNull(response.error)
    }

    fun testRestartToNonExistentScriptEntersFailedState() {
        manager.start(settings)
        waitUntil { manager.status() == ServerState.RUNNING }

        val badSettings =
            Settings().apply {
                scriptPath = "/nonexistent/path/to/psa-script-${System.nanoTime()}.php"
                executionMode = ExecutionMode.Server
                executionTimeout = 1000
            }

        manager.restart(badSettings)

        waitUntil(5000) { manager.status() == ServerState.FAILED }
        assertEquals(ServerState.FAILED, manager.status())
    }

    fun testDisposeStopsTheServer() {
        manager.start(settings)
        waitUntil { manager.status() == ServerState.RUNNING }

        manager.dispose()

        waitUntil { manager.status() == ServerState.STOPPED }
        assertEquals(ServerState.STOPPED, manager.status())
    }
}
