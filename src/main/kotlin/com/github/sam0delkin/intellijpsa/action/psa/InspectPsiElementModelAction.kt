package com.github.sam0delkin.intellijpsa.action.psa

import com.github.sam0delkin.intellijpsa.icons.Icons
import com.github.sam0delkin.intellijpsa.language.velocity.PsaCompletionContributor
import com.github.sam0delkin.intellijpsa.ui.components.Utils
import com.intellij.icons.AllIcons
import com.intellij.ide.plugins.PluginManager
import com.intellij.lang.Language
import com.intellij.openapi.actionSystem.AnAction
import com.intellij.openapi.actionSystem.AnActionEvent
import com.intellij.openapi.actionSystem.impl.ActionButton
import com.intellij.openapi.application.runWriteAction
import com.intellij.openapi.editor.Editor
import com.intellij.openapi.editor.EditorFactory
import com.intellij.openapi.extensions.PluginId
import com.intellij.openapi.fileEditor.FileDocumentManager
import com.intellij.openapi.fileEditor.FileEditorManager
import com.intellij.openapi.fileTypes.FileTypes
import com.intellij.openapi.fileTypes.PlainTextLanguage
import com.intellij.openapi.project.DumbAwareAction
import com.intellij.openapi.ui.DialogBuilder
import com.intellij.openapi.util.text.StringUtil
import com.intellij.psi.PsiManager
import com.intellij.ui.EditorTextField
import com.intellij.ui.HorizontalScrollBarEditorCustomization
import com.intellij.ui.LanguageTextField
import com.intellij.ui.dsl.builder.Cell
import com.intellij.ui.dsl.builder.panel
import org.apache.velocity.VelocityContext
import org.apache.velocity.app.Velocity
import java.awt.Dimension
import java.awt.Point
import java.io.StringWriter

class InspectPsiElementModelAction :
    AnAction(
        "Inspect PSI Element Mode",
        "Inspect PSI element Model",
        Icons.PluginIcon,
    ) {
    override fun actionPerformed(e: AnActionEvent) {
        val editor: Editor = FileEditorManager.getInstance(e.project!!).selectedTextEditor ?: return
        val file = FileDocumentManager.getInstance().getFile(editor.document)
        val offset: Int = editor.caretModel.offset

        val element = PsiManager.getInstance(e.project!!).findFile(file!!)!!.findElementAt(offset) ?: return
        PsaCompletionContributor.currentElement = element

        val velocityPlugin = PluginManager.getPlugins().find { it.pluginId == PluginId.getId("com.intellij.velocity") }
        val velocityPluginEnabled = null != velocityPlugin && velocityPlugin.isEnabled
        var language: Language = PlainTextLanguage.INSTANCE

        if (velocityPluginEnabled) {
            language = Language.findLanguageByID("VTL") ?: PlainTextLanguage.INSTANCE
        }
        var templateCodeField: Cell<EditorTextField>?
        var textAreaField: Cell<EditorTextField>? = null
        var actionButton: Cell<ActionButton>? = null
        val dialogBuilder =
            DialogBuilder().centerPanel(
                panel {
                    var rowComment = "You have a variable '\$element' which contains current PSI element."
                    if (!velocityPluginEnabled) {
                        rowComment +=
                            "<br />You can install " +
                            "<a href=\"https://plugins.jetbrains.com/plugin/21738-apache-velocity\">Apache Velocity</a> plugin " +
                            "to support additional autocomplete features.\nYou will need to restart IDE to make changes take effect."
                    }
                    row("Template code") {
                        val previewTextFieldComponent =
                            LanguageTextField(
                                language,
                                e.project,
                                "\$element",
                                false,
                            )
                        previewTextFieldComponent.addSettingsProvider { editor ->
                            run {
                                editor.settings.isLineNumbersShown = true
                                editor.setVerticalScrollbarVisible(true)
                                HorizontalScrollBarEditorCustomization.ENABLED.customize(editor)
                            }
                        }
                        previewTextFieldComponent.preferredSize = Dimension(800, 200)
                        previewTextFieldComponent.minimumSize = Dimension(800, 200)
                        previewTextFieldComponent.maximumSize = Dimension(-1, 200)
                        templateCodeField = cell(previewTextFieldComponent)
                        val action =
                            object : DumbAwareAction(
                                "Update Data",
                                "",
                                AllIcons.General.InlineRefresh,
                            ) {
                                override fun actionPerformed(e: AnActionEvent) {
                                    val writer = StringWriter()
                                    val context = VelocityContext()
                                    context.put("element", element)

                                    try {
                                        Velocity.evaluate(context, writer, "", templateCodeField!!.component.document.text)
                                    } catch (e: Exception) {
                                        val tooltip =
                                            com.intellij.ui
                                                .GotItTooltip(
                                                    "PSA",
                                                    "Error: " + e.message,
                                                ).withIcon(AllIcons.General.BalloonError)
                                                .withButtonLabel("OK")
                                        tooltip.createAndShow(actionButton!!.component) { c, _ -> Point(c.width, c.height / 2) }

                                        return
                                    }

                                    val result = writer.buffer.toString()
                                    runWriteAction {
                                        textAreaField!!.component.document.setText(result)
                                    }
                                }
                            }
                        actionButton = Utils.actionButton(action, "bottom", this)
                    }.rowComment(rowComment)
                    row("Result") {
                        val component =
                            EditorTextField(
                                EditorFactory.getInstance().createDocument(
                                    StringUtil.convertLineSeparators(""),
                                ),
                                e.project,
                                FileTypes.PLAIN_TEXT,
                                true,
                                false,
                            )
                        component.addSettingsProvider { editor ->
                            run {
                                editor.settings.isLineNumbersShown = true
                                editor.setVerticalScrollbarVisible(true)
                                HorizontalScrollBarEditorCustomization.ENABLED.customize(editor)
                            }
                        }
                        component.preferredSize = Dimension(800, 200)
                        component.minimumSize = Dimension(800, 200)
                        component.maximumSize = Dimension(-1, 200)
                        textAreaField = cell(component)
                    }
                },
            )

        dialogBuilder.show()
        PsaCompletionContributor.currentElement = null
    }
}
