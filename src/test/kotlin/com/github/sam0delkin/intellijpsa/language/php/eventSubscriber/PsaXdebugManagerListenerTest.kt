package com.github.sam0delkin.intellijpsa.language.php.eventSubscriber

import com.intellij.execution.configurations.RunProfile
import com.intellij.execution.ui.ConsoleView
import com.intellij.execution.ui.RunContentDescriptor
import com.intellij.execution.ui.RunnerLayoutUi
import com.intellij.openapi.Disposable
import com.intellij.openapi.project.Project
import com.intellij.openapi.ui.MessageType
import com.intellij.testFramework.fixtures.BasePlatformTestCase
import com.intellij.xdebugger.XDebugProcess
import com.intellij.xdebugger.XDebugSession
import com.intellij.xdebugger.XDebugSessionListener
import com.intellij.xdebugger.XDebuggerManager
import com.intellij.xdebugger.XSourcePosition
import com.intellij.xdebugger.breakpoints.XBreakpoint
import com.intellij.xdebugger.breakpoints.XLineBreakpoint
import com.intellij.xdebugger.frame.XExecutionStack
import com.intellij.xdebugger.frame.XStackFrame
import com.intellij.xdebugger.frame.XSuspendContext
import com.intellij.xdebugger.stepping.XSmartStepIntoHandler
import com.intellij.xdebugger.stepping.XSmartStepIntoVariant
import javax.swing.Icon
import javax.swing.event.HyperlinkListener

/**
 * `PsaXdebugManagerListener` bridges into two categories of PHP-plugin/live-debug-session
 * classes that cannot be constructed in a `BasePlatformTestCase` (no live XDebug connection) -
 * the same documented limitation as `PsaPhpValueTest.kt` (`XdebugValue`) and the (absent)
 * `PhpPsaStackFrameTest` (`XdebugStackFrame`):
 *
 * - `com.jetbrains.php.debug.xdebug.debugger.XdebugStackFrame`'s only public constructor
 *   requires a real `PhpDebugProcess<XdebugConnection>` (itself requiring a real
 *   `XDebugSession`/run configuration, `PhpDebugDriver`, `PhpDebugStrategy`, `ConsoleView`).
 *
 * Because of this, the branch of `processStackFrame` that actually wraps the frame
 * (`session.setCurrentStackFrame(..., PhpPsaStackFrame(project, stackFrame))`) can never be
 * exercised here - it requires `stackFrame` to genuinely be an `XdebugStackFrame` instance.
 *
 * `XDebugSession` itself, however, is a plain platform *interface* (confirmed via `javap`
 * against `app-client.jar` in the `ideaIU-2023.2` distribution - no live session/run
 * configuration required to implement it), so it can be faked with a lightweight anonymous
 * object. This lets us directly unit-test:
 * - `processStackFrame`'s early-return guard when `currentStackFrame` is `null` or is some
 *   other (non-`XdebugStackFrame`) `XStackFrame` - covering the `stackFrame !is XdebugStackFrame`
 *   branch without ever needing a real `XdebugStackFrame`.
 * - `registerSessionListener`'s bootstrap call (`processStackFrame` invoked immediately upon
 *   registration when a stack frame is already current) and its `XDebugSessionListener`
 *   callbacks (`stackFrameChanged`/`sessionPaused`/`sessionResumed`/`sessionStopped`), each
 *   gated the same way.
 * - `processStarted`, using a real (`XDebugProcess`) subclass constructed with our fake
 *   session, since `XDebugProcess` has exactly one abstract member (`getEditorsProvider`).
 * - `registerExistingSessions`, against the real (empty, since no session was started)
 *   `XDebuggerManager.getInstance(project).debugSessions`.
 *
 * Two residual gaps remain (per `build/reports/kover/xml/report.xml`), both left uncovered
 * deliberately rather than for lack of effort:
 * 1. The `forEach` body in `registerExistingSessions` (registering a listener per already-active
 *    session) only runs when `XDebuggerManager` already tracks at least one live session -
 *    populating that requires actually starting a debug session via
 *    `XDebuggerManager.startSession`/`startSessionAndShowTab`, which in turn needs a real
 *    `ExecutionEnvironment` + `XDebugProcessStarter` + run configuration. That is materially
 *    heavier platform-integration ceremony than faking the `XDebugSession` interface itself, so
 *    it is treated the same as the harder-to-reach cases above rather than forced.
 * 2. `processStackFrame`'s `session.currentStackFrame !is PhpPsaStackFrame` branch and its body
 *    are only reachable once `stackFrame` has already passed the `is XdebugStackFrame` check -
 *    i.e. the same unconstructable-without-a-live-connection dependency as `PhpPsaStackFrameTest`.
 */
