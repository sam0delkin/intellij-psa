package com.github.sam0delkin.intellijpsa.language.php.xdebugger.value

import com.github.sam0delkin.intellijpsa.language.php.services.PhpPsaManager
import com.github.sam0delkin.intellijpsa.language.php.xdebugger.value.presentation.PsaPhpValuePresentation
import com.intellij.openapi.components.service
import com.intellij.testFramework.fixtures.BasePlatformTestCase
import com.intellij.ui.SimpleTextAttributes
import com.intellij.util.ThreeState
import com.intellij.xdebugger.evaluation.XInstanceEvaluator
import com.intellij.xdebugger.frame.XCompositeNode
import com.intellij.xdebugger.frame.XDebuggerTreeNodeHyperlink
import com.intellij.xdebugger.frame.XFullValueEvaluator
import com.intellij.xdebugger.frame.XInlineDebuggerDataCallback
import com.intellij.xdebugger.frame.XNavigatable
import com.intellij.xdebugger.frame.XReferrersProvider
import com.intellij.xdebugger.frame.XValue
import com.intellij.xdebugger.frame.XValueChildrenList
import com.intellij.xdebugger.frame.XValueModifier
import com.intellij.xdebugger.frame.XValueNode
import com.intellij.xdebugger.frame.XValuePlace
import com.intellij.xdebugger.frame.presentation.XValuePresentation
import com.jetbrains.php.debug.common.PhpValue
import org.jetbrains.concurrency.resolvedPromise
import javax.swing.Icon

/**
 * Two residual coverage gaps remain in `PsaPhpValue.kt`, both structural rather than missing
 * test effort:
 *
 * 1. `class PathUtils` (line 39, the implicit no-arg constructor): `PathUtils` is only ever used
 *    via its `companion object`, so its own constructor line never executes - not something a
 *    test can trigger without pointlessly instantiating a class that's designed to never be
 *    instantiated. Same category as this project's documented `const val` inlining / interface
 *    `DefaultImpls` Kover gaps.
 * 2. The `wrapped is XdebugValue` branch and everything nested under it in `computePresentation`
 *    (roughly lines 64-193 - the `PhpType.isScalar` check, the full `__toString` evaluation
 *    closure, and both `PhpEvaluationResultProcessor` callback bodies): reaching this branch
 *    requires `wrapped` to literally be an instance of `com.jetbrains.php.debug.xdebug.debugger.XdebugValue`,
 *    which is a `public final class` whose only public constructors require a real
 *    `PhpDebugProcess<XdebugConnection>` and `DbgpProperty` sourced from an actual live XDebug
 *    connection - `PhpDebugProcess` itself extends the platform's `XDebugProcess`, which requires
 *    a real `XDebugSession`/run configuration to construct. `evaluator` similarly must be an
 *    `XdebugPhpEvaluator` (also requiring a live `PhpDebugProcess`). Neither can be faked via
 *    `java.lang.reflect.Proxy` (both are concrete classes, not interfaces) or constructed
 *    meaningfully in `BasePlatformTestCase`, which has no live debug session. This is a genuine
 *    platform-integration limitation, not a gap in test effort - all reachable branches
 *    (early-return guards, every simple delegation method, and `computeChildren`'s
 *    PhpValue-wrapping logic) are covered below.
 */
class PsaPhpValueTest : BasePlatformTestCase() {
    // ── PathUtils ─────────────────────────────────────────────────────────

    fun testGetRootVariableNameSimpleVariable() {
        assertEquals("myVar", PathUtils.getRootVariableName("myVar"))
    }

    fun testGetRootVariableNameObjectProperty() {
        assertEquals("myVar", PathUtils.getRootVariableName("myVar->property"))
    }

    fun testGetRootVariableNameArrayAccess() {
        assertEquals("myVar", PathUtils.getRootVariableName("myVar[0]"))
    }

    fun testGetRootVariableNameStaticProperty() {
        assertEquals("myVar", PathUtils.getRootVariableName("myVar::CONST"))
    }

    fun testGetRootVariableNameNestedPath() {
        assertEquals("obj", PathUtils.getRootVariableName("obj->prop->nested[0]"))
    }

    // ── computePresentation: early-return paths ────────────────────────────

    fun testComputePresentationWhenPluginDisabledDelegatesToWrappedOnce() {
        val phpSettings = project.service<PhpPsaManager>().getSettings()
        phpSettings.enabled = false
        phpSettings.toStringValueFormatter = "return (string)\$value;"

        val calls = mutableListOf<XValueNode>()
        val wrapped = stubPhpValue { node, _ -> calls.add(node) }
        val node = stubXValueNode()

        PsaPhpValue(project, wrapped, null).computePresentation(node, XValuePlace.TREE)

        assertEquals(1, calls.size)
        assertSame(node, calls[0])
    }

