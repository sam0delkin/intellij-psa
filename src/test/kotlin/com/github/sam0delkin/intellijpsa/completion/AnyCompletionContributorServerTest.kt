package com.github.sam0delkin.intellijpsa.completion

import com.github.sam0delkin.intellijpsa.services.PsaManager
import com.github.sam0delkin.intellijpsa.services.server.ServerManager
import com.github.sam0delkin.intellijpsa.services.server.ServerState
import com.github.sam0delkin.intellijpsa.settings.ExecutionMode
import com.intellij.codeInsight.completion.CompletionParameters
import com.intellij.codeInsight.completion.CompletionProcess
import com.intellij.codeInsight.completion.CompletionType
import com.intellij.openapi.components.service
import com.intellij.psi.PsiElement
import com.intellij.testFramework.PlatformTestUtil
import com.intellij.testFramework.fixtures.BasePlatformTestCase
import java.io.File

/**
 * Regression coverage for a UI freeze: Completion/GoTo used to call into the Server
 * (blocking on `future.get(executionTimeout)`) even while it wasn't RUNNING yet, instead of
 * skipping the call outright while it's starting/restarting/retrying/failed/stopped.
 */
class AnyCompletionContributorServerTest : BasePlatformTestCase() {
    override fun tearDown() {
        try {
            // ServerManager is a light service that persists across test *methods* (and across
            // test classes sharing the same light-fixture project) - must wait for the terminal
            // state here, or the next test can start while this restart is still in flight
            // (restart() redirects to a pooled thread when called from the EDT, which test
            // methods run on by default).
            val manager = project.service<ServerManager>()
            manager.restart()
            waitUntil { manager.status() == ServerState.STOPPED }
            // A few tests below tune the retry/backoff knobs to deterministically observe
            // RETRYING/FAILED - reset them so later test classes sharing this light service aren't
            // affected (mirrors ServerManagerTest's tearDown).
            manager.maxRetries = 5
            manager.initialBackoffMs = 1000L
            manager.maxBackoffMs = 30000L
            manager.killTimeoutMs = 500L
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

    // Switches into Server mode only after the PSI/file setup is done, so the plugin's
    // own file-change listener (which schedules a debounced server restart on every file event
    // while enabled) never fires mid-test and races with the assertions below.
    private fun configureServerModeSettings(script: File): PsaManager {
        val psaManager = project.service<PsaManager>()
        val settings = psaManager.getSettings()
        settings.pluginEnabled = true
        settings.executionMode = ExecutionMode.Server
        settings.supportedLanguages = "PHP"
        settings.scriptPath = script.path

        return psaManager
    }

    fun testCompletionSkipsTheScriptAndReturnsImmediatelyWhileServerIsNotRunning() {
        val script = fixturePath("counting-empty-completions.php")
        val counter = File(script.parentFile, "counting-empty-completions.count")
        counter.delete()

        myFixture.configureByText("test.php", "<?php \$foo = 1; \$fo")
        myFixture.editor.caretModel.moveToOffset(myFixture.editor.document.textLength)

        configureServerModeSettings(script)
        assertEquals(ServerState.STOPPED, project.service<ServerManager>().status())

        val start = System.currentTimeMillis()
        myFixture.completeBasic()
        val elapsed = System.currentTimeMillis() - start

        assertTrue(
            "completion should return near-instantly instead of blocking on the server, took ${elapsed}ms",
            elapsed < 2000,
        )
        assertFalse("the server should never have been invoked", counter.exists())
    }

    fun testGotoSkipsTheScriptAndReturnsNoTargetsWhileServerIsNotRunning() {
        val script = fixturePath("counting-empty-completions.php")
        val counter = File(script.parentFile, "counting-empty-completions.count")
        counter.delete()

        myFixture.configureByText("test.php", "<?php 'some_string';")
        val element = myFixture.findElementByText("'some_string'", PsiElement::class.java)

        configureServerModeSettings(script)
        assertEquals(ServerState.STOPPED, project.service<ServerManager>().status())

        val start = System.currentTimeMillis()
        val targets =
            AnyCompletionContributor.GotoDeclaration().getGotoDeclarationTargets(
                element,
                element.textOffset,
                myFixture.editor,
            )
        val elapsed = System.currentTimeMillis() - start

        assertTrue(
            "goto should return near-instantly instead of blocking on the server, took ${elapsed}ms",
            elapsed < 2000,
        )
        assertTrue(targets == null || targets.isEmpty())
        assertFalse("the server should never have been invoked", counter.exists())
    }

    @Suppress("UnstableApiUsage")
    private fun completionParametersAt(position: PsiElement): CompletionParameters =
        CompletionParameters(
            position,
            myFixture.file,
            CompletionType.BASIC,
            position.textOffset,
            1,
            myFixture.editor,
            object : CompletionProcess {
                override fun isAutopopupCompletion() = false
            },
        )

    fun testHandleEmptyLookupReturnsWarningTextWhileServerIsNotRunning() {
        val script = fixturePath("counting-empty-completions.php")

        myFixture.configureByText("test.php", "<?php \$fo<caret>o;")
        val element = myFixture.getElementAtCaret()

        configureServerModeSettings(script)
        assertEquals(ServerState.STOPPED, project.service<ServerManager>().status())

        val message =
            AnyCompletionContributor.Completion().handleEmptyLookup(
                completionParametersAt(element),
                myFixture.editor,
            )

        assertNotNull(message)
        assertTrue(message!!.contains("PSA Server"))
    }

    fun testHandleEmptyLookupReturnsNullOutsideServerMode() {
        myFixture.configureByText("test.php", "<?php \$fo<caret>o;")
        val element = myFixture.getElementAtCaret()

        val psaManager = project.service<PsaManager>()
        val settings = psaManager.getSettings()
        settings.pluginEnabled = true
        settings.executionMode = ExecutionMode.Script
        settings.supportedLanguages = "PHP"

        val message =
            AnyCompletionContributor.Completion().handleEmptyLookup(
                completionParametersAt(element),
                myFixture.editor,
            )

        assertNull(message)
    }

    // Regression/coverage test for serverUnavailableHintText()'s ServerState.STARTING branch.
    // Right after ensureStarted() launches the process, state is synchronously set to STARTING -
    // the async health check (confirmHealthy(), on a pooled thread) only flips it to RUNNING once
    // the round trip actually succeeds, which takes measurably longer than the very next statement
    // on this thread.
    fun testHandleEmptyLookupReturnsStartingHintTextWhileServerIsStarting() {
        val script = fixturePath("goto-server.php")
        myFixture.configureByText("test.php", "<?php \$fo<caret>o;")
        val element = myFixture.getElementAtCaret()

        val psaManager = configureServerModeSettings(script)
        val manager = project.service<ServerManager>()
        manager.start(psaManager.getSettings())
        assertEquals(ServerState.STARTING, manager.status())

        val message =
            AnyCompletionContributor.Completion().handleEmptyLookup(
                completionParametersAt(element),
                myFixture.editor,
            )

        assertNotNull(message)
        assertTrue(message!!.contains("starting"))

        // Let the in-flight confirmHealthy() health check settle to a stable state before this
        // method returns - otherwise its asynchronous completion can race with (and outlive)
        // tearDown()'s own restart(), leaking a RUNNING state into a later test.
        waitUntil { manager.status() == ServerState.RUNNING }
    }

    // Regression/coverage test for serverUnavailableHintText()'s ServerState.RESTARTING branch.
    // restart() sets state = RESTARTING synchronously, before ever dispatching the actual
    // kill+respawn work (to a pooled thread, since test methods run on the EDT) - so the state is
    // deterministically observable immediately afterwards.
    fun testHandleEmptyLookupReturnsRestartingHintTextWhileServerIsRestarting() {
        val script = fixturePath("goto-server.php")
        myFixture.configureByText("test.php", "<?php \$fo<caret>o;")
        val element = myFixture.getElementAtCaret()

        val psaManager = configureServerModeSettings(script)
        val settings = psaManager.getSettings()
        val manager = project.service<ServerManager>()
        manager.start(settings)
        waitUntil { manager.status() == ServerState.RUNNING }
        assertEquals(ServerState.RUNNING, manager.status())

        manager.restart(settings)
        assertEquals(ServerState.RESTARTING, manager.status())

        val message =
            AnyCompletionContributor.Completion().handleEmptyLookup(
                completionParametersAt(element),
                myFixture.editor,
            )

        assertNotNull(message)
        assertTrue(message!!.contains("restarting"))

        // Since settings has Server mode active, restart() respawns a fresh process on a pooled
        // thread. Wait for that to fully settle before returning - otherwise it can race with (and
        // outlive) tearDown()'s own restart(), leaking a RUNNING state into a later test.
        waitUntil { manager.status() == ServerState.RUNNING }
    }

    // Regression/coverage test for serverUnavailableHintText()'s ServerState.RETRYING branch.
    fun testHandleEmptyLookupReturnsRetryingHintTextAfterCrash() {
        val script = fixturePath("fixture-server.php")
        myFixture.configureByText("test.php", "<?php \$fo<caret>o;")
        val element = myFixture.getElementAtCaret()

        val psaManager = configureServerModeSettings(script)
        val settings = psaManager.getSettings()
        val manager = project.service<ServerManager>()
        manager.initialBackoffMs = 5000L
        manager.maxBackoffMs = 10000L
        manager.start(settings)
        waitUntil { manager.status() == ServerState.RUNNING }

        manager.sendRequest(settings, "Crash", null, false, null, null)
        waitUntil { manager.status() == ServerState.RETRYING }
        assertEquals(ServerState.RETRYING, manager.status())

        val message =
            AnyCompletionContributor.Completion().handleEmptyLookup(
                completionParametersAt(element),
                myFixture.editor,
            )

        assertNotNull(message)
        assertTrue(message!!.contains("retrying"))
    }

    // Regression/coverage test for serverUnavailableHintText()'s ServerState.FAILED branch.
    fun testHandleEmptyLookupReturnsFailedHintTextAfterExceedingMaxRetries() {
        val script = fixturePath("crash-on-start.php")
        myFixture.configureByText("test.php", "<?php \$fo<caret>o;")
        val element = myFixture.getElementAtCaret()

        val psaManager = configureServerModeSettings(script)
        val settings = psaManager.getSettings()
        val manager = project.service<ServerManager>()
        manager.maxRetries = 1
        manager.initialBackoffMs = 20L
        manager.maxBackoffMs = 100L
        manager.start(settings)
        waitUntil { manager.status() == ServerState.FAILED }
        assertEquals(ServerState.FAILED, manager.status())

        val message =
            AnyCompletionContributor.Completion().handleEmptyLookup(
                completionParametersAt(element),
                myFixture.editor,
            )

        assertNotNull(message)
        assertTrue(message!!.contains("failed"))
    }

    // Regression/coverage test for serverUnavailable()'s invokeLater-dispatched
    // HintManager.showInformationHint(...) block: only fires when there's a real editor and this
    // is the first time the non-running state is observed (consumeRestartWarning()). Since
    // ApplicationManager.invokeLater() merely schedules the runnable, dispatchAllInvocationEvents
    // is needed to actually run it (and thus record coverage on it) within the test.
    fun testGotoDeclarationShowsWarningHintOnFirstObservationOfServerUnavailable() {
        val script = fixturePath("counting-empty-completions.php")
        myFixture.configureByText("test.php", "<?php 'some_string';")
        val element = myFixture.findElementByText("'some_string'", PsiElement::class.java)

        configureServerModeSettings(script)
        assertEquals(ServerState.STOPPED, project.service<ServerManager>().status())

        AnyCompletionContributor.GotoDeclaration().getGotoDeclarationTargets(
            element,
            element.textOffset,
            myFixture.editor,
        )
        PlatformTestUtil.dispatchAllInvocationEventsInIdeEventQueue()

        // No public API exposes whether the hint was actually shown; consumeRestartWarning()
        // having flipped to "already shown" proves the production code's internal call already
        // consumed it (the first-and-only true return), which is what gates the invokeLater block.
        assertFalse(project.service<ServerManager>().consumeRestartWarning())
    }
}
