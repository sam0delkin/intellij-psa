package com.github.sam0delkin.intellijpsa.model.contributor

import com.fasterxml.jackson.annotation.JsonProperty
import com.github.sam0delkin.intellijpsa.model.psi.PsiElementPatternModel
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

@Serializable
enum class StaticContributorScope {
    @SerialName("file")
    @JsonProperty("file")
    File,

    @SerialName("project")
    @JsonProperty("project")
    Project,
}

@Serializable
class StaticContributorModel {
    var name: String = ""

    @SerialName("path_regex")
    @JsonProperty("path_regex")
    var pathRegex: String? = null
    var scope: StaticContributorScope = StaticContributorScope.File
    var pattern: PsiElementPatternModel? = null

    @SerialName("completion_provider")
    @JsonProperty("completion_provider")
    var completionProvider: String = ""
}
