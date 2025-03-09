package com.github.sam0delkin.intellijpsa.model

import com.fasterxml.jackson.annotation.JsonProperty
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

@Serializable
class PsiElementPatternModel {
    constructor(
        withText: String?,
        withType: String?,
        withOptions: Map<String, String>?,
        parent: PsiElementPatternModel?,
        prev: PsiElementPatternModel?,
        next: PsiElementPatternModel?,
    ) {
        this.withText = withText
        this.withType = withType
        this.withOptions = withOptions
        this.parent = parent
        this.prev = prev
        this.next = next
    }

    @SerialName("with_text")
    @JsonProperty("with_text")
    var withText: String? = null

    @SerialName("with_type")
    @JsonProperty("with_type")
    var withType: String? = null

    @SerialName("with_options")
    @JsonProperty("with_options")
    var withOptions: Map<String, String>? = null
    var parent: PsiElementPatternModel? = null
    var prev: PsiElementPatternModel? = null
    var next: PsiElementPatternModel? = null
}