    fun testComputePresentationWhenFormatterNullDelegatesToWrappedOnce() {
        val phpSettings = project.service<PhpPsaManager>().getSettings()
        phpSettings.enabled = true
        phpSettings.toStringValueFormatter = null

        val calls = mutableListOf<XValueNode>()
        val wrapped = stubPhpValue { node, _ -> calls.add(node) }
        val node = stubXValueNode()

        PsaPhpValue(project, wrapped, null).computePresentation(node, XValuePlace.TREE)

        assertEquals(1, calls.size)
        assertSame(node, calls[0])
    }

    /**
     * Regression test: wrapped must receive a node whose setPresentation delegates to the
     * original node (not a clone), and the call must happen exactly once.
     *
     * Previously, deepClonePolymorphic was used before passing the node to
     * wrapped.computePresentation. In 2026.1 that either threw or created a detached clone
     * so value.valuePresentation stayed null. The fix passes an intercepting wrapper whose
     * setPresentation forwards to the original node.
     */
    fun testComputePresentationWithFormatterEnabledCallsWrappedWithOriginalNode() {
        val phpSettings = project.service<PhpPsaManager>().getSettings()
        phpSettings.enabled = true
        phpSettings.toStringValueFormatter = "return (string)\$value;"

        val calls = mutableListOf<XValueNode>()
        val wrapped = stubPhpValue { node, _ -> calls.add(node) }
        val node = stubXValueNode()

        // evaluator is null so PHP evaluation is skipped, but wrapped must still be called
        PsaPhpValue(project, wrapped, null).computePresentation(node, XValuePlace.TREE)

        assertEquals("wrapped.computePresentation must be called exactly once", 1, calls.size)
    }

    /**
     * Regression test for the async setPresentation race (PhpStorm 2026.1).
     *
     * In 2026.1, XdebugValue.computePresentation defers setPresentation to a background thread
     * via evaluateToStringIfNoSideEffects, so reading value.valuePresentation synchronously
     * after the call returns null — breaking __toString evaluation.
     *
     * The fix intercepts setPresentation on a wrapper node so the formatter fires at the exact
     * moment the presentation is set, regardless of whether it is sync or async.
     * This test verifies that the intercepting wrapper forwards setPresentation to the original node.
     */
    fun testComputePresentationWithFormatterEnabledForwardsSetPresentationToOriginalNode() {
        val phpSettings = project.service<PhpPsaManager>().getSettings()
        phpSettings.enabled = true
        phpSettings.toStringValueFormatter = "return (string)\$value;"

        val presentationsOnOriginal = mutableListOf<XValuePresentation>()
        val originalNode =
            object : XValueNode {
                override fun isObsolete(): Boolean = false

                override fun setPresentation(
                    icon: Icon?,
                    type: String?,
                    value: String,
                    hasChildren: Boolean,
                ) {}

                override fun setPresentation(
                    icon: Icon?,
                    presentation: XValuePresentation,
                    hasChildren: Boolean,
                ) {
                    presentationsOnOriginal.add(presentation)
                }

                override fun setFullValueEvaluator(evaluator: XFullValueEvaluator) {}
            }

        val capturedNode = arrayOfNulls<XValueNode>(1)
        val wrapped = stubPhpValue { node, _ -> capturedNode[0] = node }

        PsaPhpValue(project, wrapped, null).computePresentation(originalNode, XValuePlace.TREE)

        // The wrapper node must have been passed to wrapped, not the original
        assertNotNull(capturedNode[0])
        // Calling setPresentation on the wrapper must forward to the original node
        val testPresentation =
            object : XValuePresentation() {
                override fun renderValue(renderer: XValueTextRenderer) {}
            }
        capturedNode[0]!!.setPresentation(null, testPresentation, false)
        assertEquals(1, presentationsOnOriginal.size)
        assertSame(testPresentation, presentationsOnOriginal[0])
    }

    // ── defaultPresentation ──────────────────────────────────────────────

    fun testDefaultPresentationReturnsPsaPhpValuePresentation() {
        val wrapped = stubPhpValue { _, _ -> }
        val method =
            PsaPhpValue::class.java.getDeclaredMethod(
                "defaultPresentation",
                String::class.java,
                String::class.java,
            )
        method.isAccessible = true

        val presentation = method.invoke(PsaPhpValue(project, wrapped, null), "42", "int")

        assertTrue(presentation is PsaPhpValuePresentation)
    }

