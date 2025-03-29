package com.github.sam0delkin.intellijpsa.model.template

import kotlinx.serialization.Serializable

@Serializable
data class GenerateFileFromTemplateData(
    val actionPath: String,
    val templateType: String,
    val templateName: String,
    val originatorFieldName: String?,
    val formFields: Map<String, String>,
)
