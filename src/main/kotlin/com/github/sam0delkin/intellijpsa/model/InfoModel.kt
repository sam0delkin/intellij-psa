package com.github.sam0delkin.intellijpsa.model

import com.fasterxml.jackson.annotation.JsonProperty
import com.github.sam0delkin.intellijpsa.settings.TemplateFormFieldType
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

@Serializable
enum class EditorActionSource {
    @SerialName("editor")
    @JsonProperty("editor")
    Editor,

    @SerialName("clipboard")
    @JsonProperty("clipboard")
    Clipboard,
}

@Serializable
enum class EditorActionTarget {
    @SerialName("editor")
    @JsonProperty("editor")
    Editor,

    @SerialName("clipboard")
    @JsonProperty("clipboard")
    Clipboard,

    @SerialName("noting")
    @JsonProperty("noting")
    Nothing,
}

@Serializable
enum class TemplateType {
    @SerialName("single_file")
    @JsonProperty("single_file")
    SINGLE_FILE,

    @SerialName("multiple_file")
    @JsonProperty("multiple_file")
    MULTIPLE_FILE,
}

@Serializable
class FormFieldModel {
    val name: String = ""
    val title: String = ""
    val type: TemplateFormFieldType = TemplateFormFieldType.Text
    val focused: Boolean = false
    val options: ArrayList<String> = ArrayList()
}

@Serializable
class FileTemplateModel {
    val name: String = ""
    val type: TemplateType = TemplateType.SINGLE_FILE
    val title: String = ""

    @SerialName("path_regex")
    @JsonProperty("path_regex")
    val pathRegex: String? = null
    val fields: ArrayList<FormFieldModel> = ArrayList()

    @SerialName("file_count")
    @JsonProperty("file_count")
    val fileCount: Int? = null
}

@Serializable
class EditorActionModel {
    val name: String = ""
    val title: String = ""

    @SerialName("group_name")
    @JsonProperty("group_name")
    val groupName: String? = null

    @SerialName("path_regex")
    @JsonProperty("path_regex")
    val pathRegex: String? = null
    val source: EditorActionSource = EditorActionSource.Editor
    val target: EditorActionTarget = EditorActionTarget.Editor
}

@Serializable
class StaticCompletionModel {
    val name: String = ""
    val title: String? = null
    val patterns: ArrayList<PsiElementPatternModel>? = null
    val completions: CompletionsModel? = null
}

@Serializable
class StaticCompletionsModel {
    @SerialName("static_completions")
    @JsonProperty("static_completions")
    val staticCompletions: ArrayList<StaticCompletionModel>? = null
}

@Serializable
class InfoModel {
    @SerialName("supported_languages")
    @JsonProperty("supported_languages")
    val supportedLanguages: ArrayList<String> = ArrayList()

    @SerialName("goto_element_filter")
    @JsonProperty("goto_element_filter")
    val goToElementFilter: ArrayList<String>? = null

    @SerialName("supports_batch")
    @JsonProperty("supports_batch")
    val supportsBatch: Boolean? = null

    val templates: List<FileTemplateModel>? = null

    @SerialName("supports_static_completions")
    @JsonProperty("supports_static_completions")
    val supportsStaticCompletions: Boolean? = null

    @SerialName("editor_actions")
    @JsonProperty("editor_actions")
    val editorActions: List<EditorActionModel>? = null
}
