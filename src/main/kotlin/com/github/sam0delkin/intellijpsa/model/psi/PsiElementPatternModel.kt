package com.github.sam0delkin.intellijpsa.model.psi

import com.fasterxml.jackson.annotation.JsonProperty
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

@Serializable
class PsiElementPatternModel {
    constructor(
        withText: String? = null,
        withType: String? = null,
        withOptions: Map<String, String>? = null,
        parent: PsiElementPatternModel? = null,
        anyParent: PsiElementPatternModel? = null,
        prev: PsiElementPatternModel? = null,
        anyPrev: PsiElementPatternModel? = null,
        next: PsiElementPatternModel? = null,
        anyNext: PsiElementPatternModel? = null,
        anyChild: PsiElementPatternModel? = null,
        withMatcher: String? = null,
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
        this.anyChild = anyChild
        this.withMatcher = withMatcher
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

    @SerialName("any_child")
    @JsonProperty("any_child")
    var anyChild: PsiElementPatternModel? = null

    @SerialName("with_matcher")
    @JsonProperty("with_matcher")
    var withMatcher: String? = null
}
