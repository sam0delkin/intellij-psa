package com.github.sam0delkin.intellijpsa.language.javascript.methodArguments

import com.github.sam0delkin.intellijpsa.language.javascript.settings.JsPsaSettings
import com.github.sam0delkin.intellijpsa.settings.Settings
import com.intellij.codeInsight.hints.ChangeListener
import com.intellij.codeInsight.hints.FactoryInlayHintsCollector
import com.intellij.codeInsight.hints.ImmediateConfigurable
import com.intellij.codeInsight.hints.InlayHintsCollector
import com.intellij.codeInsight.hints.InlayHintsProvider
import com.intellij.codeInsight.hints.InlayHintsSink
import com.intellij.codeInsight.hints.NoSettings
import com.intellij.codeInsight.hints.SettingsKey
import com.intellij.lang.javascript.psi.JSCallExpression
import com.intellij.openapi.components.service
import com.intellij.openapi.editor.Editor
import com.intellij.psi.PsiElement
import com.intellij.psi.PsiFile
import javax.swing.JComponent
import javax.swing.JPanel

@Suppress("UnstableApiUsage")
class JsMethodArgumentInlayHintsProvider : InlayHintsProvider<NoSettings> {
    override val key: SettingsKey<NoSettings> = SettingsKey("psa.js.method.argument.hints")

    override val name: String = "PSA dynamic method arguments"

    override val previewText: String = "\$el.somePlugin('someMethod', someArg);"

    override fun createSettings(): NoSettings = NoSettings()

    override fun createConfigurable(settings: NoSettings): ImmediateConfigurable =
        object : ImmediateConfigurable {
            override fun createComponent(listener: ChangeListener): JComponent = JPanel()
        }

    override fun getCollectorFor(
        file: PsiFile,
        editor: Editor,
        settings: NoSettings,
        sink: InlayHintsSink,
    ): InlayHintsCollector? {
        val project = file.project
        val psaSettings = project.service<Settings>()
        val jsSettings = project.service<JsPsaSettings>()
        val providers = jsSettings.methodArgumentProviders

        if (!psaSettings.pluginEnabled || !jsSettings.enabled || providers.isNullOrEmpty()) return null

        return object : FactoryInlayHintsCollector(editor) {
            override fun collect(
                element: PsiElement,
                editor: Editor,
                sink: InlayHintsSink,
            ): Boolean {
                if (element !is JSCallExpression) return true

                val match = JsMethodArgumentHelper.findProviderForCall(element, providers) ?: return true

                for (mapping in JsMethodArgumentHelper.mapArgumentsToParameters(element, match)) {
                    val name = mapping.parameter.name ?: continue
                    val label = if (mapping.parameter.isRest) "...$name:" else "$name:"
                    val presentation = factory.roundWithBackgroundAndSmallInset(factory.smallText(label))
                    sink.addInlineElement(
                        mapping.value.textRange.startOffset,
                        false,
                        presentation,
                        false,
                    )
                }

                return true
            }
        }
    }
}
