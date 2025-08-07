package org.litellmkt.handlers.ollama

import io.ktor.client.HttpClient
import io.ktor.client.call.body
import io.ktor.client.request.get
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.asFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.emitAll
import kotlinx.datetime.Instant
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import org.litellmkt.handlers.ModelDetails
import org.litellmkt.handlers.ModelInfo
import org.litellmkt.handlers.ModelsHandler

class OllamaModelsHandler(
    private val parser: Json,
    private val baseUrl: String,
    private val httpClient: HttpClient
) : ModelsHandler {

    override fun listModels(): Flow<ModelInfo> {
        return flow {
            val response = httpClient.get("${baseUrl}/api/tags")
            val ollamaResponse = response.body<OllamaTagsResponse>()
            emitAll(
                ollamaResponse.models.asFlow()
                    .map { it.toModelInfo() }
            )
        }
    }
}

internal fun OllamaModel.toModelInfo(): ModelInfo {
    return ModelInfo(
        name = name,
        model = model,
        modifiedAt = Instant.parse(modifiedAt),
        size = size,
        digest = digest,
        details = details.toModelDetails()
    )
}

internal fun OllamaModelDetails.toModelDetails(): ModelDetails {
    return ModelDetails(
        parentModel = parentModel,
        format = format,
        family = family,
        families = families,
        parameterSize = parameterSize,
        quantizationLevel = quantizationLevel
    )
}

@Serializable
internal data class OllamaTagsResponse(
    val models: List<OllamaModel>
)

@Serializable
internal data class OllamaModel(
    val name: String,
    val model: String,
    @SerialName("modified_at") val modifiedAt: String,
    val size: Long,
    val digest: String,
    val details: OllamaModelDetails
)

@Serializable
internal data class OllamaModelDetails(
    @SerialName("parent_model") val parentModel: String,
    val format: String,
    val family: String,
    val families: List<String>?,
    @SerialName("parameter_size") val parameterSize: String,
    @SerialName("quantization_level") val quantizationLevel: String
)