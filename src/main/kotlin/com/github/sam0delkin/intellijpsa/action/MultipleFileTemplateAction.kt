@file:Suppress("ktlint:standard:no-wildcard-imports")

package com.github.sam0delkin.intellijpsa.action

import com.github.sam0delkin.intellijpsa.services.PsaManager
import com.github.sam0delkin.intellijpsa.settings.TemplateFormField
import com.github.sam0delkin.intellijpsa.settings.TemplateFormFieldType
import com.github.sam0delkin.intellijpsa.ui.components.JTextFieldCollection
import com.github.sam0delkin.intellijpsa.ui.components.Utils
import com.intellij.codeInsight.lookup.LookupElementBuilder
import com.intellij.icons.AllIcons
import com.intellij.ide.IdeView
import com.intellij.openapi.actionSystem.AnAction
import com.intellij.openapi.actionSystem.AnActionEvent
import com.intellij.openapi.actionSystem.DataContext
import com.intellij.openapi.actionSystem.LangDataKeys
import com.intellij.openapi.actionSystem.impl.ActionButton
import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.application.ex.ApplicationUtil
import com.intellij.openapi.components.service
import com.intellij.openapi.editor.EditorFactory
import com.intellij.openapi.editor.event.DocumentEvent
import com.intellij.openapi.editor.event.DocumentListener
import com.intellij.openapi.fileEditor.OpenFileDescriptor
import com.intellij.openapi.fileTypes.FileType
import com.intellij.openapi.fileTypes.FileTypeManager
import com.intellij.openapi.fileTypes.FileTypes
import com.intellij.openapi.progress.EmptyProgressIndicator
import com.intellij.openapi.progress.ProcessCanceledException
import com.intellij.openapi.project.DumbAwareAction
import com.intellij.openapi.project.guessProjectDir
import com.intellij.openapi.ui.ComboBox
import com.intellij.openapi.ui.DialogBuilder
import com.intellij.openapi.util.NlsActions
import com.intellij.openapi.util.text.StringUtil
import com.intellij.openapi.vfs.VfsUtil
import com.intellij.openapi.vfs.VirtualFileManager
import com.intellij.psi.PsiDirectory
import com.intellij.ui.AnimatedIcon
import com.intellij.ui.EditorTextField
import com.intellij.ui.HorizontalScrollBarEditorCustomization
import com.intellij.ui.components.JBCheckBox
import com.intellij.ui.components.JBLabel
import com.intellij.ui.components.JBTabbedPane
import com.intellij.ui.components.JBTextField
import com.intellij.ui.dsl.builder.*
import com.intellij.util.textCompletion.TextCompletionValueDescriptor
import com.intellij.util.textCompletion.TextFieldWithCompletion
import com.intellij.util.textCompletion.ValuesCompletionProvider
import kotlinx.serialization.json.jsonPrimitive
import java.awt.Dimension
import java.awt.FlowLayout
import java.awt.Point
import java.nio.charset.StandardCharsets
import java.nio.file.Files
import java.nio.file.Path
import java.nio.file.Paths
import java.util.Timer
import java.util.TimerTask
import javax.swing.*

