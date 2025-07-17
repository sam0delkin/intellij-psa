package com.github.sam0delkin.intellijpsa.language.velocity

import com.github.sam0delkin.intellijpsa.icons.Icons
import com.github.sam0delkin.intellijpsa.model.completion.CompletionModel
import com.github.sam0delkin.intellijpsa.model.psi.PsiElementModel
import com.github.sam0delkin.intellijpsa.services.PsaManager
import com.intellij.codeInsight.completion.CompletionContributor
import com.intellij.codeInsight.completion.CompletionParameters
import com.intellij.codeInsight.completion.CompletionResultSet
import com.intellij.codeInsight.completion.CompletionUtil
import com.intellij.codeInsight.lookup.LookupElementBuilder
import com.intellij.injected.editor.VirtualFileWindow
import com.intellij.openapi.components.service
import com.intellij.openapi.project.guessProjectDir
import com.intellij.psi.PsiElement
import com.intellij.psi.util.elementType
import com.intellij.velocity.psi.VtlIndexExpression
import com.intellij.velocity.psi.VtlMethodCallExpression
import com.intellij.velocity.psi.VtlTokenType
import kotlin.reflect.full.memberProperties

class PsaCompletionContributor : CompletionContributor() {
    companion object {
        var currentElement: PsiElement? = null
    }

    override fun fillCompletionVariants(
        parameters: CompletionParameters,
        result: CompletionResultSet,
    ) {
        val project = parameters.position.project
        val psaManager = project.service<PsaManager>()
        val settings = psaManager.getSettings()

        if (!settings.pluginEnabled || null === settings.scriptPath) {
            return
        }

        var scriptDir = settings.getScriptDir()
        if (null === scriptDir) {
            return
        }
        val projectDir = project.guessProjectDir()
        if (null === projectDir) {
            return
        }

        scriptDir = projectDir.path + '/' + scriptDir

        val originalFile = parameters.originalFile.virtualFile

        if (
            originalFile !is VirtualFileWindow &&
            null == currentElement
        ) {
            return
        }

        if (
            originalFile is VirtualFileWindow &&
            originalFile.delegate.path.indexOf(scriptDir) < 0
        ) {
            return
        }

        val position = parameters.position
        if (position.elementType !is VtlTokenType) {
            return
        }

        if (position.prevSibling?.prevSibling?.text == "completion") {
            CompletionModel::class.memberProperties.map {
                result.addElement(
                    LookupElementBuilder
                        .create(it.name)
                        .withIcon(Icons.PluginIcon)
                        .withTypeText(it.returnType.toString()),
                )
            }
        }

        if (position.prevSibling?.prevSibling?.text == "model") {
            PsiElementModel::class.memberProperties.map {
                result.addElement(
                    LookupElementBuilder
                        .create(it.name)
                        .withIcon(Icons.PluginIcon)
                        .withTypeText(it.returnType.toString()),
                )
            }
        }

        if (position.prevSibling?.prevSibling?.text == "element" && null != currentElement) {
            currentElement!!.javaClass.methods.map {
                if (it.parameterCount > 0) {
                    return@map
                }

                result.addElement(
                    LookupElementBuilder
                        .create(it.name + "()")
                        .withIcon(Icons.PluginIcon)
                        .withTypeText(it.returnType.toString())
                        .withPresentableText(it.name),
                )
            }
        }

        if (
            (
                position.prevSibling?.prevSibling is VtlMethodCallExpression ||
                    position.prevSibling?.prevSibling is VtlIndexExpression
            ) &&
            null != currentElement
        ) {
            val text =
                position.prevSibling.prevSibling.text
                    .replace("." + CompletionUtil.DUMMY_IDENTIFIER_TRIMMED, "")
            val parts = text.split(".")

            if (parts.size > 1) {
                var element: Any? = currentElement
                var error = false
                for (i in 1 until parts.size) {
                    try {
                        var part = parts[i].replace("()", "")
                        val regex = Regex("(.*)\\[([\\d+])\\]")
                        var index: Int? = null
                        if (regex.matches(part)) {
                            val matchResult = regex.find(part, 0)
                            part = matchResult!!.groupValues[1]
                            index = matchResult.groupValues[2].toInt()
                        }
                        val method = element?.javaClass?.methods?.find { it.name == part }
                        if (null == method) {
                            error = true

                            break
                        }
                        element = method.invoke(element)
                        if (null != index && element is Iterable<*>) {
                            element = element.toList()[index]
                        }
                    } catch (_: Throwable) {
                        error = true

                        break
                    }
                }

                if (!error && null != element) {
                    element.javaClass.methods.map {
                        if (it.parameterCount > 0) {
                            return@map
                        }

                        result.addElement(
                            LookupElementBuilder
                                .create(it.name + "()")
                                .withIcon(Icons.PluginIcon)
                                .withTypeText(it.returnType.toString())
                                .withPresentableText(it.name),
                        )
                    }
                }
            }
        }
    }
}
