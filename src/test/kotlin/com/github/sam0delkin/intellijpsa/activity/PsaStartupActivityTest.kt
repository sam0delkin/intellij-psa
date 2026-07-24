package com.github.sam0delkin.intellijpsa.activity

import com.github.sam0delkin.intellijpsa.services.server.ServerManager
import com.github.sam0delkin.intellijpsa.services.server.ServerState
import com.github.sam0delkin.intellijpsa.settings.ExecutionMode
import com.github.sam0delkin.intellijpsa.settings.Settings
import com.github.sam0delkin.intellijpsa.status.widget.PsaStatusBarWidgetFactory
import com.intellij.openapi.components.service
import com.intellij.openapi.wm.impl.status.widget.StatusBarWidgetsManager
import com.intellij.testFramework.PlatformTestUtil
import com.intellij.testFramework.fixtures.BasePlatformTestCase
import kotlinx.coroutines.runBlocking
import java.io.File
import java.util.Timer

/**
 * `execute()`'s plugin-disabled branch only notifies when a real, executable `.psa/psa.sh` file
 * exists on disk at `guessProjectDir().path`. In a light `BasePlatformTestCase` fixture,
 * `guessProjectDir()` resolves to the synthetic in-memory `temp:///src` root (see
 * `PsaContextualIntentionActionTest`/`ExecutionUtilsTest` for the same documented limitation), which
 * is never backed by a real file - so the `RunOnceUtil.runOnceForProject { ... }` notification body
 * is not reachable here and is a documented residual gap, consistent with this project's other
 * environment-dependent gaps.
 *
 * Every test that reaches `scheduleUpdate()` waits for its 500ms `java.util.Timer` task to actually
 * finish (via `waitForWidgetRegistration`) before returning, instead of relying on a fixed sleep -
 * `Settings`/`PsaManager`/`StatusBarWidgetsManager` are shared project-level services, and a
 * still-pending timer left running past the end of a test method fires later against whatever the
 * *next* test configured, corrupting its state (observed as unrelated tests failing when this timer
 * was allowed to leak).
 */
class PsaStartupActivityTest : BasePlatformTestCase() {
    private fun fixturePath(name: String): File {
        val resource = javaClass.classLoader.getResource("server/$name")!!
        val file = File(resource.toURI())
        file.setExecutable(true)

        return file
    }

    private fun waitUntil(
        timeoutMs: Long = 5000,
        condition: () -> Boolean,
    ) {
        val deadline = System.currentTimeMillis() + timeoutMs
        while (!condition() && System.currentTimeMillis() < deadline) {
            PlatformTestUtil.dispatchAllInvocationEventsInIdeEventQueue()
            Thread.sleep(20)
        }
        PlatformTestUtil.dispatchAllInvocationEventsInIdeEventQueue()
    }

    private fun waitForWidgetRegistration() {
        waitUntil(5000) {
            null !=
                project
                    .service<StatusBarWidgetsManager>()
                    .findWidgetFactory(PsaStatusBarWidgetFactory.WIDGET_ID)
        }
    }

    override fun tearDown() {
        try {
            project.service<ServerManager>().restart()
            waitUntil { project.service<ServerManager>().status() == ServerState.STOPPED }

            // Unlike most tests in this codebase, `execute()` reads `project.service<Settings>()`
            // directly rather than accepting an injectable `Settings` instance, so configuring it
            // necessarily mutates the shared, project-level singleton. The real `getInfo`/
            // `updateStaticCompletions` calls this test exercises also mutate further fields
            // (`supportsStaticCompletions`, `goToFilter`, etc.) as a side effect. Reset everything to
            // defaults so later tests (in this class or others sharing the light-fixture project)
            // don't inherit this test's configuration - see the `AnyCompletionContributorTest` failure
            // this caused before this reset was added.
            project.service<Settings>().loadState(Settings())
        } finally {
            super.tearDown()
        }
    }

