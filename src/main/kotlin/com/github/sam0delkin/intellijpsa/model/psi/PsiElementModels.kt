package com.github.sam0delkin.intellijpsa.model.psi

import kotlinx.serialization.Contextual
import kotlinx.serialization.Serializable

@Serializable
data class PsiElementModelChild(
    val model: PsiElementModel? = null,
    var string: String? = null,
    var array: Array<PsiElementModel?>? = null,
) {
    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (javaClass != other?.javaClass) return false

        other as PsiElementModelChild

        if (model != other.model) return false
        if (string != other.string) return false
        if (array != null) {
            if (other.array == null) return false
            if (!array.contentEquals(other.array)) return false
        } else if (other.array != null) {
            return false
        }

        return true
    }

    override fun hashCode(): Int {
        var result = model?.hashCode() ?: 0
        result = 31 * result + (string?.hashCode() ?: 0)
        result = 31 * result + (array?.contentHashCode() ?: 0)
        return result
    }
}

@Serializable
data class PsiElementModelTextRange(
    var startOffset: Int,
    var endOffset: Int,
)

@Serializable
data class PsiElementModel(
    val id: String,
    var elementType: String,
    var options: MutableMap<String, PsiElementModelChild>,
    var elementName: String?,
    var elementFqn: String?,
    var elementSignature: ArrayList<String>?,
    var text: String?,
    var parent: PsiElementModel?,
    var prev: PsiElementModel?,
    var next: PsiElementModel?,
    @Contextual
    var textRange: PsiElementModelTextRange?,
    var filePath: String? = null,
)