class MultipleFileTemplateAction(
    text: @NlsActions.ActionText String?,
    description: @NlsActions.ActionDescription String?,
    icon: Icon?,
) : AnAction(text, description, icon) {
    var templateName: String? = null
    private var changed: Boolean = true
    private var timer: Timer? = null
    private var filePathFields: ArrayList<JLabel> = ArrayList()
    private var filePaths: ArrayList<String> = ArrayList()
    private var tabbedPane: JBTabbedPane? = null
    private var previewTextFields: ArrayList<EditorTextField> = ArrayList()
    private var loadingIcon: Cell<JLabel>? = null
    private var errorIcon: Cell<ActionButton>? = null
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
        val template = settings.multipleFileCodeTemplates?.find { it.name == templateName }

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
                    "multiple_file",
                    originatorFieldName,
                    formFieldData,
                )

            if (null === templateData) {
                loadingIcon?.visible(false)
                errorIcon?.visible(true)

                return
            }

            SwingUtilities.invokeLater {
                ApplicationManager.getApplication().runWriteAction {
                    if (null == templateData.fileNames) {
                        return@runWriteAction
                    }
                    val fileTypes = ArrayList<FileType>()

                    filePaths.clear()
                    for (key in 0 until templateData.fileNames.size) {
                        val filePath = templateData.fileNames[key]
                        val fileName = filePath.split('/').last()
                        val newFileType =
                            FileTypeManager
                                .getInstance()
                                .getFileTypeByFileName(fileName)
                        fileTypes.add(newFileType)
                        this.tabbedPane!!.setTitleAt(key, fileName)
                        filePathFields[key].text = "File Path: $filePath"
                        filePaths.add(filePath)
                    }

                    if (templateData.formFields != null) {
                        for (fieldName in templateData.formFields!!.keys) {
                            val value = templateData.formFields!![fieldName] ?: continue

                            if (richTextEditorValues.containsKey(fieldName)) {
                                richTextEditorValues[fieldName]!!.addAll(value.options)
                            }

                            if (formFields.containsKey(fieldName)) {
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

                    if (templateData.contents != null) {
                        for (index in 0 until templateData.contents.size) {
                            val previewTextField = previewTextFields[index]
                            val newFileType = fileTypes[index]
                            if (newFileType !== previewTextField.fileType) {
                                previewTextField.fileType = newFileType
                            }
                            val contents = templateData.contents[index]
                            previewTextField.document.setText(contents)
                        }
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
            errorIcon?.visible(false)
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
                                    cell(richText)
                                }

                                null -> {}
                            }
                        }
                    }
                    row {
                        label("Preview")
                        loadingIcon = icon(AnimatedIcon.Default()).visible(false)
                        @Suppress("DialogTitleCapitalization")
                        val action =
                            object : DumbAwareAction(
                                "Get List of supported languages by your IDE",
                                "",
                                AllIcons.General.ErrorDialog,
                            ) {
                                override fun actionPerformed(e: AnActionEvent) {
                                    val tooltip =
                                        com.intellij.ui
                                            .GotItTooltip(
                                                "PSA",
                                                psaManager.lastResultMessage,
                                            ).withIcon(AllIcons.General.ErrorDialog)
                                            .withButtonLabel("OK")
                                    tooltip.createAndShow(errorIcon!!.component) { c, _ ->
                                        Point(
                                            c.width,
                                            c.height / 2,
                                        )
                                    }
                                }
                            }
                        errorIcon = Utils.actionButton(action, "bottom", this).visible(false)
                    }
                    row { }
                    row {
                        panel {
                            row {
                                tabbedPane = JBTabbedPane()

                                for (i in 0 until template.fileCount!!) {
                                    val panel = JPanel(FlowLayout(FlowLayout.LEFT))
                                    panel.preferredSize = Dimension(800, 600)
                                    panel.minimumSize = Dimension(800, 200)
                                    panel.maximumSize = Dimension(-1, 600)
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
                                    previewTextFields.add(previewTextFieldComponent)
                                    filePathFields.add(JBLabel("File path: "))
                                    panel.add(filePathFields[i])
                                    panel.add(previewTextFieldComponent)
                                    panel.isVisible = true
                                    tabbedPane!!.addTab("File 1", AllIcons.FileTypes.Any_type, panel)
                                }

                                cell(tabbedPane!!)
                            }
                        }
                    }.resizableRow()
                    row("Navigate to files after creation") {
                        navigateToFile = checkBox("")
                        navigateToFile!!.component.isSelected = true
                    }
                    SwingUtilities.invokeLater { changeListener(null, null) }
                },
            )
        dialogBuilder.setTitle("Create " + template.title)
        if (dialogBuilder.showAndGet()) {
            ApplicationManager.getApplication().runWriteAction {
                var index = 0
                filePaths.forEach {
                    val previewTextField = previewTextFields[index]
                    val fullFileName = e.project!!.guessProjectDir()!!.path + "/" + it
                    val fileNameParts = fullFileName.split("/")
                    val filePath = fileNameParts.dropLast(1).joinToString("/")
                    Files.createDirectories(Paths.get(filePath))
                    val file: Path = Paths.get(fullFileName)
                    Files.write(file, previewTextField.document.text.split("\n"), StandardCharsets.UTF_8)
                    val virtualFile = VirtualFileManager.getInstance().refreshAndFindFileByUrl("file://$fullFileName")

                    if (navigateToFile!!.component.isSelected && virtualFile !== null) {
                        OpenFileDescriptor(e.project!!, virtualFile, 0).navigate(true)
                    }
                    index++
                }
            }
        }
    }
}
