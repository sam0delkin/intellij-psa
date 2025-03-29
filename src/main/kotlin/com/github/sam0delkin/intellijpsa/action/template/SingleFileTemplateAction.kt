package com.github.sam0delkin.intellijpsa.action.template

import com.github.sam0delkin.intellijpsa.services.PsaManager
import com.github.sam0delkin.intellijpsa.settings.TemplateFormField
import com.github.sam0delkin.intellijpsa.settings.TemplateFormFieldType
import com.github.sam0delkin.intellijpsa.ui.components.JTextFieldCollection
import com.intellij.codeInsight.lookup.LookupElementBuilder
import com.intellij.ide.IdeView
import com.intellij.openapi.actionSystem.AnAction
import com.intellij.openapi.actionSystem.AnActionEvent
import com.intellij.openapi.actionSystem.DataContext
import com.intellij.openapi.actionSystem.LangDataKeys
import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.application.ex.ApplicationUtil
import com.intellij.openapi.components.service
import com.intellij.openapi.editor.EditorFactory
import com.intellij.openapi.editor.event.DocumentEvent
import com.intellij.openapi.editor.event.DocumentListener
import com.intellij.openapi.fileEditor.OpenFileDescriptor
import com.intellij.openapi.fileTypes.FileTypeManager
import com.intellij.openapi.fileTypes.FileTypes
import com.intellij.openapi.progress.EmptyProgressIndicator
import com.intellij.openapi.progress.ProcessCanceledException
import com.intellij.openapi.project.guessProjectDir
import com.intellij.openapi.ui.ComboBox
import com.intellij.openapi.ui.DialogBuilder
import com.intellij.openapi.util.NlsActions
import com.intellij.openapi.util.text.StringUtil
import com.intellij.openapi.vfs.VfsUtil
import com.intellij.psi.PsiDirectory
import com.intellij.psi.PsiFile
import com.intellij.psi.PsiFileFactory
import com.intellij.psi.impl.file.PsiDirectoryFactory
import com.intellij.ui.AnimatedIcon
import com.intellij.ui.EditorTextField
import com.intellij.ui.HorizontalScrollBarEditorCustomization
import com.intellij.ui.components.JBCheckBox
import com.intellij.ui.components.JBTextField
import com.intellij.ui.dsl.builder.Cell
import com.intellij.ui.dsl.builder.panel
import com.intellij.util.textCompletion.TextCompletionValueDescriptor
import com.intellij.util.textCompletion.TextFieldWithCompletion
import com.intellij.util.textCompletion.ValuesCompletionProvider
import kotlinx.serialization.json.jsonPrimitive
import java.awt.Dimension
import java.util.Timer
import java.util.TimerTask
import javax.swing.Icon
import javax.swing.JCheckBox
import javax.swing.JComponent
import javax.swing.JLabel
import javax.swing.SwingUtilities
import kotlin.collections.ArrayList

