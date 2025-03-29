@file:Suppress("PLUGIN_IS_NOT_ENABLED", "ktlint:standard:no-wildcard-imports")

package com.github.sam0delkin.intellijpsa.model.psi

import kotlinx.serialization.Serializable

@Serializable
data class IndexedPsiElementModel(
    val model: PsiElementModel,
    val textRange: String,
)
