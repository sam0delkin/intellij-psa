package com.github.sam0delkin.intellijpsa.language.php.xdebugger.value

import com.github.sam0delkin.intellijpsa.language.php.settings.PhpPsaSettings
import com.github.sam0delkin.intellijpsa.language.php.xdebugger.value.presentation.PsaPhpValuePresentation
import com.github.sam0delkin.intellijpsa.settings.Settings
import com.intellij.notification.NotificationGroupManager
import com.intellij.notification.NotificationType
import com.intellij.openapi.components.service
import com.intellij.openapi.project.Project
import com.intellij.ui.SimpleTextAttributes
import com.intellij.util.ReflectionUtil
import com.intellij.util.ThreeState
import com.intellij.xdebugger.XExpression
import com.intellij.xdebugger.evaluation.XDebuggerEvaluator
import com.intellij.xdebugger.evaluation.XInstanceEvaluator
import com.intellij.xdebugger.frame.XCompositeNode
import com.intellij.xdebugger.frame.XDebuggerTreeNodeHyperlink
import com.intellij.xdebugger.frame.XInlineDebuggerDataCallback
import com.intellij.xdebugger.frame.XNavigatable
import com.intellij.xdebugger.frame.XReferrersProvider
import com.intellij.xdebugger.frame.XValueChildrenList
import com.intellij.xdebugger.frame.XValueModifier
import com.intellij.xdebugger.frame.XValueNode
import com.intellij.xdebugger.frame.XValuePlace
import com.intellij.xdebugger.frame.presentation.XValuePresentation
import com.intellij.xdebugger.impl.ui.tree.nodes.XValueNodeImpl
import com.jetbrains.php.debug.TypeInfo
import com.jetbrains.php.debug.common.PhpCompactValuePresentation
import com.jetbrains.php.debug.common.PhpEvaluationResultProcessor
import com.jetbrains.php.debug.common.PhpNavigatableValue
import com.jetbrains.php.debug.common.PhpValue
import com.jetbrains.php.debug.xdebug.debugger.XdebugPhpEvaluator
import com.jetbrains.php.debug.xdebug.debugger.XdebugValue
import com.jetbrains.php.lang.psi.resolve.types.PhpType
import com.jetbrains.rd.framework.base.deepClonePolymorphic
import org.jetbrains.concurrency.Promise
import java.util.UUID
import javax.swing.Icon

class PathUtils {
    companion object {
        fun getRootVariableName(path: String): String {
            val parts = path.split(Regex("::|->|\\[|]"))

            return parts[0]
        }
    }
}