class SingleFileTemplateAction(
    text: @NlsActions.ActionText String?,
    description: @NlsActions.ActionDescription String?,
    icon: Icon?,
) : AnAction(text, description, icon) {
    var templateName: String? = null
    private var changed: Boolean = true
    private var timer: Timer? = null
    private var fileNameField: Cell<JLabel>? = null
    private var previewTextField: Cell<EditorTextField>? = null
    private var loadingIcon: Cell<JLabel>? = null
    private var navigateToFile: Cell<JCheckBox>? = null
    private val formFields: HashMap<String, Cell<JComponent>> = HashMap()
    private val richTextEditorValues = HashMap<String, MutableList<String>>()
    private var indicator = EmptyProgressIndicator()

    override fun actionPerformed(e: AnActionEvent) {
        val psaManager = e.project?.service<PsaManager>()

        if (null === psaManager) {
            return
        }

        val settings = psaManager.getSettings()
        val template = settings.singleFileCodeTemplates?.find { it.name == templateName }

        if (null === template || null === template.formFields) {
            return
        }

        val dataContext: DataContext = e.dataContext
        val view: IdeView = LangDataKeys.IDE_VIEW.getData(dataContext) ?: return

        val directories: Array<PsiDirectory> = view.directories
        if (directories.isEmpty()) {
            return
        }
        val directoryPath =
            VfsUtil.getRelativePath(directories[0].virtualFile, e.project!!.guessProjectDir()!!, '/').toString()
        val formFieldData = HashMap<String, String>()
        template.formFields!!.map { event -> formFieldData[event.name!!] = "" }

        fun updateData(originatorFieldName: String?) {
            if (!changed) {
                return
            }

            val templateData =
                psaManager.generateTemplateCode(
                    settings,
                    e.project!!,
                    directoryPath,
                    template.name!!,
                    "single_file",
                    originatorFieldName,
                    formFieldData,
                )

            if (null === templateData) {
                return
            }

            SwingUtilities.invokeLater {
                ApplicationManager.getApplication().runWriteAction {
                    if (null == templateData.fileName) {
                        return@runWriteAction
                    }

                    val newFileType =
                        FileTypeManager
                            .getInstance()
                            .getFileTypeByFileName(templateData.fileName)

                    if (null !== fileNameField) {
                        fileNameField!!.component.text = templateData.fileName
                    }

                    if (templateData.formFields != null) {
                        for (fieldName in templateData.formFields!!.keys) {
                            val value = templateData.formFields!![fieldName]
                            if (null === value) {
                                continue
                            }

                            if (richTextEditorValues.containsKey(fieldName)) {
                                richTextEditorValues[fieldName]!!.addAll(value.options)
                            }

                            if (formFields.containsKey(fieldName) && value.value != null) {
                                val component = formFields[fieldName]!!.component
                                if (component is TextFieldWithCompletion) {
                                    component.document.setText(value.value!!.jsonPrimitive.content)
                                }
                                if (component is JBTextField) {
                                    component.text = value.value!!.jsonPrimitive.content
                                }
                                if (component is JBCheckBox) {
                                    component.isSelected =
                                        value
                                            .value!!
                                            .jsonPrimitive.content
                                            .toBoolean()
                                }
                                if (component is ComboBox<*>) {
                                    component.selectedItem = value.value!!.jsonPrimitive.content
                                }
                            }
                        }
                    }

                    if (null !== previewTextField) {
                        if (newFileType !== previewTextField!!.component.fileType) {
                            previewTextField!!.component.fileType = newFileType
                        }
                        previewTextField!!.component.document.setText(
                            StringUtil.convertLineSeparators(templateData.content),
                        )
                    }
                }
            }

            changed = false
            loadingIcon?.visible(false)
        }

        val changeListener = fun(
            field: TemplateFormField?,
            value: String?,
        ) {
            changed = true
            loadingIcon?.visible(true)
            if (field !== null && value !== null) {
                formFieldData[field.name!!] = value
            }
            ApplicationManager.getApplication().invokeLater {
                if (null !== timer) {
                    timer?.cancel()
                }

                indicator.cancel()
                indicator = EmptyProgressIndicator()

                timer = Timer()
                timer!!.schedule(
                    object : TimerTask() {
                        override fun run() {
                            try {
                                ApplicationUtil.runWithCheckCanceled(
                                    {
                                        updateData(field?.name)
                                    },
                                    indicator,
                                )
                            } catch (_: ProcessCanceledException) {
                            }
                        }
                    },
                    500,
                )
            }
        }

        val dialogBuilder =
            DialogBuilder().centerPanel(
                panel {
                    for (field in template.formFields!!) {
                        row(field.title!!) {
                            when (field.type) {
                                TemplateFormFieldType.Text -> {
                                    val formField = textField()
                                    formField.validationOnInput {
                                        changeListener(field, it.text)
                                        null
                                    }
                                    formFields[field.name!!] = formField
                                }
                                TemplateFormFieldType.Checkbox -> {
                                    val formField = checkBox("")
                                    formField.validationOnInput {
                                        changeListener(field, formField.component.isSelected.toString())
                                        null
                                    }
                                    formFields[field.name!!] = formField
                                }
                                TemplateFormFieldType.Select -> {
                                    val formField = comboBox(field.options!!)
                                    formField.component.addActionListener {
                                        changeListener(field, formField.component.selectedItem!!.toString())
                                    }
                                    formField.component.selectedItem = field.options!![0]
                                    formFields[field.name!!] = formField
                                }
                                TemplateFormFieldType.Collection -> {
                                    val coll = JTextFieldCollection()
                                    coll.setValues(listOf(""))
                                    val formField = cell(coll)
                                    coll.addValuesChangeListener { e ->
                                        val newValue = e.newValue as List<*>
                                        changeListener(field, newValue.joinToString(","))
                                    }
                                    formFields[field.name!!] = formField
                                }
                                TemplateFormFieldType.RichText -> {
                                    val values = ArrayList<String>()
                                    values.addAll(if (null !== field.options) field.options!!.toList() else listOf())
                                    val richText =
                                        TextFieldWithCompletion(
                                            e.project!!,
                                            ValuesCompletionProvider(
                                                object : TextCompletionValueDescriptor<String> {
                                                    override fun compare(
                                                        o1: String?,
                                                        o2: String?,
                                                    ): Int = o1!!.compareTo(o2!!)

                                                    override fun createLookupBuilder(item: String): LookupElementBuilder =
                                                        LookupElementBuilder.create(item)
                                                },
                                                values,
                                            ),
                                            "",
                                            true,
                                            true,
                                            true,
                                        )
                                    richText.preferredSize = Dimension(204, 30)
                                    richText.document.addDocumentListener(
                                        object : DocumentListener {
                                            override fun documentChanged(event: DocumentEvent) {
                                                changeListener(field, event.document.text)
                                            }
                                        },
                                    )
                                    richTextEditorValues[field.name!!] = values
                                    val cell = cell(richText)
                                    formFields[field.name!!] = cell
                                }

                                null -> {}
                            }

                            if (null !== field.focused && field.focused!!) {
                                SwingUtilities.invokeLater {
                                    formFields[field.name!!]!!.component.requestFocus()
                                }
                            }
                            formFields[field.name!!]!!.component.minimumSize = Dimension(600, 30)
                            formFields[field.name!!]!!.component.preferredSize = Dimension(600, 30)
                        }
                    }
                    row("File Name") {
                        fileNameField = label("")
                    }
                    row {
                        label("Preview")
                        loadingIcon = icon(AnimatedIcon.Default()).visible(false)
                    }
                    row { }
                    row {
                        val previewTextFieldComponent =
                            EditorTextField(
                                EditorFactory.getInstance().createDocument(
                                    StringUtil.convertLineSeparators(""),
                                ),
                                e.project,
                                FileTypes.PLAIN_TEXT,
                                true,
                                false,
                            )
                        previewTextFieldComponent.addSettingsProvider { editor ->
                            run {
                                editor.settings.isLineNumbersShown = true
                                editor.setVerticalScrollbarVisible(true)
                                HorizontalScrollBarEditorCustomization.ENABLED.customize(editor)
                            }
                        }
                        previewTextFieldComponent.preferredSize = Dimension(800, 600)
                        previewTextFieldComponent.minimumSize = Dimension(800, 200)
                        previewTextFieldComponent.maximumSize = Dimension(-1, 600)
                        previewTextField = cell(previewTextFieldComponent)
                    }.resizableRow()
                    row("Navigate to file after creation") {
                        navigateToFile = checkBox("")
                        navigateToFile!!.component.isSelected = true
                    }
                    SwingUtilities.invokeLater { changeListener(null, null) }
                },
            )
        dialogBuilder.setTitle("Create " + template.title)
        if (dialogBuilder.showAndGet()) {
            ApplicationManager.getApplication().runWriteAction {
                val fileFromText: PsiFile =
                    PsiFileFactory
                        .getInstance(e.project)
                        .createFileFromText(
                            fileNameField!!.component.text,
                            previewTextField!!.component.fileType,
                            previewTextField!!.component.document.text,
                        )
                val el =
                    PsiDirectoryFactory
                        .getInstance(e.project)
                        .createDirectory(directories[0].virtualFile)
                        .add(fileFromText)

                if (navigateToFile!!.component.isSelected) {
                    OpenFileDescriptor(e.project!!, el.containingFile.virtualFile, 0).navigate(true)
                }
            }
        }
    }
}
