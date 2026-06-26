package com.github.sam0delkin.intellijpsa.language.php.methodArguments

import com.github.sam0delkin.intellijpsa.language.php.model.MethodArgumentProviderModel
import com.intellij.psi.PsiElement
import com.intellij.psi.PsiWhiteSpace
import com.intellij.psi.util.PsiTreeUtil
import com.jetbrains.php.PhpIndex
import com.jetbrains.php.lang.psi.elements.ArrayCreationExpression
import com.jetbrains.php.lang.psi.elements.ClassConstantReference
import com.jetbrains.php.lang.psi.elements.ClassReference
import com.jetbrains.php.lang.psi.elements.ConstantReference
import com.jetbrains.php.lang.psi.elements.Function
import com.jetbrains.php.lang.psi.elements.Method
import com.jetbrains.php.lang.psi.elements.MethodReference
import com.jetbrains.php.lang.psi.elements.NewExpression
import com.jetbrains.php.lang.psi.elements.Parameter
import com.jetbrains.php.lang.psi.elements.ParameterList
import com.jetbrains.php.lang.psi.elements.PhpClass
import com.jetbrains.php.lang.psi.elements.StringLiteralExpression

object PsaPhpMethodArgumentHelper {
    data class ProviderMatch(
        val provider: MethodArgumentProviderModel,
        val method: Method,
    )

    data class ArgumentMapping(
        val value: PsiElement,
        val parameter: Parameter,
        val parameterIndex: Int,
    )

    fun mapArgumentsToParameters(
        array: ArrayCreationExpression,
        match: ProviderMatch,
    ): List<ArgumentMapping> {
        val values = array.arrayValues()
        val params = match.method.parameters
        val offset = match.provider.argumentsOffset
        val variadic = params.lastOrNull()?.takeIf { it.isVariadic }

        val result = mutableListOf<ArgumentMapping>()
        for ((arrayIndex, value) in values.withIndex()) {
            val parameterIndex = arrayIndex + offset
            val param = params.getOrNull(parameterIndex) ?: variadic ?: continue
            val index = if (params.getOrNull(parameterIndex) != null) parameterIndex else params.lastIndex
            result.add(ArgumentMapping(value, param, index))
        }
        return result
    }

    fun findProviderForMethodNameElement(
        element: PsiElement,
        providers: List<MethodArgumentProviderModel>,
    ): ProviderMatch? {
        for (provider in providers) {
            val method = matchMethodNameElement(element, provider) ?: continue
            return ProviderMatch(provider, method)
        }
        return null
    }

    fun findProviderForArgumentsArray(
        array: ArrayCreationExpression,
        providers: List<MethodArgumentProviderModel>,
    ): ProviderMatch? {
        for (provider in providers) {
            val method = matchArgumentsArray(array, provider) ?: continue
            return ProviderMatch(provider, method)
        }
        return null
    }

    private fun matchMethodNameElement(
        element: PsiElement,
        provider: MethodArgumentProviderModel,
    ): Method? {
        if (provider.callableArgumentIndex != null) {
            val callableArray =
                PsiTreeUtil.getParentOfType(element, ArrayCreationExpression::class.java)
                    ?: return null

            getOuterCallArgs(callableArray, provider) ?: return null

            val values = callableArray.arrayValues()
            if (!psiContains(values.getOrNull(1), element)) return null

            val phpClass = resolvePhpClass(values.getOrNull(0)) ?: return null
            val methodName = extractStringContents(element) ?: return null
            return phpClass.findMethodByName(methodName)
        }

        val methodArgIdx = provider.methodArgumentIndex
        if (methodArgIdx != null) {
            val args = getOuterCallArgs(element, provider) ?: return null
            val argAtIndex = args.getOrNull(methodArgIdx) ?: return null
            if (!PsiTreeUtil.isAncestor(argAtIndex, element, false)) return null

            val classIndex = provider.classArgumentIndex ?: return null
            val phpClass = resolvePhpClass(args.getOrNull(classIndex)) ?: return null
            val methodName = extractStringContents(element) ?: return null
            return phpClass.findMethodByName(methodName)
        }

        return null
    }

