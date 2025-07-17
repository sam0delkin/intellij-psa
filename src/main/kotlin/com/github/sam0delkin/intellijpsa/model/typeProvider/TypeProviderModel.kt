package com.github.sam0delkin.intellijpsa.model.typeProvider

import com.github.sam0delkin.intellijpsa.model.psi.PsiElementPatternModel
import kotlinx.serialization.Serializable

@Serializable
class TypeProviderModel {
    var language: String = ""
    var pattern: PsiElementPatternModel? = null
    var type: String = ""
}

@Serializable
class TypeProvidersModel {
    val providers: ArrayList<TypeProviderModel>? = null
}