class PsaXdebugManagerListenerTest : BasePlatformTestCase() {
    fun testRegisterExistingSessionsWithNoActiveSessionsDoesNotThrow() {
        assertEquals(0, XDebuggerManager.getInstance(project).debugSessions.size)

        // Must not throw even though there are no active sessions to register listeners on.
        PsaXdebugManagerListener(project).registerExistingSessions()
    }

    fun testProcessStackFrameReturnsEarlyWhenCurrentStackFrameIsNull() {
        var setCalled = false
        val session = fakeSession(currentStackFrame = null, onSetCurrentStackFrame = { _, _ -> setCalled = true })

        PsaXdebugManagerListener(project).processStackFrame(session)

        assertFalse(setCalled)
    }

    fun testProcessStackFrameReturnsEarlyWhenCurrentStackFrameIsNotXdebugStackFrame() {
        var setCalled = false
        val notXdebugFrame = object : XStackFrame() {}
        val session = fakeSession(currentStackFrame = notXdebugFrame, onSetCurrentStackFrame = { _, _ -> setCalled = true })

        PsaXdebugManagerListener(project).processStackFrame(session)

        assertFalse(setCalled)
    }

    fun testProcessStartedRegistersSessionListenerAndBootstrapsWhenFrameAlreadyCurrent() {
        var addedListener: XDebugSessionListener? = null
        var setCalled = false
        val notXdebugFrame = object : XStackFrame() {}
        val session =
            fakeSession(
                currentStackFrame = notXdebugFrame,
                onAddSessionListener = { addedListener = it },
                onSetCurrentStackFrame = { _, _ -> setCalled = true },
            )
        val process =
            object : XDebugProcess(session) {
                override fun getEditorsProvider() = throw NotImplementedError()
            }

        PsaXdebugManagerListener(project).processStarted(process)

        // registerSessionListener always registers a listener...
        assertNotNull(addedListener)
        // ...and immediately bootstraps processStackFrame since currentStackFrame != null,
        // which then hits the early-return guard (not an XdebugStackFrame) without throwing.
        assertFalse(setCalled)
    }

    fun testProcessStartedDoesNotBootstrapWhenNoCurrentStackFrame() {
        var addedListener: XDebugSessionListener? = null
        val session =
            fakeSession(
                currentStackFrame = null,
                onAddSessionListener = { addedListener = it },
            )
        val process =
            object : XDebugProcess(session) {
                override fun getEditorsProvider() = throw NotImplementedError()
            }

        PsaXdebugManagerListener(project).processStarted(process)

        assertNotNull(addedListener)
    }

    fun testSessionListenerCallbacksInvokeProcessStackFrameGuardWhenFrameCurrent() {
        var addedListener: XDebugSessionListener? = null
        var stackFrameToReturn: XStackFrame? = null
        var setCalled = false
        val session =
            fakeSession(
                currentStackFrame = { stackFrameToReturn },
                onAddSessionListener = { addedListener = it },
                onSetCurrentStackFrame = { _, _ -> setCalled = true },
            )

        PsaXdebugManagerListener(project).invokeRegisterSessionListener(session)

        val listener = addedListener
        assertNotNull(listener)

        // With no current stack frame, none of the callbacks should reach processStackFrame's
        // body (guarded by `if (null !== session.currentStackFrame)` in the listener itself).
        listener!!.stackFrameChanged()
        listener.sessionPaused()
        listener.sessionResumed()
        listener.sessionStopped()
        assertFalse(setCalled)

        // Once a (non-Xdebug) stack frame is current, the callbacks do reach
        // processStackFrame's body, which itself early-returns without calling
        // setCurrentStackFrame.
        stackFrameToReturn = object : XStackFrame() {}
        listener.stackFrameChanged()
        listener.sessionPaused()
        listener.sessionResumed()
        listener.sessionStopped()
        assertFalse(setCalled)
    }

    // ── helpers ───────────────────────────────────────────────────────────

