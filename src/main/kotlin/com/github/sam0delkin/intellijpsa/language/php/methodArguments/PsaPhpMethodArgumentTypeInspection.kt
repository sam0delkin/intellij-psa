package com.github.sam0delkin.intellijpsa.language.php.methodArguments

import com.github.sam0delkin.intellijpsa.language.php.settings.PhpPsaSettings
import com.github.sam0delkin.intellijpsa.settings.Settings
import com.intellij.codeInspection.LocalInspectionTool
import com.intellij.codeInspection.LocalQuickFix
import com.intellij.codeInspection.ProblemHighlightType
import com.intellij.codeInspection.ProblemsHolder
import com.intellij.openapi.components.service
import com.intellij.psi.PsiElement
import com.intellij.psi.PsiElementVisitor
import com.intellij.psi.PsiFile
import com.intellij.psi.util.PsiTreeUtil
import com.jetbrains.php.PhpBundle
import com.jetbrains.php.PhpIndex
import com.jetbrains.php.codeInsight.PhpCodeInsightUtil
import com.jetbrains.php.lang.documentation.PhpDocumentationProvider
import com.jetbrains.php.lang.inspections.type.PhpParamsInspection
import com.jetbrains.php.lang.inspections.type.PhpStrictTypeCheckingInspection
import com.jetbrains.php.lang.psi.PhpFile
import com.jetbrains.php.lang.psi.elements.ArrayCreationExpression
import com.jetbrains.php.lang.psi.elements.Method
import com.jetbrains.php.lang.psi.elements.PhpExpression
import com.jetbrains.php.lang.psi.elements.PhpTypedElement

class PsaPhpMethodArgumentTypeInspection : LocalInspectionTool() {
    override fun buildVisitor(
        holder: ProblemsHolder,
        isOnTheFly: Boolean,
    ): PsiElementVisitor =
        object : PsiElementVisitor() {
            override fun visitElement(element: PsiElement) {
                if (element is ArrayCreationExpression) {
                    check(element, holder, isOnTheFly)
                }
            }
        }

    private fun check(
        array: ArrayCreationExpression,
        holder: ProblemsHolder,
        onTheFly: Boolean,
    ) {
        val project = array.project
        val settings = project.service<Settings>()
        val phpSettings = project.service<PhpPsaSettings>()
        val providers = phpSettings.methodArgumentProviders

        if (!settings.pluginEnabled || !phpSettings.enabled || providers.isNullOrEmpty()) return

        val match = PsaPhpMethodArgumentHelper.findProviderForArgumentsArray(array, providers) ?: return

        val targetStrict = isStrictTypes(match.method.containingFile)
        val highlightType = if (targetStrict) ProblemHighlightType.GENERIC_ERROR else ProblemHighlightType.WARNING

        for (mapping in PsaPhpMethodArgumentHelper.mapArgumentsToParameters(array, match)) {
            val declared =
                runCatchingSilently { PhpStrictTypeCheckingInspection.getDeclaredType(mapping.parameter) }
                    ?: mapping.parameter.declaredType
            if (declared.isEmpty) continue

            val typed =
                mapping.value as? PhpTypedElement
                    ?: PsiTreeUtil.findChildOfType(mapping.value, PhpExpression::class.java)
                    ?: continue
            val actual = typed.type.global(project)
            if (actual.filterUnknown().isEmpty) continue

            val compatible =
                runCatchingSilently {
                    PhpStrictTypeCheckingInspection.isTypeCompatible(declared, actual)
                } ?: true
            if (compatible) continue

            val baseMessage =
                runCatchingSilently {
                    PhpBundle.message("inspection.strict.type.checking.parameter", declared.toString(), actual.toString())
                } ?: "Expected parameter of type '$declared', '$actual' provided"

            val message = if (onTheFly) withMethodSignature(baseMessage, match.method, typed) else baseMessage

            val fixes =
                runCatchingSilently { PhpParamsInspection.getFixes(typed, actual, declared) }
                    ?: LocalQuickFix.EMPTY_ARRAY

            holder.registerProblem(typed, message, highlightType, *fixes)
        }
    }

    private fun isStrictTypes(file: PsiFile?): Boolean =
        (file as? PhpFile)?.let { runCatchingSilently { PhpCodeInsightUtil.isStrictTypes(it) } } ?: false

    private fun withMethodSignature(
        message: String,
        method: Method,
        context: PsiElement,
    ): String {
        val info = runCatchingSilently { PhpDocumentationProvider().getQuickNavigateInfo(method, context) }
        if (info.isNullOrBlank()) return message
        return "<html><body>$message<hr/>$info</body></html>"
    }

    private inline fun <T> runCatchingSilently(block: () -> T): T? =
        try {
            block()
        } catch (_: Throwable) {
            null
        }
}
