package com.github.sam0delkin.intellijpsa.language.php.xdebugger.stackFrame

import com.github.sam0delkin.intellijpsa.language.php.xdebugger.value.PsaPhpValue
import com.intellij.openapi.project.Project
import com.intellij.ui.ColoredTextContainer
import com.intellij.ui.SimpleTextAttributes
import com.intellij.xdebugger.XSourcePosition
import com.intellij.xdebugger.evaluation.XDebuggerEvaluator
import com.intellij.xdebugger.frame.XCompositeNode
import com.intellij.xdebugger.frame.XDebuggerTreeNodeHyperlink
import com.intellij.xdebugger.frame.XStackFrame
import com.intellij.xdebugger.frame.XValueChildrenList
import com.jetbrains.php.debug.common.PhpValue
import com.jetbrains.php.debug.xdebug.debugger.XdebugStackFrame
import javax.swing.Icon

class PhpPsaStackFrame(
    private val project: Project,
    private val wrapped: XdebugStackFrame,
) : XStackFrame() {
    override fun getEqualityObject(): Any? = wrapped.equalityObject

    override fun getEvaluator(): XDebuggerEvaluator? = wrapped.evaluator

    override fun getSourcePosition(): XSourcePosition? = wrapped.sourcePosition

    override fun customizePresentation(component: ColoredTextContainer) {
        wrapped.customizePresentation(component)
    }

    override fun computeChildren(node: XCompositeNode) {
        wrapped.computeChildren(
            object : XCompositeNode {
                override fun addChildren(
                    list: XValueChildrenList,
                    b: Boolean,
                ) {
                    val newList = XValueChildrenList(list.size())
                    for (i in 0 until list.size()) {
                        val value = list.getValue(i)
                        if (value is PhpValue) {
                            newList.add(list.getName(i), PsaPhpValue(project, value, wrapped.evaluator))
                        } else {
                            newList.add(list.getName(i), value)
                        }
                    }
                    node.addChildren(newList, b)
                }

                override fun tooManyChildren(p0: Int) {
                    node.tooManyChildren(p0)
                }

                override fun setAlreadySorted(p0: Boolean) {
                    node.setAlreadySorted(p0)
                }

                override fun setErrorMessage(p0: String) {
                    node.setErrorMessage(p0)
                }

                override fun setErrorMessage(
                    p0: String,
                    p1: XDebuggerTreeNodeHyperlink?,
                ) {
                    node.setErrorMessage(p0, p1)
                }

                override fun setMessage(
                    p0: String,
                    p1: Icon?,
                    p2: SimpleTextAttributes,
                    p3: XDebuggerTreeNodeHyperlink?,
                ) {
                    node.setMessage(p0, p1, p2, p3)
                }
            },
        )
    }
}
