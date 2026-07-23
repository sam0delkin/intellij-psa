package com.github.sam0delkin.intellijpsa.language.php.xdebugger.stackFrame

import com.intellij.testFramework.fixtures.BasePlatformTestCase
import com.intellij.xdebugger.frame.XStackFrame
import com.jetbrains.php.debug.xdebug.debugger.XdebugStackFrame

/**
 * `PhpPsaStackFrame` cannot be meaningfully unit-tested in a `BasePlatformTestCase`: unlike
 * `PsaPhpValue` (see `PsaPhpValueTest.kt`), which only has *part* of one method gated behind an
 * unconstructable platform type, every single member of `PhpPsaStackFrame` (`getEqualityObject`,
 * `getEvaluator`, `getSourcePosition`, `customizePresentation`, `computeChildren`) simply
 * delegates to `wrapped`, and the constructor itself requires a non-null
 * `com.jetbrains.php.debug.xdebug.debugger.XdebugStackFrame` - there is no code path that can be
 * exercised without one.
 *
 * Confirmed via `javap` against `php-232.8660.60/php-impl/lib/php.jar` and the platform's
 * `ideaIU-2023.2/lib/app-client.jar`:
 * - `XdebugStackFrame`'s only public constructor is
 *   `XdebugStackFrame(PhpDebugProcess<XdebugConnection>, Runnable, String, int, String, int, int,
 *   DbgpExceptionBreak)` - it is not `final` (so it isn't a hard language-level barrier by
 *   itself), but every viable instance still requires a real `PhpDebugProcess<XdebugConnection>`.
 * - `PhpDebugProcess`'s only public constructor is `PhpDebugProcess(XDebugSession,
 *   PhpDebugDriver<S>, PhpDebugStrategy, ConsoleView, boolean)`. `XDebugSession` is a plain
 *   platform *interface* and so is fakeable (see `PsaXdebugManagerListenerTest.kt`, which does
 *   exactly that), but `PhpDebugDriver` alone declares ~15 abstract protocol-level methods
 *   (`registerBreakpoint`, `registerHandlers`, `start`, `createPathFromUrlExtractor`, ...) and
 *   `ConsoleView` is a full UI console interface - faking the whole chain just to obtain a
 *   constructible instance is disproportionate ceremony for a class with no interesting logic
 *   of its own.
 * - Even granting a successfully constructed instance, `PhpStackFrame.computeChildren` (the
 *   `final` method `XdebugStackFrame` inherits and that `PhpPsaStackFrame.computeChildren`
 *   delegates to) calls the abstract `computeVariables`, which performs real DBGP-protocol
 *   variable retrieval over the live connection - so the one method with any branching logic
 *   worth covering (the `value is PhpValue` check in `PhpPsaStackFrame.computeChildren`'s
 *   children-wrapping callback) cannot be reached even with a fully faked object graph; it
 *   requires a live XDebug session to invoke `wrapped.computeChildren` with real children.
 *
 * This is the same category of platform-integration limitation as `XdebugValue` in
 * `PsaPhpValueTest.kt`'s class doc comment, one level deeper. 0% coverage on this file is the
 * expected, documented outcome - not a gap in test effort.
 */
class PhpPsaStackFrameTest : BasePlatformTestCase() {
    /**
     * Not a behavioral test of `PhpPsaStackFrame` itself (there is none reachable - see above);
     * this documents/guards the exact precondition that makes it untestable, so that if a future
     * refactor ever loosens the constructor (e.g. to accept an interface instead of the concrete
     * `XdebugStackFrame`), this test fails and signals that the KDoc above should be revisited.
     */
    fun testWrapsXStackFrameAndRequiresAConcreteXdebugStackFrame() {
        assertTrue(XStackFrame::class.java.isAssignableFrom(PhpPsaStackFrame::class.java))

        val constructor = PhpPsaStackFrame::class.java.declaredConstructors.single()
        val paramTypes = constructor.parameterTypes

        assertEquals(2, paramTypes.size)
        assertEquals(XdebugStackFrame::class.java, paramTypes[1])
    }
}