    // ── simple delegation methods ────────────────────────────────────────

    fun testGetEvaluationExpressionDelegatesToWrapped() {
        val wrapped =
            object : PhpValue() {
                override fun computePresentation(
                    node: XValueNode,
                    place: XValuePlace,
                ) {}

                override fun getEvaluationExpression(): String = "wrapped-expr"
            }

        assertEquals("wrapped-expr", PsaPhpValue(project, wrapped, null).evaluationExpression)
    }

    fun testCalculateEvaluationExpressionDelegatesToWrapped() {
        val marker = resolvedPromise<com.intellij.xdebugger.XExpression?>(null)
        val wrapped =
            object : PhpValue() {
                override fun computePresentation(
                    node: XValueNode,
                    place: XValuePlace,
                ) {}

                override fun calculateEvaluationExpression() = marker
            }

        assertSame(marker, PsaPhpValue(project, wrapped, null).calculateEvaluationExpression())
    }

    fun testGetInstanceEvaluatorDelegatesToWrapped() {
        val marker =
            object : XInstanceEvaluator {
                override fun evaluate(
                    context: com.intellij.xdebugger.evaluation.XDebuggerEvaluator.XEvaluationCallback,
                    node: com.intellij.xdebugger.frame.XStackFrame,
                ) {}
            }
        val wrapped =
            object : PhpValue() {
                override fun computePresentation(
                    node: XValueNode,
                    place: XValuePlace,
                ) {}

                override fun getInstanceEvaluator(): XInstanceEvaluator = marker
            }

        assertSame(marker, PsaPhpValue(project, wrapped, null).instanceEvaluator)
    }

    fun testGetModifierDelegatesToWrapped() {
        val marker =
            object : XValueModifier() {
                override fun setValue(
                    expression: com.intellij.xdebugger.XExpression,
                    callback: XValueModifier.XModificationCallback,
                ) {}
            }
        val wrapped =
            object : PhpValue() {
                override fun computePresentation(
                    node: XValueNode,
                    place: XValuePlace,
                ) {}

                override fun getModifier(): XValueModifier = marker
            }

        assertSame(marker, PsaPhpValue(project, wrapped, null).modifier)
    }

    fun testComputeSourcePositionDelegatesToWrapped() {
        var received: XNavigatable? = null
        val wrapped =
            object : PhpValue() {
                override fun computePresentation(
                    node: XValueNode,
                    place: XValuePlace,
                ) {}

                override fun computeSourcePosition(navigatable: XNavigatable) {
                    received = navigatable
                }
            }
        val navigatable = XNavigatable { }

        PsaPhpValue(project, wrapped, null).computeSourcePosition(navigatable)

        assertSame(navigatable, received)
    }

    fun testComputeInlineDebuggerDataDelegatesToWrapped() {
        val callback =
            object : XInlineDebuggerDataCallback() {
                override fun computed(position: com.intellij.xdebugger.XSourcePosition?) {}
            }
        val wrapped =
            object : PhpValue() {
                override fun computePresentation(
                    node: XValueNode,
                    place: XValuePlace,
                ) {}

                override fun computeInlineDebuggerData(cb: XInlineDebuggerDataCallback): ThreeState = ThreeState.YES
            }

        assertEquals(ThreeState.YES, PsaPhpValue(project, wrapped, null).computeInlineDebuggerData(callback))
    }

    fun testCanNavigateToSourceDelegatesToWrapped() {
        val wrapped =
            object : PhpValue() {
                override fun computePresentation(
                    node: XValueNode,
                    place: XValuePlace,
                ) {}

                override fun canNavigateToSource(): Boolean = true
            }

        assertTrue(PsaPhpValue(project, wrapped, null).canNavigateToSource())
    }

    fun testCanNavigateToTypeSourceDelegatesToWrapped() {
        val wrapped =
            object : PhpValue() {
                override fun computePresentation(
                    node: XValueNode,
                    place: XValuePlace,
                ) {}

                override fun canNavigateToTypeSource(): Boolean = true
            }

        assertTrue(PsaPhpValue(project, wrapped, null).canNavigateToTypeSource())
    }

    fun testCanNavigateToTypeSourceAsyncDelegatesToWrapped() {
        val marker = resolvedPromise<Boolean?>(true)
        val wrapped =
            object : PhpValue() {
                override fun computePresentation(
                    node: XValueNode,
                    place: XValuePlace,
                ) {}

                override fun canNavigateToTypeSourceAsync() = marker
            }

        assertSame(marker, PsaPhpValue(project, wrapped, null).canNavigateToTypeSourceAsync())
    }

