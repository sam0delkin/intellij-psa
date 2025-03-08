package com.github.sam0delkin.intellijpsa.model

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.JsonElement

@Serializable
class FormFieldDataModel {
    var value: JsonElement? = null
    val options = ArrayList<String>()
}

@Serializable
class TemplateDataModel {
    @SerialName("file_name")
    val fileName: String? = null

    @SerialName("file_names")
    val fileNames: ArrayList<String>? = null
    val content: String = ""
    val contents: ArrayList<String>? = null

    @SerialName("form_fields")
    var formFields: MutableMap<String, FormFieldDataModel>? = null
}
