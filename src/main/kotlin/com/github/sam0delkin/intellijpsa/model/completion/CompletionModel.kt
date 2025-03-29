package com.github.sam0delkin.intellijpsa.model.completion

import com.fasterxml.jackson.annotation.JsonProperty
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

@Serializable
open class CompletionModel {
    var text: String? = null
    var bold: Boolean? = null

    @SerialName("presentable_text")
    @JsonProperty("presentable_text")
    var presentableText: String? = null

    @SerialName("tail_text")
    @JsonProperty("tail_text")
    var tailText: String? = null
    var type: String? = null
    var priority: Double? = null
    var link: String? = null
}

@Serializable
class NotificationModel {
    var type: String = ""
    var text: String = ""
}

@Serializable
open class CompletionsModel {
    var completions: List<CompletionModel>? = null
    var notifications: List<NotificationModel>? = null
}
