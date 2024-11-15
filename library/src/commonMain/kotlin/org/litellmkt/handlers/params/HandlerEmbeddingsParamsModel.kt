package org.litellmkt.handlers.params

import kotlinx.serialization.Serializable

sealed interface HandlerEmbeddingsParamsModel : Model {
    val input: List<String>
}

@Serializable
data class HandlerEmbeddingsOllamaParams(
    override val model: String,
    override val input: List<String>,
    val truncate: Boolean? = null,
    val options: Map<String, String>? = null,
) : HandlerEmbeddingsParamsModel
