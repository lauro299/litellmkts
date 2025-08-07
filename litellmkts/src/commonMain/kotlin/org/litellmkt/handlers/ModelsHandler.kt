package org.litellmkt.handlers

import kotlinx.coroutines.flow.Flow
import kotlinx.datetime.Instant

data class ModelInfo(
    val name: String,
    val model: String,
    val modifiedAt: Instant,
    val size: Long,
    val digest: String,
    val details: ModelDetails
)

data class ModelDetails(
    val parentModel: String,
    val format: String,
    val family: String,
    val families: List<String>?,
    val parameterSize: String,
    val quantizationLevel: String
)

interface ModelsHandler {
    fun listModels(): Flow<ModelInfo>
}