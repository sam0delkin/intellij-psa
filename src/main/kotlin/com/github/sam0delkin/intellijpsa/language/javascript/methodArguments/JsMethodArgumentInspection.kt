package com.github.sam0delkin.intellijpsa.language.javascript.methodArguments

import com.github.sam0delkin.intellijpsa.language.javascript.settings.JsPsaSettings
import com.github.sam0delkin.intellijpsa.settings.Settings
import com.intellij.codeInspection.LocalInspectionTool
import com.intellij.codeInspection.ProblemHighlightType
import com.intellij.codeInspection.ProblemsHolder
import com.intellij.lang.javascript.psi.JSLiteralExpression
import com.intellij.openapi.components.service
import com.intellij.psi.PsiElement
import com.intellij.psi.PsiElementVisitor

class JsMethodArgumentInspection : LocalInspectionTool() {
    override fun buildVisitor(
        holder: ProblemsHolder,
        isOnTheFly: Boolean,
    ): PsiElementVisitor =
        object : PsiElementVisitor() {
            override fun visitElement(element: PsiElement) {
                if (element !is JSLiteralExpression || !element.isStringLiteral) return

                val project = element.project
                val settings = project.service<Settings>()
                val jsSettings = project.service<JsPsaSettings>()
                val providers = jsSettings.methodArgumentProviders

                if (!settings.pluginEnabled ||
                    !jsSettings.enabled ||
                    !jsSettings.methodArgumentProvidersInspectionsEnabled ||
                    providers.isNullOrEmpty()
                ) {
                    return
                }

                val provider = JsMethodArgumentHelper.findProviderForMethodNamePosition(element, providers) ?: return
                val methodName = element.stringValue ?: return
                if (methodName.isEmpty()) return

                val names = JsMethodArgumentHelper.methodNames(project, provider.`class`)
                if (names.isEmpty() || names.contains(methodName)) return

                holder.registerProblem(
                    element,
                    "Method '$methodName' not found in '${provider.`class`}'",
                    ProblemHighlightType.GENERIC_ERROR_OR_WARNING,
                )
            }
        }
}
