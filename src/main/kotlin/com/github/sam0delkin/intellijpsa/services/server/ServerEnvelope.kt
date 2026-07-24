package com.github.sam0delkin.intellijpsa.services.server

import kotlinx.serialization.Serializable
import kotlinx.serialization.json.JsonElement

@Serializable
data class ServerRequestEnvelope(
    val id: String,
    val type: String,
    val language: String? = null,
    val debug: Boolean = false,
    val offset: Int? = null,
    val context: JsonElement? = null,
)

@Serializable
data class ServerResponseEnvelope(
    val id: String,
    val result: JsonElement? = null,
    val error: String? = null,
)

data class ServerResponse(
    val result: JsonElement?,
    val error: String?,
)
