package com.github.sam0delkin.intellijpsa.language.javascript.methodArguments

import com.github.sam0delkin.intellijpsa.language.javascript.settings.JsPsaSettings
import com.github.sam0delkin.intellijpsa.settings.Settings
import com.intellij.lang.javascript.JSTokenTypes
import com.intellij.lang.javascript.psi.JSArgumentList
import com.intellij.lang.javascript.psi.JSCallExpression
import com.intellij.lang.javascript.psi.JSExpression
import com.intellij.lang.javascript.psi.JSParameterListElement
import com.intellij.lang.parameterInfo.CreateParameterInfoContext
import com.intellij.lang.parameterInfo.ParameterInfoHandlerWithTabActionSupport
import com.intellij.lang.parameterInfo.ParameterInfoUIContext
import com.intellij.lang.parameterInfo.UpdateParameterInfoContext
import com.intellij.openapi.components.service
import com.intellij.psi.PsiElement
import com.intellij.psi.tree.IElementType
import com.intellij.psi.util.PsiTreeUtil

class JsParameterInfoHandler : ParameterInfoHandlerWithTabActionSupport<JSArgumentList, Array<JSParameterListElement>, JSExpression> {
    override fun getArgumentListClass(): Class<JSArgumentList> = JSArgumentList::class.java

    override fun getActualParameters(o: JSArgumentList): Array<JSExpression> = o.arguments

    override fun getActualParameterDelimiterType(): IElementType = JSTokenTypes.COMMA

    override fun getActualParametersRBraceType(): IElementType = JSTokenTypes.RPAR

    override fun getArgumentListAllowedParentClasses(): Set<Class<*>> = setOf(JSCallExpression::class.java)

    override fun getArgListStopSearchClasses(): Set<Class<*>> = emptySet()

    override fun findElementForParameterInfo(context: CreateParameterInfoContext): JSArgumentList? {
        val argumentList = findArgumentList(context.file.findElementAt(context.offset)) ?: return null
        val match = findMatch(argumentList) ?: return null
        context.itemsToShow = arrayOf(match.function.parameters)
        return argumentList
    }

    override fun showParameterInfo(
        element: JSArgumentList,
        context: CreateParameterInfoContext,
    ) {
        context.showHint(element, element.textRange.startOffset, this)
    }

    override fun findElementForUpdatingParameterInfo(context: UpdateParameterInfoContext): JSArgumentList? =
        findArgumentList(context.file.findElementAt(context.offset))

    override fun updateParameterInfo(
        element: JSArgumentList,
        context: UpdateParameterInfoContext,
    ) {
        context.setCurrentParameter(currentParameterIndex(element, context.offset))
    }

    override fun updateUI(
        parameters: Array<JSParameterListElement>?,
        context: ParameterInfoUIContext,
    ) {
        if (parameters == null || parameters.isEmpty()) {
            context.isUIComponentEnabled = false
            return
        }

        val current = context.currentParameterIndex
        val sb = StringBuilder()
        var highlightStart = -1
        var highlightEnd = -1

        parameters.forEachIndexed { i, parameter ->
            if (i > 0) sb.append(", ")
            val start = sb.length
            sb.append(renderParameter(parameter))
            if (i == current) {
                highlightStart = start
                highlightEnd = sb.length
            }
        }

        context.setupUIComponentPresentation(
            sb.toString(),
            highlightStart,
            highlightEnd,
            false,
            false,
            false,
            context.defaultParameterColor,
        )
    }

    private fun renderParameter(parameter: JSParameterListElement): String {
        val builder = StringBuilder()
        if (parameter.isRest) builder.append("...")
        builder.append(parameter.name ?: "")
        if (parameter.isOptional) builder.append("?")
        parameter.typeElement?.text?.let { builder.append(": $it") }
        parameter.initializer?.text?.let { builder.append(" = $it") }
        return builder.toString()
    }

    private fun findArgumentList(leaf: PsiElement?): JSArgumentList? {
        val argumentList = PsiTreeUtil.getParentOfType(leaf, JSArgumentList::class.java) ?: return null
        return if (findMatch(argumentList) != null) argumentList else null
    }

    private fun findMatch(argumentList: JSArgumentList): JsMethodArgumentHelper.ProviderMatch? {
        val call = argumentList.parent as? JSCallExpression ?: return null
        val project = argumentList.project
        val settings = project.service<Settings>()
        val jsSettings = project.service<JsPsaSettings>()
        val providers = jsSettings.methodArgumentProviders
        if (!settings.pluginEnabled || !jsSettings.enabled || providers.isNullOrEmpty()) return null
        return JsMethodArgumentHelper.findProviderForCall(call, providers)
    }

    private fun currentParameterIndex(
        argumentList: JSArgumentList,
        offset: Int,
    ): Int {
        val arguments = argumentList.arguments
        var callArgIndex = arguments.size
        for ((i, argument) in arguments.withIndex()) {
            if (offset <= argument.textRange.endOffset) {
                callArgIndex = i
                break
            }
        }
        val match = findMatch(argumentList) ?: return callArgIndex
        return callArgIndex - match.provider.argumentsOffset
    }
}
