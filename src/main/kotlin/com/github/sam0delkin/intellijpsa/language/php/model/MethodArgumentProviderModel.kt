package com.github.sam0delkin.intellijpsa.language.php.model

import com.fasterxml.jackson.annotation.JsonProperty
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

@Serializable
class MethodArgumentProviderModel {
    var `class`: String = ""
    var method: String = ""

    @SerialName("class_argument_index")
    @JsonProperty("class_argument_index")
    var classArgumentIndex: Int? = null

    @SerialName("method_argument_index")
    @JsonProperty("method_argument_index")
    var methodArgumentIndex: Int? = null

    @SerialName("callable_argument_index")
    @JsonProperty("callable_argument_index")
    var callableArgumentIndex: Int? = null

    @SerialName("arguments_argument_index")
    @JsonProperty("arguments_argument_index")
    var argumentsArgumentIndex: Int = 0

    @SerialName("arguments_offset")
    @JsonProperty("arguments_offset")
    var argumentsOffset: Int = 0
}
