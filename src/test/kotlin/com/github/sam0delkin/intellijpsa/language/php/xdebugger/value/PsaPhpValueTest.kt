package com.github.sam0delkin.intellijpsa.language.php.xdebugger.value

import com.github.sam0delkin.intellijpsa.language.php.services.PhpPsaManager
import com.intellij.openapi.components.service
import com.intellij.testFramework.fixtures.BasePlatformTestCase
import com.intellij.xdebugger.frame.XFullValueEvaluator
import com.intellij.xdebugger.frame.XValueNode
import com.intellij.xdebugger.frame.XValuePlace
import com.intellij.xdebugger.frame.presentation.XValuePresentation
import com.jetbrains.php.debug.common.PhpValue
import javax.swing.Icon

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
