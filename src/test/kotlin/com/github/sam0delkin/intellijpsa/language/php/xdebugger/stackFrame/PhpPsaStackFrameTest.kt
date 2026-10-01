package com.github.sam0delkin.intellijpsa.language.php.xdebugger.stackFrame

import com.github.sam0delkin.intellijpsa.language.php.xdebugger.value.PsaPhpValue
import com.intellij.testFramework.fixtures.BasePlatformTestCase
import com.intellij.ui.ColoredTextContainer
import com.intellij.ui.SimpleTextAttributes
import com.intellij.xdebugger.XSourcePosition
import com.intellij.xdebugger.frame.XCompositeNode
import com.intellij.xdebugger.frame.XDebuggerTreeNodeHyperlink
import com.intellij.xdebugger.frame.XValue
import com.intellij.xdebugger.frame.XValueChildrenList
import com.jetbrains.php.debug.common.PhpValue
import com.jetbrains.php.debug.xdebug.debugger.XdebugPhpEvaluator
import com.jetbrains.php.debug.xdebug.debugger.XdebugStackFrame
import org.mockito.ArgumentMatchers.any
import org.mockito.ArgumentMatchers.anyBoolean
import org.mockito.Mockito.doAnswer
import org.mockito.Mockito.mock
import org.mockito.Mockito.verify
import org.mockito.Mockito.`when`

/**
 * `XdebugStackFrame` needs a live debug process to construct, so it's mocked (Mockito).
 */
class PhpPsaStackFrameTest : BasePlatformTestCase() {
    private val wrapped: XdebugStackFrame = mock(XdebugStackFrame::class.java)

    private fun frame() = PhpPsaStackFrame(project, wrapped)

    fun testSimpleMembersDelegateToWrappedFrame() {
        val equality = Any()
        val evaluator = mock(XdebugPhpEvaluator::class.java)
        val position = mock(XSourcePosition::class.java)
        `when`(wrapped.equalityObject).thenReturn(equality)
        `when`(wrapped.evaluator).thenReturn(evaluator)
        `when`(wrapped.sourcePosition).thenReturn(position)
        val container = mock(ColoredTextContainer::class.java)

        frame().customizePresentation(container)

        assertSame(equality, frame().equalityObject)
        assertSame(evaluator, frame().evaluator)
        assertSame(position, frame().sourcePosition)
        verify(wrapped).customizePresentation(container)
    }

    // PHP values among the frame's children are wrapped in PsaPhpValue (so the `__toString`
    // formatter applies); anything else is passed through untouched.
    fun testComputeChildrenWrapsPhpValuesOnly() {
        val phpValue = mock(PhpValue::class.java)
        val otherValue = mock(XValue::class.java)
        doAnswer {
            val list = XValueChildrenList()
            list.add("user", phpValue)
            list.add("other", otherValue)
            it.getArgument<XCompositeNode>(0).addChildren(list, true)
        }.`when`(wrapped).computeChildren(any())
        var received: XValueChildrenList? = null
        val node = mock(XCompositeNode::class.java)
        doAnswer { received = it.getArgument(0) }.`when`(node).addChildren(any(), anyBoolean())

        frame().computeChildren(node)

        val children = received!!
        assertEquals(listOf("user", "other"), (0 until children.size()).map { children.getName(it) })
        assertInstanceOf(children.getValue(0), PsaPhpValue::class.java)
        assertSame(otherValue, children.getValue(1))
    }

    @Suppress("DEPRECATION")
    fun testComputeChildrenForwardsNodeStatusCalls() {
        val link = mock(XDebuggerTreeNodeHyperlink::class.java)
        doAnswer {
            val inner = it.getArgument<XCompositeNode>(0)
            inner.tooManyChildren(5)
            inner.setAlreadySorted(true)
            inner.setErrorMessage("error")
            inner.setErrorMessage("linked error", link)
            inner.setMessage("message", null, SimpleTextAttributes.REGULAR_ATTRIBUTES, link)
        }.`when`(wrapped).computeChildren(any())
        val node = mock(XCompositeNode::class.java)

        frame().computeChildren(node)

        verify(node).tooManyChildren(5)
        verify(node).setAlreadySorted(true)
        verify(node).setErrorMessage("error")
        verify(node).setErrorMessage("linked error", link)
        verify(node).setMessage("message", null, SimpleTextAttributes.REGULAR_ATTRIBUTES, link)
    }
}
