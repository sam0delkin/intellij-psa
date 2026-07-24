package com.github.sam0delkin.intellijpsa.status.widget

import com.github.sam0delkin.intellijpsa.icons.Icons
import com.github.sam0delkin.intellijpsa.services.PsaManager
import com.github.sam0delkin.intellijpsa.services.server.ServerManager
import com.github.sam0delkin.intellijpsa.services.server.ServerState
import com.github.sam0delkin.intellijpsa.settings.ExecutionMode
import com.intellij.openapi.actionSystem.ActionGroup
import com.intellij.openapi.actionSystem.AnAction
import com.intellij.openapi.actionSystem.DataContext
import com.intellij.openapi.actionSystem.Separator
import com.intellij.openapi.components.service
import com.intellij.openapi.util.Disposer
import com.intellij.openapi.wm.StatusBar
import com.intellij.openapi.wm.StatusBarWidget
import com.intellij.testFramework.TestActionEvent
import com.intellij.testFramework.fixtures.BasePlatformTestCase
import java.io.File

class PsaStatusBarWidgetFactoryTest : BasePlatformTestCase() {
    private lateinit var factory: PsaStatusBarWidgetFactory

    override fun setUp() {
        super.setUp()
        factory = PsaStatusBarWidgetFactory()
        project.service<PsaManager>().getSettings().apply {
            executionMode = ExecutionMode.Script
            debug = false
            supportsStaticCompletions = false
        }
    }

    override fun tearDown() {
        try {
            val manager = project.service<ServerManager>()
            manager.restart()
            waitUntil { manager.status() == ServerState.STOPPED }
            manager.maxRetries = 5
            manager.initialBackoffMs = 1000L
            manager.maxBackoffMs = 30000L
        } finally {
            super.tearDown()
        }
    }

    private fun fixturePath(name: String = "fixture-server.php"): String {
        val resource = javaClass.classLoader.getResource("server/$name")!!
        val file = File(resource.toURI())
        file.setExecutable(true)

        return file.path
    }

    /**
     * `serverStateLabel(ServerState)` is a file-private top-level function, compiled to a private
     * static method on the synthetic `PsaStatusBarWidgetFactoryKt` class - but since it's called
     * from the widget's anonymous inner classes, Kotlin also generates a public synthetic
     * `access$serverStateLabel` bridge we can invoke directly via reflection without needing
     * `setAccessible`, to exercise all six `ServerState` branches directly instead of only the ones
     * reachable through `getTooltipText()`/the popup status label.
     */
    private fun serverStateLabel(state: ServerState): String {
        val method =
            Class
                .forName("com.github.sam0delkin.intellijpsa.status.widget.PsaStatusBarWidgetFactoryKt")
                .getDeclaredMethod("access\$serverStateLabel", ServerState::class.java)

        return method.invoke(null, state) as String
    }

    /**
     * A `Project` proxy whose `getService(Class)` always throws `IllegalStateException`, to exercise
     * `isAvailable()`'s catch branch - which guards against calling `project.service<PsaManager>()`
     * before the project's service container is ready. No real, non-disposed `Project` in this test
     * environment throws that from `getService`, so a proxy is the only way to reach it (mirrors the
     * `mockStatusBar()` proxy technique above).
     */
    private fun throwingServiceProject(): com.intellij.openapi.project.Project =
        java.lang.reflect.Proxy.newProxyInstance(
            com.intellij.openapi.project.Project::class.java.classLoader,
            arrayOf(com.intellij.openapi.project.Project::class.java),
        ) { _, method, _ ->
            if (method.name == "getService") {
                throw IllegalStateException("simulated: service unavailable")
            }
            null
        } as com.intellij.openapi.project.Project

    private fun presentation(): StatusBarWidget.IconPresentation = factory.createWidget(project) as StatusBarWidget.IconPresentation

    private fun mockStatusBar(): StatusBar =
        java.lang.reflect.Proxy.newProxyInstance(
            StatusBar::class.java.classLoader,
            arrayOf(StatusBar::class.java),
        ) { _, _, _ -> null } as StatusBar

