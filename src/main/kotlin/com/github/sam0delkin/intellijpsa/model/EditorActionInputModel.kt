package com.github.sam0delkin.intellijpsa.model

import com.fasterxml.jackson.annotation.JsonProperty
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

@Serializable
class EditorActionInputModel {
    constructor(
        actionName: String,
        fileName: String,
        text: String? = null,
    ) {
        this.actionName = actionName
        this.fileName = fileName
        this.text = text
    }

    @SerialName("action_name")
    @JsonProperty("action_name")
    var actionName: String = ""

    @SerialName("file_name")
    @JsonProperty("file_name")
    var fileName: String = ""
    var text: String? = null
}
