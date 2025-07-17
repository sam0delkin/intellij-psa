package com.github.sam0delkin.intellijpsa.model

import com.fasterxml.jackson.annotation.JsonProperty
import com.github.sam0delkin.intellijpsa.model.completion.CompletionsModel
import com.github.sam0delkin.intellijpsa.model.completion.ExtendedCompletionModel
import com.intellij.openapi.project.Project
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.Transient

@Serializable
class ExtendedStaticCompletionModel : StaticCompletionModel() {
    @Transient
    @com.intellij.util.xmlb.annotations.Transient
    var extendedCompletions: ExtendedCompletionsModel? = null

    companion object {
        fun createFromModel(
            model: StaticCompletionModel,
            project: Project,
        ): ExtendedStaticCompletionModel {
            val extendedModel = ExtendedStaticCompletionModel()
            extendedModel.name = model.name
            extendedModel.title = model.title
            extendedModel.patterns = model.patterns
            extendedModel.matcher = model.matcher
            extendedModel.completions = model.completions
            extendedModel.extendedCompletions = ExtendedCompletionsModel.createFromModel(model.completions!!, project)

            return extendedModel
        }
    }

    fun toStaticCompletionModel(): StaticCompletionModel {
        val model = StaticCompletionModel()
        model.name = name
        model.title = title
        model.patterns = patterns
        model.matcher = matcher
        model.completions = completions

        return model
    }
}

@Serializable
class ExtendedCompletionsModel : CompletionsModel() {
    @Transient
    @com.intellij.util.xmlb.annotations.Transient
    var extendedCompletions: MutableList<ExtendedCompletionModel>? = null

    companion object {
        fun createFromModel(
            model: CompletionsModel,
            project: Project,
        ): ExtendedCompletionsModel {
            val extendedModel = ExtendedCompletionsModel()
            extendedModel.completions = model.completions
            extendedModel.extendedCompletions = model.completions!!.map { ExtendedCompletionModel.create(it, project) }.toMutableList()
            extendedModel.notifications = model.notifications

            return extendedModel
        }
    }
}

@Serializable
class ExtendedStaticCompletionsModel {
    @SerialName("static_completions")
    @JsonProperty("static_completions")
    var staticCompletions: ArrayList<ExtendedStaticCompletionModel>? = null

    @Transient
    var hash: String? = null
}
