package com.github.sam0delkin.intellijpsa.language.php.searchQueryExecutor

import com.github.sam0delkin.intellijpsa.util.PsiUtils
import com.intellij.openapi.application.ReadAction
import com.intellij.psi.PsiElement
import com.intellij.psi.PsiReference
import com.intellij.psi.search.searches.ReferencesSearch
import com.intellij.util.ArrayUtil
import com.intellij.util.Processor
import com.intellij.util.QueryExecutor
import com.jetbrains.php.lang.psi.elements.FunctionReference
import com.jetbrains.php.lang.psi.elements.Method
import com.jetbrains.php.lang.psi.elements.MethodReference
import com.jetbrains.php.lang.psi.elements.Parameter
import com.jetbrains.php.lang.psi.elements.ParameterList
import com.jetbrains.php.lang.psi.elements.PhpNamedElement
import com.jetbrains.php.lang.psi.elements.impl.FunctionImpl

class ParameterUsageSearcher : QueryExecutor<PsiReference, ReferencesSearch.SearchParameters> {
    override fun execute(
        params: ReferencesSearch.SearchParameters,
        consumer: Processor<in PsiReference>,
    ): Boolean {
        val target = params.elementToSearch
        if (target !is Parameter) return true

        ReadAction.run<RuntimeException> {
            val parameterList = target.parent as? ParameterList ?: return@run
            val parameters = parameterList.parameters

            val parameterIndex = ArrayUtil.indexOf(parameters, target)
            val parameterName = target.name

            val function = getFunction(target) ?: return@run

            ReferencesSearch.search(function, params.scopeDeterminedByUser).forEach { reference ->

                val element = reference.element

                when (element) {
                    is FunctionReference -> {
                        if (isArgumentUsed(element.parameters, parameterIndex, parameterName)) {
                            consumer.process(reference)
                        }
                    }

                    is MethodReference -> {
                        if (isArgumentUsed(element.parameters, parameterIndex, parameterName)) {
                            consumer.process(reference)
                        }
                    }
                }

                true
            }
        }

        return true
    }

    private fun getFunction(parameter: Parameter): PhpNamedElement? {
        val parent = parameter.parent?.parent

        return when (parent) {
            is FunctionImpl -> parent
            is Method -> parent
            else -> null
        } as PhpNamedElement?
    }

    private fun isArgumentUsed(
        arguments: Array<PsiElement>,
        index: Int,
        parameterName: String?,
    ): Boolean {
        if (parameterName != null) {
            for (arg in arguments) {
                val prevColon = PsiUtils.getPrevElementByType(arg, "colon")
                val prevElementByType = PsiUtils.getPrevElementByType(prevColon, "identifier")
                if (
                    null !== prevElementByType &&
                    prevElementByType.text == parameterName
                ) {
                    return true
                }
            }
        }

        return arguments.size > index
    }
}
