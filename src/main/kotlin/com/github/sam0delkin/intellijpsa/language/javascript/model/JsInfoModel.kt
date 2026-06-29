package com.github.sam0delkin.intellijpsa.language.javascript.model

import com.fasterxml.jackson.annotation.JsonProperty
import com.github.sam0delkin.intellijpsa.model.InfoModel
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

@Serializable
class JsInfoModel : InfoModel() {
    @SerialName("js_method_argument_providers")
    @JsonProperty("js_method_argument_providers")
    val methodArgumentProviders: ArrayList<JsMethodArgumentProviderModel>? = null
}
