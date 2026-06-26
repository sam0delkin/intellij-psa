package com.github.sam0delkin.intellijpsa.language.php.methodArguments

import com.github.sam0delkin.intellijpsa.language.php.settings.PhpPsaSettings
import com.github.sam0delkin.intellijpsa.settings.Settings
import com.intellij.codeInsight.hints.ChangeListener
import com.intellij.codeInsight.hints.FactoryInlayHintsCollector
import com.intellij.codeInsight.hints.ImmediateConfigurable
import com.intellij.codeInsight.hints.InlayHintsCollector
import com.intellij.codeInsight.hints.InlayHintsProvider
import com.intellij.codeInsight.hints.InlayHintsSink
import com.intellij.codeInsight.hints.NoSettings
import com.intellij.codeInsight.hints.SettingsKey
import com.intellij.openapi.components.service
import com.intellij.openapi.editor.Editor
import com.intellij.psi.PsiElement
import com.intellij.psi.PsiFile
import com.jetbrains.php.lang.psi.elements.ArrayCreationExpression
import javax.swing.JComponent
import javax.swing.JPanel

@Suppress("UnstableApiUsage")
class PsaPhpMethodArgumentInlayHintsProvider : InlayHintsProvider<NoSettings> {
    override val key: SettingsKey<NoSettings> = SettingsKey("psa.php.method.argument.hints")

    override val name: String = "PSA dynamic method arguments"

    override val previewText: String =
        "new ServiceMethodMessage([Manager::class, 'doWork'], [\$account, true]);"

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
        val phpSettings = project.service<PhpPsaSettings>()
        val providers = phpSettings.methodArgumentProviders

        if (!psaSettings.pluginEnabled || !phpSettings.enabled || providers.isNullOrEmpty()) return null

        return object : FactoryInlayHintsCollector(editor) {
            override fun collect(
                element: PsiElement,
                editor: Editor,
                sink: InlayHintsSink,
            ): Boolean {
                if (element !is ArrayCreationExpression) return true

                val match =
                    PsaPhpMethodArgumentHelper.findProviderForArgumentsArray(element, providers)
                        ?: return true

                for (mapping in PsaPhpMethodArgumentHelper.mapArgumentsToParameters(element, match)) {
                    val label = if (mapping.parameter.isVariadic) "...${mapping.parameter.name}:" else "${mapping.parameter.name}:"
                    val presentation =
                        factory.roundWithBackgroundAndSmallInset(factory.smallText(label))
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
