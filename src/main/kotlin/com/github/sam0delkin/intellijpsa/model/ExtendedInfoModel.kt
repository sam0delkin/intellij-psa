package com.github.sam0delkin.intellijpsa.model

import com.fasterxml.jackson.annotation.JsonProperty
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

@Serializable
class ExtendedStaticCompletionModel : StaticCompletionModel()

class ExtendedCompletionsModel : CompletionsModel() {
    var extendedCompletions: List<ExtendedCompletionModel>? = null

    companion object {
        fun createFromModel(model: CompletionsModel): ExtendedCompletionsModel {
            val extendedModel = ExtendedCompletionsModel()
            extendedModel.completions = model.completions!!.map { ExtendedCompletionModel.create(it) }
            extendedModel.extendedCompletions = model.completions!!.map { ExtendedCompletionModel.create(it) }
            extendedModel.notifications = model.notifications

            return extendedModel
        }
    }
}

@Serializable
class ExtendedStaticCompletionsModel {
    @SerialName("static_completions")
    @JsonProperty("static_completions")
    val staticCompletions: ArrayList<ExtendedStaticCompletionModel>? = null
}
