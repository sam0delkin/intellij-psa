package com.github.sam0delkin.intellijpsa.language.php.model

import com.fasterxml.jackson.annotation.JsonProperty
import com.github.sam0delkin.intellijpsa.model.InfoModel
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

@Serializable
class PhpInfoModel : InfoModel() {
    @SerialName("supports_type_providers")
    @JsonProperty("supports_type_providers")
    val supportsTypeProviders: Boolean? = null

    @SerialName("to_string_value_formatter")
    @JsonProperty("to_string_value_formatter")
    val toStringValueFormatter: String? = null
}
