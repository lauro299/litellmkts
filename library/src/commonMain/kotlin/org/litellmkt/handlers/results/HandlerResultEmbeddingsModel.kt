package org.litellmkt.handlers.results

import kotlinx.serialization.Serializable
import org.litellmkt.handlers.params.Model

sealed interface HandlerResultEmbeddingsModel : Model, Evaluation

@Serializable
data class HandlerResultOllamaEmbeddings(
    override val model: String,
    val embeddings: List<List<Double>>,
    override val totalDuration: Long? = null,
    override val loadDuration: Long? = null,
    override val promptEvalCount: Int? = null,
    override val promptEvalDuration: Long? = null,
    override val evalCount: Int? = null,
    override val evalDuration: Long? = null
) : HandlerResultEmbeddingsModel