    fun testExecuteReturnsWhenPluginDisabledAndScriptFileMissing() {
        project.service<Settings>().pluginEnabled = false

        val activity = PsaStartupActivity()
        try {
            runBlocking { activity.execute(project) }
            PlatformTestUtil.dispatchAllInvocationEventsInIdeEventQueue()
        } finally {
            activity.dispose()
        }
    }

    fun testExecuteReturnsWhenPluginEnabledAndScriptPathIsNull() {
        project.service<Settings>().apply {
            pluginEnabled = true
            scriptPath = null
        }

        val activity = PsaStartupActivity()
        try {
            runBlocking { activity.execute(project) }
            PlatformTestUtil.dispatchAllInvocationEventsInIdeEventQueue()
        } finally {
            activity.dispose()
        }
    }

    fun testExecuteSchedulesUpdateInScriptMode() {
        project.service<Settings>().apply {
            pluginEnabled = true
            executionMode = ExecutionMode.Script
            scriptPath = fixturePath("startup-activity-success.php").path
            supportedLanguages = "PHP"
        }

        val activity = PsaStartupActivity()
        try {
            runBlocking { activity.execute(project) }
            PlatformTestUtil.dispatchAllInvocationEventsInIdeEventQueue()
            waitForWidgetRegistration()

            assertNotNull(
                project.service<StatusBarWidgetsManager>().findWidgetFactory(PsaStatusBarWidgetFactory.WIDGET_ID),
            )
        } finally {
            activity.dispose()
        }
    }

    fun testExecuteCancelsPreviousTimerWhenRunTwice() {
        project.service<Settings>().apply {
            pluginEnabled = true
            executionMode = ExecutionMode.Script
            scriptPath = fixturePath("startup-activity-success.php").path
            supportedLanguages = "PHP"
        }

        val activity = PsaStartupActivity()

        // `StatusBarWidgetsManager` registration is a monotonic, project-shared signal (see class doc)
        // - it can already be true from an earlier test method by the time this one runs, which makes
        // waiting on it an unreliable way to confirm *this* test's `execute()` call reached its inner
        // `invokeLater` (and assigned `timer`). Pre-seeding `timer` via reflection with a spy instead
        // deterministically exercises - and directly asserts - the "cancel the previous timer" branch
        // on a single `execute()` call, with no dependency on `invokeLater` ordering.
        val spyTimer =
            object : Timer() {
                var cancelCalled = false

                override fun cancel() {
                    cancelCalled = true
                    super.cancel()
                }
            }
        val timerField = PsaStartupActivity::class.java.getDeclaredField("timer")
        timerField.isAccessible = true
        timerField.set(activity, spyTimer)

        try {
            runBlocking { activity.execute(project) }
            PlatformTestUtil.dispatchAllInvocationEventsInIdeEventQueue()
            waitForWidgetRegistration()

            assertTrue(spyTimer.cancelCalled)
        } finally {
            activity.dispose()
        }
    }

    fun testExecuteStartsServerModeWhenActive() {
        project.service<Settings>().apply {
            pluginEnabled = true
            executionMode = ExecutionMode.Server
            scriptPath = fixturePath("counting-server.php").path
            supportedLanguages = "PHP"
        }

        val activity = PsaStartupActivity()
        try {
            runBlocking { activity.execute(project) }
            PlatformTestUtil.dispatchAllInvocationEventsInIdeEventQueue()
            waitUntil { project.service<ServerManager>().status() == ServerState.RUNNING }

            assertEquals(ServerState.RUNNING, project.service<ServerManager>().status())

            // Drain the scheduleUpdate() timer this also triggers before returning - see class doc.
            waitForWidgetRegistration()
        } finally {
            activity.dispose()
        }
    }

    fun testDisposeIsIdempotentWithoutTimer() {
        val activity = PsaStartupActivity()

        activity.dispose()
        activity.dispose()
    }
}
