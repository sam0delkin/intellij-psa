package com.github.sam0delkin.intellijpsa.model

import com.github.sam0delkin.intellijpsa.settings.TemplateFormFieldType
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

@Serializable
enum class TemplateType {
    @SerialName("single_file")
    SINGLE_FILE,

    @SerialName("multiple_file")
    MULTIPLE_FILE,
}

@Serializable
class FormFieldModel {
    val name: String = ""
    val title: String = ""
    val type: TemplateFormFieldType = TemplateFormFieldType.Text
    val focused: Boolean = false
    val options: List<String> = ArrayList()
}

@Serializable
class FileTemplateModel {
    val name: String = ""
    val type: TemplateType = TemplateType.SINGLE_FILE
    val title: String = ""

    @SerialName("path_regex")
    val pathRegex: String? = null
    val fields: List<FormFieldModel> = ArrayList()

    @SerialName("file_count")
    val fileCount: Int? = null
}

@Serializable
class InfoModel {
    @SerialName("supported_languages")
    val supportedLanguages: List<String> = ArrayList()

    @SerialName("goto_element_filter")
    val goToElementFilter: List<String>? = null

    @SerialName("supports_batch")
    val supportsBatch: Boolean? = null

    val templates: List<FileTemplateModel>? = null
}