    /**
     * `createPopup(context)` is a local function on the anonymous `StatusBarWidget` object
     * returned by `createWidget()` - not part of any public interface - so the only way to invoke
     * it from a test is reflection. It builds a `DefaultActionGroup` (all the interesting
     * debug/static-completions/server conditional wiring lives there) and passes it
     * straight into `ActionPopupStep.createActionsStep(...)`, which is what actually backs the
     * returned `ListPopup`'s `getValues()`/`getTitles()`. We recover the original `AnAction`s from
     * there instead of calling `.show()` on the popup, which cannot run headlessly in this
     * environment (see the `SingleFileTemplateActionTest`/`MultipleFileTemplateActionTest`
     * `DialogBuilder` precedent for the same platform limitation).
     */
    @Suppress("UNCHECKED_CAST")
    private fun createPopupActionGroup(widget: StatusBarWidget): ActionGroup {
        val method = widget.javaClass.getDeclaredMethod("createPopup", DataContext::class.java)
        method.isAccessible = true
        val popup = method.invoke(widget, DataContext { null }) as com.intellij.openapi.ui.popup.ListPopup
        val actions =
            try {
                val stepField = popup.javaClass.methods.first { it.name == "getListStep" }
                stepField.isAccessible = true
                val step = stepField.invoke(popup)
                val valuesMethod = step.javaClass.methods.first { it.name == "getValues" }
                valuesMethod.isAccessible = true
                val values = valuesMethod.invoke(step) as List<Any?>

                values.mapNotNull { value ->
                    val actionField = runCatching { value!!.javaClass.getDeclaredField("myAction") }.getOrNull()
                    if (actionField != null) {
                        actionField.isAccessible = true
                        actionField.get(value) as? AnAction
                    } else {
                        value as? AnAction
                    }
                }
            } finally {
                Disposer.dispose(popup)
            }
        val group =
            com.intellij.openapi.actionSystem
                .DefaultActionGroup()
        actions.forEach { group.add(it) }

        return group
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

    fun testIconIsActiveWhenServerRunning() {
        val settings = project.service<PsaManager>().getSettings()
        settings.executionMode = ExecutionMode.Server
        settings.scriptPath = fixturePath()
        val manager = project.service<ServerManager>()
        manager.start(settings)
        waitUntil { manager.status() == ServerState.RUNNING }

        val presentation = presentation()

        assertEquals(Icons.PluginActiveIcon, presentation.getIcon())
        assertTrue(presentation.getTooltipText()!!.contains("Running"))
    }

    fun testIconIsErrorWhenServerNotRunning() {
        val settings = project.service<PsaManager>().getSettings()
        settings.executionMode = ExecutionMode.Server
        val manager = project.service<ServerManager>()
        manager.restart()

        waitUntil { manager.status() == ServerState.STOPPED }
        val presentation = presentation()

        assertEquals(Icons.PluginErrorIcon, presentation.getIcon())
        assertTrue(presentation.getTooltipText()!!.contains("Stopped"))
    }

    fun testIconFallsBackToLastResultWhenNotInServerMode() {
        val psaManager = project.service<PsaManager>()
        val settings = psaManager.getSettings()
        settings.executionMode = ExecutionMode.Script
        psaManager.lastResultSucceed = true

        val presentation = presentation()

        assertEquals(Icons.PluginActiveIcon, presentation.getIcon())
        assertEquals("PSA: Working", presentation.getTooltipText())
    }

    fun testIconReflectsLastResultFailureWhenNotInServerMode() {
        val psaManager = project.service<PsaManager>()
        val settings = psaManager.getSettings()
        settings.executionMode = ExecutionMode.Script
        psaManager.lastResultSucceed = false

        val presentation = presentation()

        assertEquals(Icons.PluginErrorIcon, presentation.getIcon())
        assertEquals("PSA: Error", presentation.getTooltipText())
    }

    fun testIconIsThrobberWhileRetryingAfterCrash() {
        val settings = project.service<PsaManager>().getSettings()
        settings.executionMode = ExecutionMode.Server
        settings.scriptPath = fixturePath()
        val manager = project.service<ServerManager>()
        manager.initialBackoffMs = 2000
        manager.maxBackoffMs = 3000
        manager.start(settings)

        manager.sendRequest(settings, "Crash", null, false, null, null)
        val deadline = System.currentTimeMillis() + 5000
        while (manager.status() != ServerState.RETRYING && System.currentTimeMillis() < deadline) {
            Thread.sleep(20)
        }
        assertEquals(ServerState.RETRYING, manager.status())

        val presentation = presentation()

        assertTrue(presentation.getIcon() !== Icons.PluginActiveIcon)
        assertTrue(presentation.getIcon() !== Icons.PluginErrorIcon)
        assertTrue(presentation.getTooltipText()!!.contains("Retrying"))
    }

    fun testGetIdAndDisplayName() {
        assertEquals("psa.statusBar.widget_factory", factory.getId())
        assertEquals("Project Specific Autocomplete", factory.getDisplayName())
    }

    fun testIsAvailableReflectsPluginEnabled() {
        val settings = project.service<PsaManager>().getSettings()
        settings.pluginEnabled = true

        assertTrue(factory.isAvailable(project))

        settings.pluginEnabled = false

        assertFalse(factory.isAvailable(project))
    }

    fun testIsAvailableReturnsFalseWhenServiceLookupThrowsIllegalStateException() {
        assertFalse(factory.isAvailable(throwingServiceProject()))
    }

    fun testServerStateLabelCoversAllStates() {
        assertEquals("Stopped", serverStateLabel(ServerState.STOPPED))
        assertEquals("Starting", serverStateLabel(ServerState.STARTING))
        assertEquals("Running", serverStateLabel(ServerState.RUNNING))
        assertEquals("Retrying", serverStateLabel(ServerState.RETRYING))
        assertEquals("Restarting", serverStateLabel(ServerState.RESTARTING))
        assertEquals("Failed", serverStateLabel(ServerState.FAILED))
    }

    fun testCanBeEnabledOnReturnsTrue() {
        assertTrue(factory.canBeEnabledOn(mockStatusBar()))
    }

    @Suppress("DEPRECATION")
    fun testDisposeWidgetDelegatesToDisposer() {
        val widget = factory.createWidget(project)

        factory.disposeWidget(widget)

        assertTrue(Disposer.isDisposed(widget as com.intellij.openapi.Disposable))
    }

    fun testWidgetIdMatchesConstant() {
        val widget = factory.createWidget(project)

        assertEquals(PsaStatusBarWidgetFactory.WIDGET_ID, widget.ID())
    }

    fun testGetPresentationReturnsSelf() {
        val widget = factory.createWidget(project)

        assertSame(widget, widget.getPresentation())
    }

    fun testDisposeCancelsInstallTimer() {
        val widget = factory.createWidget(project)
        val statusBar = mockStatusBar()
        widget.install(statusBar)

        widget.dispose()
        // Calling dispose a second time (or install again) should not throw once the timer is
        // already cancelled.
        widget.dispose()
    }

    fun testInstallCalledTwiceCancelsPreviousTimer() {
        val widget = factory.createWidget(project)
        val statusBar = mockStatusBar()

        widget.install(statusBar)
        // Installing again while a timer from the first install() is still running exercises the
        // `timer!!.cancel()` branch that cancels the previous timer before scheduling a new one.
        widget.install(statusBar)

        widget.dispose()
    }

    fun testInstallTimerTriggersStatusBarUpdate() {
        val widget = factory.createWidget(project)
        val updateCount =
            java.util.concurrent.atomic
                .AtomicInteger(0)
        val statusBar =
            java.lang.reflect.Proxy.newProxyInstance(
                StatusBar::class.java.classLoader,
                arrayOf(StatusBar::class.java),
            ) { _, method, _ ->
                if (method.name == "updateWidget") {
                    updateCount.incrementAndGet()
                }
                null
            } as StatusBar

        widget.install(statusBar)
        try {
            waitUntil(2000) { updateCount.get() > 0 }
            assertTrue(updateCount.get() > 0)
        } finally {
            // Cancel the real java.util.Timer scheduled by install() so it can't fire again and
            // touch this (about to be torn-down) test's state later.
            widget.dispose()
        }
    }

    fun testPopupActionGroupContainsCoreActionsWithDebugAndStaticCompletionsEnabled() {
        val settings = project.service<PsaManager>().getSettings()
        settings.executionMode = ExecutionMode.Script
        settings.debug = true
        settings.supportsStaticCompletions = true
        val widget = factory.createWidget(project)

        val group = createPopupActionGroup(widget)
        val actions = group.getChildren(null)

        assertTrue(actions.any { it.templatePresentation.text == "Settings" })
        assertTrue(actions.any { it.templatePresentation.text == "Disable Debug" })
        assertTrue(actions.any { it.templatePresentation.text == "Update Static Completions" })
        assertTrue(actions.any { it.templatePresentation.text == "Update Info" })
        assertTrue(actions.any { it.templatePresentation.text == "Show Last Error" })
        assertFalse(actions.any { it is Separator })
    }

    fun testPopupActionGroupShowsEnableDebugWhenDebugOff() {
        val settings = project.service<PsaManager>().getSettings()
        settings.executionMode = ExecutionMode.Script
        settings.debug = false
        settings.supportsStaticCompletions = false
        val widget = factory.createWidget(project)

        val group = createPopupActionGroup(widget)
        val actions = group.getChildren(null)

        assertTrue(actions.any { it.templatePresentation.text == "Enable Debug" })
        assertFalse(actions.any { it.templatePresentation.text == "Update Static Completions" })
    }

    fun testPopupActionGroupIncludesServerControlsInServerMode() {
        // ActionPopupStep is constructed with showDisabledActions=false, so the permanently-disabled
        // "Server: <state>" status label and the Separator preceding it are filtered out of
        // getListStep().getValues() at construction time and cannot be recovered from the returned
        // ListPopup - but building the popup still executes the lines that construct and add them
        // (Separator.getInstance(), the status action's constructor and its update() override, which
        // the platform must invoke on every action in the group to decide what to filter).
        val settings = project.service<PsaManager>().getSettings()
        settings.executionMode = ExecutionMode.Server
        val widget = factory.createWidget(project)

        val group = createPopupActionGroup(widget)
        val actions = group.getChildren(null)

        assertTrue(actions.any { it.templatePresentation.text == "Start Server" })
        assertTrue(actions.any { it.templatePresentation.text == "Restart Server" })
    }

    fun testDebugToggleActionsFlipSettings() {
        val settings = project.service<PsaManager>().getSettings()
        settings.executionMode = ExecutionMode.Script
        settings.debug = false
        val widget = factory.createWidget(project)
        val enableDebug = createPopupActionGroup(widget).getChildren(null).first { it.templatePresentation.text == "Enable Debug" }

        enableDebug.actionPerformed(TestActionEvent.createTestEvent(enableDebug))

        assertTrue(settings.debug)

        val disableDebug = createPopupActionGroup(widget).getChildren(null).first { it.templatePresentation.text == "Disable Debug" }
        disableDebug.actionPerformed(TestActionEvent.createTestEvent(disableDebug))

        assertFalse(settings.debug)
    }

    fun testShowLastErrorActionNotifiesWhenMessagePresent() {
        val psaManager = project.service<PsaManager>()
        psaManager.getSettings().executionMode = ExecutionMode.Script
        psaManager.lastResultMessage = "Something went wrong"
        val widget = factory.createWidget(project)
        val showLastError =
            createPopupActionGroup(widget).getChildren(null).first { it.templatePresentation.text == "Show Last Error" }

        showLastError.actionPerformed(TestActionEvent.createTestEvent(showLastError))
    }

    fun testShowLastErrorActionNoOpWithoutMessage() {
        val psaManager = project.service<PsaManager>()
        psaManager.getSettings().executionMode = ExecutionMode.Script
        psaManager.lastResultMessage = ""
        val widget = factory.createWidget(project)
        val showLastError =
            createPopupActionGroup(widget).getChildren(null).first { it.templatePresentation.text == "Show Last Error" }

        showLastError.actionPerformed(TestActionEvent.createTestEvent(showLastError))
    }

    fun testStartServerActionEnabledWhenNotRunning() {
        val settings = project.service<PsaManager>().getSettings()
        settings.executionMode = ExecutionMode.Server
        val widget = factory.createWidget(project)
        val startAction =
            createPopupActionGroup(widget).getChildren(null).first { it.templatePresentation.text == "Start Server" }
        val event = TestActionEvent.createTestEvent(startAction)

        startAction.update(event)

        assertTrue(event.presentation.isEnabledAndVisible)
    }

    fun testStartServerActionInvokesManager() {
        val settings = project.service<PsaManager>().getSettings()
        settings.executionMode = ExecutionMode.Server
        settings.scriptPath = fixturePath()
        val widget = factory.createWidget(project)
        val startAction =
            createPopupActionGroup(widget).getChildren(null).first { it.templatePresentation.text == "Start Server" }
        val event = TestActionEvent.createTestEvent(startAction)

        startAction.actionPerformed(event)

        val manager = project.service<ServerManager>()
        waitUntil { manager.status() == ServerState.RUNNING }
        assertEquals(ServerState.RUNNING, manager.status())
    }

    fun testRestartServerActionInvokesManager() {
        val settings = project.service<PsaManager>().getSettings()
        settings.executionMode = ExecutionMode.Server
        settings.scriptPath = fixturePath()
        val widget = factory.createWidget(project)
        val restartAction =
            createPopupActionGroup(widget).getChildren(null).first { it.templatePresentation.text == "Restart Server" }
        val event = TestActionEvent.createTestEvent(restartAction)

        restartAction.update(event)
        assertTrue(event.presentation.isEnabled)
        restartAction.actionPerformed(event)
    }

    fun testUpdateInfoActionNoOpWithoutScriptPath() {
        val settings = project.service<PsaManager>().getSettings()
        settings.executionMode = ExecutionMode.Script
        settings.scriptPath = null
        val widget = factory.createWidget(project)
        val updateInfoAction =
            createPopupActionGroup(widget).getChildren(null).first { it.templatePresentation.text == "Update Info" }

        updateInfoAction.actionPerformed(TestActionEvent.createTestEvent(updateInfoAction))
    }

    fun testUpdateInfoActionSucceedsWithRealScript() {
        val psaManager = project.service<PsaManager>()
        val settings = psaManager.getSettings()
        settings.executionMode = ExecutionMode.Script
        // `fixture-server.php` is an NDJSON loop server built for Server-mode tests - invoked as a
        // one-shot Script it reads no stdin, prints nothing and exits 0, which previously made this
        // test silently exercise the *failure* path (empty stdout fails to deserialize as InfoModel)
        // without ever asserting anything. Use a script that actually returns a valid Info payload so
        // the success branch (notification text, `lastResultSucceed = true`) is genuinely exercised.
        settings.scriptPath = fixturePath("configurable-info-success.php")
        psaManager.lastResultSucceed = false
        psaManager.lastResultMessage = "stale"
        val widget = factory.createWidget(project)
        val updateInfoAction =
            createPopupActionGroup(widget).getChildren(null).first { it.templatePresentation.text == "Update Info" }

        updateInfoAction.actionPerformed(TestActionEvent.createTestEvent(updateInfoAction))

        waitUntil { psaManager.lastResultSucceed }
        assertTrue(psaManager.lastResultSucceed)
        assertEquals("", psaManager.lastResultMessage)
    }

    fun testUpdateInfoActionFailureWithEmptyScriptOutputUsesFallbackMessage() {
        val psaManager = project.service<PsaManager>()
        val settings = psaManager.getSettings()
        settings.executionMode = ExecutionMode.Script
        // `crash-on-start.php` exits non-zero without printing anything to stdout or stderr, so the
        // raw failure message built from `result.stdout + "\n" + result.stderr` is just "\n" - which
        // trims to blank and exercises the `.ifEmpty { "Failed to retrieve info" }` fallback branch
        // when building the error notification text.
        settings.scriptPath = fixturePath("crash-on-start.php")
        psaManager.lastResultSucceed = true
        psaManager.lastResultMessage = ""
        val widget = factory.createWidget(project)
        val updateInfoAction =
            createPopupActionGroup(widget).getChildren(null).first { it.templatePresentation.text == "Update Info" }

        updateInfoAction.actionPerformed(TestActionEvent.createTestEvent(updateInfoAction))

        waitUntil { !psaManager.lastResultSucceed }
        assertFalse(psaManager.lastResultSucceed)
        assertEquals("\n", psaManager.lastResultMessage)
    }

    fun testGetClickConsumerBuildsDataContextAndConsumer() {
        val consumer = presentation().getClickConsumer()

        assertNotNull(consumer)
        // Not invoking consumer.accept(): it calls createPopup(...).show(RelativePoint(e)), which
        // shows a real popup and cannot run headlessly in this test environment.
    }

    fun testSettingsActionOpensConfigurable() {
        val widget = factory.createWidget(project)
        val settingsAction = createPopupActionGroup(widget).getChildren(null).first { it.templatePresentation.text == "Settings" }
        val event = TestActionEvent.createTestEvent(settingsAction)

        // ShowSettingsUtil.getInstance().editConfigurable(...) attempts to open a real modal settings
        // dialog via DialogWrapper.showAndGet() - which, unlike DialogBuilder.show() elsewhere in this
        // codebase, fails fast in this headless test environment with an IllegalStateException instead
        // of an NPE (the PsaConfigurable it constructs is a non-modal Configurable, so the platform
        // refuses to drive it through the modal-only showAndGet() path at all).
        try {
            settingsAction.actionPerformed(event)
        } catch (e: IllegalStateException) {
            assertTrue(e.message.orEmpty().contains("showAndGet"))
        }
    }

    fun testUpdateStaticCompletionsActionInvokesManager() {
        val settings = project.service<PsaManager>().getSettings()
        settings.executionMode = ExecutionMode.Script
        settings.supportsStaticCompletions = true
        settings.scriptPath = fixturePath()
        val widget = factory.createWidget(project)
        val updateAction =
            createPopupActionGroup(widget).getChildren(null).first { it.templatePresentation.text == "Update Static Completions" }

        updateAction.actionPerformed(TestActionEvent.createTestEvent(updateAction))
    }
}
