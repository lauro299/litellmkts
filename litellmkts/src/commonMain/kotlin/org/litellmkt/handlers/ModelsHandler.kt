package org.litellmkt.handlers

import kotlinx.coroutines.flow.Flow
import kotlinx.datetime.Instant

/**
 * A model available on the provider's server. Fields a provider does not report are null.
 *
 * @property name model name (Ollama `name`, OpenAI `id`).
 * @property model Ollama model reference.
 * @property modifiedAt last modification (Ollama `modified_at`) or creation time (OpenAI `created`).
 * @property size size in bytes (Ollama).
 * @property digest content digest (Ollama).
 * @property ownedBy owner of the model (OpenAI `owned_by`).
 * @property details format and quantization details (Ollama).
 */
data class ModelInfo(
    val name: String,
    val model: String? = null,
    val modifiedAt: Instant? = null,
    val size: Long? = null,
    val digest: String? = null,
    val ownedBy: String? = null,
    val details: ModelDetails? = null
)

data class ModelDetails(
    val parentModel: String? = null,
    val format: String? = null,
    val family: String? = null,
    val families: List<String>? = null,
    val parameterSize: String? = null,
    val quantizationLevel: String? = null
)

interface ModelsHandler {
    /** Emits one [ModelInfo] per model available on the server, in the provider's order. */
    fun listModels(): Flow<ModelInfo>
}