    private fun matchArgumentsArray(
        array: ArrayCreationExpression,
        provider: MethodArgumentProviderModel,
    ): Method? {
        val args = getOuterCallArgs(array, provider) ?: return null
        val argAtIndex = args.getOrNull(provider.argumentsArgumentIndex) ?: return null
        if (!PsiTreeUtil.isAncestor(argAtIndex, array, false)) return null

        val callableArgIdx = provider.callableArgumentIndex
        if (callableArgIdx != null) {
            val callableWrapper = args.getOrNull(callableArgIdx) ?: return null
            val callableArg =
                callableWrapper as? ArrayCreationExpression
                    ?: PsiTreeUtil.findChildOfType(callableWrapper, ArrayCreationExpression::class.java)
                    ?: return null
            val values = callableArg.arrayValues()
            val phpClass = resolvePhpClass(values.getOrNull(0)) ?: return null
            val methodName = extractStringContents(values.getOrNull(1)) ?: return null
            return phpClass.findMethodByName(methodName)
        }

        val classArgIdx = provider.classArgumentIndex
        val methArgIdx = provider.methodArgumentIndex
        if (classArgIdx != null && methArgIdx != null) {
            val phpClass = resolvePhpClass(args.getOrNull(classArgIdx)) ?: return null
            val methodName = extractStringContents(args.getOrNull(methArgIdx)) ?: return null
            return phpClass.findMethodByName(methodName)
        }

        return null
    }

    private fun getOuterCallArgs(
        element: PsiElement,
        provider: MethodArgumentProviderModel,
    ): Array<PsiElement>? {
        val paramList = (element as? ParameterList) ?: PsiTreeUtil.getParentOfType(element, ParameterList::class.java) ?: return null
        val outerCall = paramList.parent ?: return null
        return if (outerCallMatches(outerCall, provider)) paramList.parameters else null
    }

    private fun outerCallMatches(
        element: PsiElement,
        provider: MethodArgumentProviderModel,
    ): Boolean =
        when (element) {
            is MethodReference -> {
                element.name == provider.method
            }

            is NewExpression -> {
                val fqn = element.classReference?.fqn ?: ""
                fqn == provider.`class` || fqn.endsWith("\\${provider.`class`}")
            }

            else -> {
                false
            }
        }

    private fun psiContains(
        container: PsiElement?,
        target: PsiElement,
    ): Boolean {
        container ?: return false
        return container === target || target.parent === container
    }

    private fun resolvePhpClass(element: PsiElement?): PhpClass? {
        element ?: return null
        val constantReference =
            if (element is ConstantReference) {
                element
            } else {
                PsiTreeUtil.findChildOfType(
                    element,
                    ConstantReference::class.java,
                    false,
                )
            }
        if (constantReference != null && constantReference.name == "__CLASS__") {
            return PsiTreeUtil.getParentOfType(constantReference, PhpClass::class.java)
        }
        val classConstantReference =
            if (element is ClassConstantReference) {
                element
            } else {
                PsiTreeUtil.findChildOfType(
                    element,
                    ClassConstantReference::class.java,
                    false,
                )
            }
        if (classConstantReference != null && classConstantReference.name == "class") {
            val resolved = (classConstantReference.classReference as ClassReference).resolve() as? PhpClass
            if (resolved != null) return resolved
            val className = classConstantReference.text.substringBefore("::").trim()
            if (className.isEmpty()) return null
            return PsiTreeUtil
                .findChildrenOfType(classConstantReference.containingFile, PhpClass::class.java)
                .firstOrNull { it.name == className }
        }
        val stringLiteralExpression = PsiTreeUtil.findChildOfType(element, StringLiteralExpression::class.java, false)
        if (stringLiteralExpression != null) {
            val name = stringLiteralExpression.contents
            val inFile =
                PsiTreeUtil
                    .findChildrenOfType(stringLiteralExpression.containingFile, PhpClass::class.java)
                    .firstOrNull { it.name == name }
            if (inFile != null) return inFile
            val index = PhpIndex.getInstance(stringLiteralExpression.project)
            return index.getAnyByFQN(name).firstOrNull()
                ?: index.getClassesByFQN(name).firstOrNull()
        }
        return null
    }

    fun extractStringContents(element: PsiElement?): String? {
        if (element is StringLiteralExpression) return element.contents
        val constantReference =
            if (element is ConstantReference) {
                element
            } else {
                PsiTreeUtil.findChildOfType(
                    element,
                    ConstantReference::class.java,
                    false,
                )
            }
        if (constantReference != null) {
            when (constantReference.name) {
                "__METHOD__", "__FUNCTION__" -> {
                    return PsiTreeUtil.getParentOfType(constantReference, Function::class.java)?.name
                }
            }
        }
        return element
            ?.children
            ?.filterIsInstance<StringLiteralExpression>()
            ?.firstOrNull()
            ?.contents
    }

    private fun ArrayCreationExpression.arrayValues(): List<PsiElement> =
        children.filter {
            it !is PsiWhiteSpace && it.text != "," && it.text != "[" && it.text != "]" && it.text.isNotBlank()
        }
}
