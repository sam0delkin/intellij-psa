package com.github.sam0delkin.intellijpsa.language.php.eventSubscriber

import com.github.sam0delkin.intellijpsa.language.php.xdebugger.stackFrame.PhpPsaStackFrame
import com.intellij.openapi.project.Project
import com.intellij.xdebugger.XDebugProcess
import com.intellij.xdebugger.XDebugSession
import com.intellij.xdebugger.XDebugSessionListener
import com.intellij.xdebugger.XDebuggerManager
import com.intellij.xdebugger.XDebuggerManagerListener
import com.jetbrains.php.debug.xdebug.debugger.XdebugStackFrame

class PsaXdebugManagerListener(
    private val project: Project,
) : XDebuggerManagerListener {
    fun registerExistingSessions() {
        val debugManager = XDebuggerManager.getInstance(project)
        debugManager.debugSessions.forEach { session ->
            registerSessionListener(session)
        }
    }

    override fun processStarted(debugProcess: XDebugProcess) {
        registerSessionListener(debugProcess.session)
    }

    private fun registerSessionListener(session: XDebugSession) {
        session.addSessionListener(
            object : XDebugSessionListener {
                override fun stackFrameChanged() {
                    if (null !== session.currentStackFrame) {
                        processStackFrame(session)
                    }
                }

                override fun sessionPaused() {
                    if (null !== session.currentStackFrame) {
                        processStackFrame(session)
                    }
                }

                override fun sessionResumed() {
                    if (null !== session.currentStackFrame) {
                        processStackFrame(session)
                    }
                }

                override fun sessionStopped() {
                    if (null !== session.currentStackFrame) {
                        processStackFrame(session)
                    }
                }
            },
        )
        if (null !== session.currentStackFrame) {
            processStackFrame(session)
        }
    }

    fun processStackFrame(session: XDebugSession) {
        val stackFrame = session.currentStackFrame
        val context = session.suspendContext
        if (stackFrame !is XdebugStackFrame) {
            return
        }
        if (session.currentStackFrame !is PhpPsaStackFrame) {
            session.setCurrentStackFrame(context?.activeExecutionStack!!, PhpPsaStackFrame(project, stackFrame))
        }
    }
}
