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
        anyParent: PsiElementPatternModel?,
        prev: PsiElementPatternModel?,
        anyPrev: PsiElementPatternModel?,
        next: PsiElementPatternModel?,
        anyNext: PsiElementPatternModel?,
    ) {
        this.withText = withText
        this.withType = withType
        this.withOptions = withOptions
        this.parent = parent
        this.anyParent = anyParent
        this.prev = prev
        this.anyPrev = anyPrev
        this.next = next
        this.anyNext = anyNext
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

    @SerialName("any_parent")
    @JsonProperty("any_parent")
    var anyParent: PsiElementPatternModel? = null
    var prev: PsiElementPatternModel? = null

    @SerialName("any_prev")
    @JsonProperty("any_prev")
    var anyPrev: PsiElementPatternModel? = null
    var next: PsiElementPatternModel? = null

    @SerialName("any_next")
    @JsonProperty("any_next")
    var anyNext: PsiElementPatternModel? = null
}
