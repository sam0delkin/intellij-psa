package com.github.sam0delkin.intellijpsa.language.php.methodArguments

import com.github.sam0delkin.intellijpsa.language.php.settings.PhpPsaSettings
import com.github.sam0delkin.intellijpsa.settings.Settings
import com.intellij.openapi.application.QueryExecutorBase
import com.intellij.openapi.components.service
import com.intellij.psi.PsiReference
import com.intellij.psi.search.GlobalSearchScope
import com.intellij.psi.search.PsiSearchHelper
import com.intellij.psi.search.searches.ReferencesSearch
import com.intellij.psi.util.PsiTreeUtil
import com.intellij.util.Processor
import com.jetbrains.php.lang.psi.elements.Method
import com.jetbrains.php.lang.psi.elements.Parameter
import com.jetbrains.php.lang.psi.elements.ParameterList
import com.jetbrains.php.lang.psi.elements.StringLiteralExpression

class PsaPhpDynamicParameterUsageSearcher : QueryExecutorBase<PsiReference, ReferencesSearch.SearchParameters>(true) {
    override fun processQuery(
        params: ReferencesSearch.SearchParameters,
        consumer: Processor<in PsiReference>,
    ) {
        val parameter = params.elementToSearch as? Parameter ?: return
        val project = parameter.project
        val settings = project.service<Settings>()
        val phpSettings = project.service<PhpPsaSettings>()
        val providers = phpSettings.methodArgumentProviders
        if (!settings.pluginEnabled || !phpSettings.enabled || providers.isNullOrEmpty()) return

        val parameterList = parameter.parent as? ParameterList ?: return
        val method = parameterList.parent as? Method ?: return
        val parameterIndex = parameterList.parameters.indexOf(parameter)
        if (parameterIndex < 0) return

        val methodName = method.name
        val scope = GlobalSearchScope.projectScope(project)

        PsiSearchHelper
            .getInstance(project)
            .processAllFilesWithWordInLiterals(methodName, scope) { file ->
                PsiTreeUtil
                    .findChildrenOfType(file, StringLiteralExpression::class.java)
                    .filter { it.contents == methodName }
                    .forEach { stringLiteral ->
                        val match =
                            PsaPhpMethodArgumentHelper.findProviderForMethodNameElement(
                                stringLiteral,
                                providers,
                            ) ?: return@forEach
                        if (!method.manager.areElementsEquivalent(match.method, method)) return@forEach

                        val argElement =
                            PsaPhpMethodArgumentHelper.findArgumentForParameter(
                                stringLiteral,
                                match.provider,
                                parameterIndex,
                            ) ?: return@forEach

                        consumer.process(PsaPhpDynamicArgumentReference(argElement, parameter))
                    }
                true
            }
    }
}
