package com.github.sam0delkin.intellijpsa.language.php.methodArguments

import com.github.sam0delkin.intellijpsa.language.php.settings.PhpPsaSettings
import com.github.sam0delkin.intellijpsa.settings.Settings
import com.intellij.lang.parameterInfo.CreateParameterInfoContext
import com.intellij.lang.parameterInfo.ParameterInfoHandlerWithTabActionSupport
import com.intellij.lang.parameterInfo.ParameterInfoUIContext
import com.intellij.lang.parameterInfo.UpdateParameterInfoContext
import com.intellij.openapi.components.service
import com.intellij.psi.PsiElement
import com.intellij.psi.PsiWhiteSpace
import com.intellij.psi.tree.IElementType
import com.intellij.psi.util.PsiTreeUtil
import com.jetbrains.php.lang.lexer.PhpTokenTypes
import com.jetbrains.php.lang.psi.elements.ArrayCreationExpression
import com.jetbrains.php.lang.psi.elements.Parameter
import com.jetbrains.php.lang.psi.elements.ParameterList

class PsaPhpParameterInfoHandler : ParameterInfoHandlerWithTabActionSupport<ArrayCreationExpression, Array<Parameter>, PsiElement> {
    override fun getArgumentListClass(): Class<ArrayCreationExpression> = ArrayCreationExpression::class.java

    override fun getActualParameters(o: ArrayCreationExpression): Array<PsiElement> = arrayValues(o).toTypedArray()

    override fun getActualParameterDelimiterType(): IElementType = PhpTokenTypes.opCOMMA

    override fun getActualParametersRBraceType(): IElementType = PhpTokenTypes.chRBRACKET

    override fun getArgumentListAllowedParentClasses(): Set<Class<*>> = setOf(ParameterList::class.java)

    override fun getArgListStopSearchClasses(): Set<Class<*>> = emptySet()

    override fun findElementForParameterInfo(context: CreateParameterInfoContext): ArrayCreationExpression? {
        val array = findMatchingArray(context.file.findElementAt(context.offset)) ?: return null
        val match = findMatch(array) ?: return null
        context.itemsToShow = arrayOf(match.method.parameters)
        return array
    }

    override fun showParameterInfo(
        element: ArrayCreationExpression,
        context: CreateParameterInfoContext,
    ) {
        context.showHint(element, element.textOffset, this)
    }

    override fun findElementForUpdatingParameterInfo(context: UpdateParameterInfoContext): ArrayCreationExpression? =
        findMatchingArray(context.file.findElementAt(context.offset))

    override fun updateParameterInfo(
        element: ArrayCreationExpression,
        context: UpdateParameterInfoContext,
    ) {
        context.setCurrentParameter(currentArgIndex(element, context.offset))
    }

    override fun updateUI(
        parameters: Array<Parameter>?,
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

        parameters.forEachIndexed { i, param ->
            if (i > 0) sb.append(", ")
            val start = sb.length
            sb.append(param.type.toString())
            sb.append(" $")
            sb.append(param.name)
            if (param.defaultValue != null) sb.append(" = ${param.defaultValue!!.text}")
            if (i == current) {
                highlightStart = start
                highlightEnd = sb.length
            }
        }

        val text =
            sb
                .toString()
                .replace(Regex("([^\\\\a-zA-Z0-9])#[A-Z]#"), "$1") // Handle cases like Э#A#
                .replace(Regex("^#[A-Z]#"), "") // Handle cases like #K# at start
                .replace(Regex("(#|\\?|\\|)?#[A-Z]#"), "$1") // Handle cases like |#K# or ?#K# or ##K#
                .replace(Regex("\\\\+"), "\\\\")
                .replace("|", " | ")

        context.setupUIComponentPresentation(
            text,
            highlightStart,
            highlightEnd,
            false,
            false,
            false,
            context.defaultParameterColor,
        )
    }

    private fun findMatchingArray(leaf: PsiElement?): ArrayCreationExpression? {
        val array = PsiTreeUtil.getParentOfType(leaf, ArrayCreationExpression::class.java) ?: return null
        return if (findMatch(array) != null) array else null
    }

    private fun findMatch(array: ArrayCreationExpression): PsaPhpMethodArgumentHelper.ProviderMatch? {
        val settings = array.project.service<Settings>()
        val phpSettings = array.project.service<PhpPsaSettings>()
        val providers = phpSettings.methodArgumentProviders
        if (!settings.pluginEnabled || !phpSettings.enabled || providers.isNullOrEmpty()) return null
        return PsaPhpMethodArgumentHelper.findProviderForArgumentsArray(array, providers)
    }

    private fun currentArgIndex(
        array: ArrayCreationExpression,
        offset: Int,
    ): Int {
        val values = arrayValues(array)
        var index = 0
        for ((i, value) in values.withIndex()) {
            if (offset <= value.textRange.endOffset) {
                index = i
                break
            }
            index = i + 1
        }
        val match = findMatch(array) ?: return index
        return index + match.provider.argumentsOffset
    }

    private fun arrayValues(array: ArrayCreationExpression): List<PsiElement> =
        array.children.filter {
            it !is PsiWhiteSpace &&
                it.node.elementType != PhpTokenTypes.opCOMMA &&
                it.node.elementType != PhpTokenTypes.chLBRACKET &&
                it.node.elementType != PhpTokenTypes.chRBRACKET &&
                it.text.isNotBlank()
        }
}
