package com.github.sam0delkin.intellijpsa.language.javascript.model

import com.fasterxml.jackson.annotation.JsonProperty
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

@Serializable
class JsMethodArgumentProviderModel {
    @SerialName("reference_name")
    @JsonProperty("reference_name")
    var referenceName: String = ""

    @SerialName("class")
    @JsonProperty("class")
    var `class`: String = ""

    @SerialName("method_argument_index")
    @JsonProperty("method_argument_index")
    var methodArgumentIndex: Int = 0

    @SerialName("arguments_offset")
    @JsonProperty("arguments_offset")
    var argumentsOffset: Int = 1
}