    /**
     * `registerSessionListener` is private (used internally by both `registerExistingSessions`
     * and `processStarted`); invoke it directly via reflection to exercise its listener
     * callbacks without needing a real `XDebuggerManager`-tracked session.
     */
    private fun PsaXdebugManagerListener.invokeRegisterSessionListener(session: XDebugSession) {
        val method = this.javaClass.getDeclaredMethod("registerSessionListener", XDebugSession::class.java)
        method.isAccessible = true
        method.invoke(this, session)
    }

    private fun fakeSession(
        currentStackFrame: XStackFrame?,
        onAddSessionListener: (XDebugSessionListener) -> Unit = {},
        onSetCurrentStackFrame: (XExecutionStack, XStackFrame) -> Unit = { _, _ -> },
    ): XDebugSession = fakeSession({ currentStackFrame }, onAddSessionListener, onSetCurrentStackFrame)

    private fun fakeSession(
        currentStackFrame: () -> XStackFrame?,
        onAddSessionListener: (XDebugSessionListener) -> Unit = {},
        onSetCurrentStackFrame: (XExecutionStack, XStackFrame) -> Unit = { _, _ -> },
    ): XDebugSession =
        object : XDebugSession {
            override fun getProject(): Project = this@PsaXdebugManagerListenerTest.project

            override fun getDebugProcess(): XDebugProcess = throw NotImplementedError()

            override fun isSuspended(): Boolean = false

            override fun getCurrentStackFrame(): XStackFrame? = currentStackFrame()

            override fun getSuspendContext(): XSuspendContext? = null

            override fun getCurrentPosition(): XSourcePosition? = null

            override fun getTopFramePosition(): XSourcePosition? = null

            override fun stepOver(ignoreBreakpoints: Boolean) {}

            override fun stepInto() {}

            override fun stepOut() {}

            override fun forceStepInto() {}

            override fun runToPosition(
                position: XSourcePosition,
                ignoreBreakpoints: Boolean,
            ) {}

            override fun pause() {}

            override fun resume() {}

            override fun showExecutionPoint() {}

            override fun setCurrentStackFrame(
                executionStack: XExecutionStack,
                frame: XStackFrame,
                changeInline: Boolean,
            ) {
                onSetCurrentStackFrame(executionStack, frame)
            }

            override fun updateBreakpointPresentation(
                breakpoint: XLineBreakpoint<*>,
                icon: Icon?,
                errorMessage: String?,
            ) {}

            override fun setBreakpointVerified(breakpoint: XLineBreakpoint<*>) {}

            override fun setBreakpointInvalid(
                breakpoint: XLineBreakpoint<*>,
                errorMessage: String?,
            ) {}

            override fun breakpointReached(
                breakpoint: XBreakpoint<*>,
                evaluatedLogExpression: String?,
                suspendContext: XSuspendContext,
            ): Boolean = false

            override fun positionReached(suspendContext: XSuspendContext) {}

            override fun sessionResumed() {}

            override fun stop() {}

            override fun setBreakpointMuted(muted: Boolean) {}

            override fun areBreakpointsMuted(): Boolean = false

            override fun addSessionListener(
                listener: XDebugSessionListener,
                disposable: Disposable,
            ) {}

            override fun addSessionListener(listener: XDebugSessionListener) {
                onAddSessionListener(listener)
            }

            override fun removeSessionListener(listener: XDebugSessionListener) {}

            override fun reportError(message: String) {}

            override fun reportMessage(
                message: String,
                type: MessageType,
            ) {}

            override fun reportMessage(
                message: String,
                type: MessageType,
                listener: HyperlinkListener?,
            ) {}

            override fun getSessionName(): String = "Test"

            override fun getRunContentDescriptor(): RunContentDescriptor = throw NotImplementedError()

            override fun getRunProfile(): RunProfile? = null

            override fun setPauseActionSupported(supported: Boolean) {}

            override fun rebuildViews() {}

            override fun <V : XSmartStepIntoVariant> smartStepInto(
                handler: XSmartStepIntoHandler<V>,
                variant: V,
            ) {}

            override fun updateExecutionPosition() {}

            override fun initBreakpoints() {}

            override fun getConsoleView(): ConsoleView? = null

            override fun getUI(): RunnerLayoutUi? = null

            override fun isStopped(): Boolean = false

            override fun isPaused(): Boolean = false
        }
}
