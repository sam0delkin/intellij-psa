package com.github.sam0delkin.intellijpsa.model

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

@Serializable
class CompletionModel {
    val text: String? = null
    val bold: Boolean? = null

    @SerialName("presentable_text")
    val presentableText: String? = null

    @SerialName("tail_text")
    val tailText: String? = null
    val type: String? = null
    val priority: Double? = null
    val link: String? = null
}

@Serializable
class NotificationModel {
    var type: String = ""
    var text: String = ""
}

@Serializable
class CompletionsModel {
    val completions: List<CompletionModel>? = null
    val notifications: List<NotificationModel>? = null
}