class PsaPhpValue(
    private val project: Project,
    private val wrapped: PhpValue,
    private val evaluator: XDebuggerEvaluator?,
) : PhpValue() {
    override fun computePresentation(
        value: XValueNode,
        place: XValuePlace,
    ) {
        val settings = project.service<Settings>()
        val phpSettings = project.service<PhpPsaSettings>()

        if (!phpSettings.enabled || null === phpSettings.toStringValueFormatter) {
            wrapped.computePresentation(value, place)

            return
        }

        val clonedValue = value.deepClonePolymorphic()
        val clonedPlace = place.deepClonePolymorphic()
        wrapped.computePresentation(clonedValue, clonedPlace)

        if (value is XValueNodeImpl && this.wrapped is XdebugValue) {
            val type = this.wrapped.type
            val presentation = value.valuePresentation
            if (!PhpType.isScalar(type, project) && presentation is PhpCompactValuePresentation) {
                if (null != evaluator && evaluator is XdebugPhpEvaluator) {
                    val variablePath =
                        this.wrapped.fullName
                            .substring(1)
                            .replace(Regex("\\*[^*]+\\*"), "")
                            .replace(Regex("[\"']"), "")
                    val rootName = PathUtils.getRootVariableName(variablePath)
                    val name = UUID.randomUUID().toString()
                    val userCode = phpSettings.toStringValueFormatter!!
                    val phpCode =
                        @Suppress("ktlint:standard:max-line-length")
                        String.format(
                            "\$GLOBALS['IDE_EVAL_CACHE']['$name'] = (function (\$root) {\n" +
                                "    try{\n" +
                                "        set_error_handler(function () {\n" +
                                "            return true;\n" +
                                "        });\n" +
                                "        \$path = '$variablePath';\n" +
                                "        \$parts = preg_split('/->|\\[|\\]|::/', \$path, -1, PREG_SPLIT_NO_EMPTY);\n\n" +
                                "        if (count(\$parts) > 1) {\n" +
                                "            array_shift(\$parts);\n" +
                                "        } else {" +
                                "            \$parts = [];\n" +
                                "        }\n\n" +
                                "        \$current = \$root;\n" +
                                "        foreach (\$parts as \$part) {\n" +
                                "            if (is_object(\$current)) {\n" +
                                "                \$reflector = new \\ReflectionObject(\$current);" +
                                "                if (!\$reflector->hasProperty(\$part)) {" +
                                "                    \$reflector = \$reflector->getParentClass();\n" +
                                "                }\n" +
                                "                \$property = \$reflector->getProperty(\$part);\n" +
                                "                \$property->setAccessible(true);\n" +
                                "                \$current = \$property->getValue(\$current);\n" +
                                "            } elseif (is_array(\$current)) {\n" +
                                "                \$current = \$current[\$part] ?: null;\n" +
                                "            } else {\n" +
                                "                return '';\n" +
                                "            }\n" +
                                "        }\n" +
                                "        \$result = (function (\$value) { $userCode })(\$current);\n\n" +
                                "        if (isset(\$GLOBALS['IDE_EVAL_CACHE']['old_$variablePath']) && \$GLOBALS['IDE_EVAL_CACHE']['old_$variablePath'] === \$result) {\n" +
                                "            return '__PSA__VALUE__UNCHANGED__|' . \$result;\n" +
                                "        }\n\n" +
                                "        \$GLOBALS['IDE_EVAL_CACHE']['old_$variablePath'] = \$result;\n\n" +
                                "        return \$result;\n" +
                                "    } catch (Exception \$e) {\n" +
                                "        return \$e->getMessage();\n" +
                                "    } finally {\n" +
                                "        restore_error_handler();\n" +
                                "    }\n" +
                                "    \n" +
                                "})($$rootName);\n\n",
                        )

                    evaluator.evaluateCodeFragment(
                        phpCode,
                        object : PhpEvaluationResultProcessor {
                            override fun success(
                                result: String,
                                type: TypeInfo,
                                navigation: PhpNavigatableValue,
                            ) {
                                val originalResult = result.replace("__PSA__VALUE__UNCHANGED__|", "")

                                if (originalResult.isEmpty()) {
                                    return
                                }

                                value.setPresentation(
                                    value.icon,
                                    PsaPhpValuePresentation(originalResult, presentation.type),
                                    true,
                                )

                                if (result.indexOf("__PSA__VALUE__UNCHANGED__") == 0) {
                                    val field =
                                        ReflectionUtil.findField(
                                            XValueNodeImpl::class.java,
                                            null,
                                            "myChanged",
                                        )
                                    field.trySetAccessible()
                                    field.set(value, false)
                                }
                            }

                            override fun error(error: String) {
                                if (settings.showErrors) {
                                    NotificationGroupManager
                                        .getInstance()
                                        .getNotificationGroup("PSA Notification")
                                        .createNotification(
                                            "Error executing __toString code: $error",
                                            NotificationType.ERROR,
                                        ).notify(project)
                                }
                            }
                        },
                    )

                    return
                }
            }
        }

        wrapped.computePresentation(value, place)
    }

    override fun defaultPresentation(
        value: String,
        type: String?,
    ): XValuePresentation = PsaPhpValuePresentation(value, type)

    override fun getEvaluationExpression(): String? = wrapped.evaluationExpression

    override fun calculateEvaluationExpression(): Promise<XExpression?> = wrapped.calculateEvaluationExpression()

    override fun getInstanceEvaluator(): XInstanceEvaluator? = wrapped.getInstanceEvaluator()

    override fun getModifier(): XValueModifier? = wrapped.modifier

    override fun computeSourcePosition(navigatable: XNavigatable) {
        wrapped.computeSourcePosition(navigatable)
    }

    override fun computeInlineDebuggerData(callback: XInlineDebuggerDataCallback): ThreeState = wrapped.computeInlineDebuggerData(callback)

    override fun canNavigateToSource(): Boolean = wrapped.canNavigateToSource()

    override fun canNavigateToTypeSource(): Boolean = wrapped.canNavigateToTypeSource()

    override fun canNavigateToTypeSourceAsync(): Promise<Boolean?>? = wrapped.canNavigateToTypeSourceAsync()

    override fun computeTypeSourcePosition(navigatable: XNavigatable) {
        wrapped.computeTypeSourcePosition(navigatable)
    }

    override fun getReferrersProvider(): XReferrersProvider? = wrapped.getReferrersProvider()

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
                            newList.add(list.getName(i), PsaPhpValue(project, value, evaluator))
                        } else {
                            newList.add(list.getName(i), value)
                        }
                    }
                    node.addChildren(newList, b)
                }

                @Deprecated("Deprecated in Java")
                override fun tooManyChildren(p0: Int) {
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