    fun testComputeTypeSourcePositionDelegatesToWrapped() {
        var received: XNavigatable? = null
        val wrapped =
            object : PhpValue() {
                override fun computePresentation(
                    node: XValueNode,
                    place: XValuePlace,
                ) {}

                override fun computeTypeSourcePosition(navigatable: XNavigatable) {
                    received = navigatable
                }
            }
        val navigatable = XNavigatable { }

        PsaPhpValue(project, wrapped, null).computeTypeSourcePosition(navigatable)

        assertSame(navigatable, received)
    }

    fun testGetReferrersProviderDelegatesToWrapped() {
        val marker =
            object : XReferrersProvider() {
                override fun getReferringObjectsValue(): XValue? = null
            }
        val wrapped =
            object : PhpValue() {
                override fun computePresentation(
                    node: XValueNode,
                    place: XValuePlace,
                ) {}

                override fun getReferrersProvider(): XReferrersProvider = marker
            }

        assertSame(marker, PsaPhpValue(project, wrapped, null).referrersProvider)
    }

    // ── computeChildren ──────────────────────────────────────────────────

    fun testComputeChildrenWrapsPhpValueChildrenAndPassesOthersThrough() {
        val childPhpValue =
            object : PhpValue() {
                override fun computePresentation(
                    node: XValueNode,
                    place: XValuePlace,
                ) {}
            }
        val childOtherValue =
            object : XValue() {
                override fun computePresentation(
                    node: XValueNode,
                    place: XValuePlace,
                ) {}
            }
        val wrapped =
            object : PhpValue() {
                override fun computePresentation(
                    node: XValueNode,
                    place: XValuePlace,
                ) {}

                override fun computeChildren(node: XCompositeNode) {
                    val list = XValueChildrenList(2)
                    list.add("phpChild", childPhpValue)
                    list.add("otherChild", childOtherValue)
                    node.addChildren(list, true)

                    @Suppress("DEPRECATION")
                    node.tooManyChildren(0)
                    node.setAlreadySorted(true)
                    node.setErrorMessage("boom")
                    node.setErrorMessage("boom2", null)
                    node.setMessage("msg", null, SimpleTextAttributes.REGULAR_ATTRIBUTES, null)
                }
            }

        var receivedList: XValueChildrenList? = null
        var receivedLast: Boolean? = null
        var sortedFlag: Boolean? = null
        val errorMessages = mutableListOf<String>()
        val messages = mutableListOf<String>()
        val targetNode =
            object : XCompositeNode {
                override fun addChildren(
                    list: XValueChildrenList,
                    last: Boolean,
                ) {
                    receivedList = list
                    receivedLast = last
                }

                @Deprecated("Deprecated in Java")
                override fun tooManyChildren(remaining: Int) {}

                override fun setAlreadySorted(alreadySorted: Boolean) {
                    sortedFlag = alreadySorted
                }

                override fun setErrorMessage(errorMessage: String) {
                    errorMessages.add(errorMessage)
                }

                override fun setErrorMessage(
                    errorMessage: String,
                    link: XDebuggerTreeNodeHyperlink?,
                ) {
                    errorMessages.add(errorMessage)
                }

                override fun setMessage(
                    message: String,
                    icon: Icon?,
                    attributes: SimpleTextAttributes,
                    link: XDebuggerTreeNodeHyperlink?,
                ) {
                    messages.add(message)
                }
            }

        PsaPhpValue(project, wrapped, null).computeChildren(targetNode)

        assertNotNull(receivedList)
        assertEquals(2, receivedList!!.size())
        assertTrue(receivedList.getValue(0) is PsaPhpValue)
        assertSame(childOtherValue, receivedList.getValue(1))
        assertEquals(true, receivedLast)
        assertEquals(true, sortedFlag)
        assertEquals(listOf("boom", "boom2"), errorMessages)
        assertEquals(listOf("msg"), messages)
    }

    // ── helpers ───────────────────────────────────────────────────────────

    private fun stubPhpValue(onCompute: (XValueNode, XValuePlace) -> Unit): PhpValue =
        object : PhpValue() {
            override fun computePresentation(
                node: XValueNode,
                place: XValuePlace,
            ) = onCompute(node, place)
        }

    private fun stubXValueNode(): XValueNode =
        object : XValueNode {
            override fun isObsolete(): Boolean = false

            override fun setPresentation(
                icon: Icon?,
                type: String?,
                value: String,
                hasChildren: Boolean,
            ) {}

            override fun setPresentation(
                icon: Icon?,
                presentation: XValuePresentation,
                hasChildren: Boolean,
            ) {}

            override fun setFullValueEvaluator(evaluator: XFullValueEvaluator) {}
        }
}
