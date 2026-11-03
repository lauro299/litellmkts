package org.litellmkt.handlers.ollama

import io.ktor.client.HttpClient
import io.ktor.client.request.get
import io.ktor.client.statement.bodyAsText
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow
import kotlinx.datetime.Instant
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import org.litellmkt.handlers.ModelDetails
import org.litellmkt.handlers.ModelInfo
import org.litellmkt.handlers.ModelsHandler
import org.litellmkt.handlers.internal.decodeProviderChunk
import org.litellmkt.handlers.internal.ensureSuccess

class OllamaModelsHandler(
    private val parser: Json,
    private val baseUrl: String,
    private val httpClient: HttpClient
) : ModelsHandler {

    override fun listModels(): Flow<ModelInfo> {
        return flow {
            val httpResponse = httpClient.get("$baseUrl/api/tags")
            httpResponse.ensureSuccess()
            parser.decodeProviderChunk<OllamaTagsResponse>(httpResponse.bodyAsText())
                .models
                .forEach { emit(it.toModelInfo()) }
        }
    }
}

internal fun OllamaModel.toModelInfo(): ModelInfo {
    return ModelInfo(
        name = name,
        model = model.nullIfBlank(),
        // An unparseable date must not drop the whole listing.
        modifiedAt = modifiedAt?.let { runCatching { Instant.parse(it) }.getOrNull() },
        size = size,
        digest = digest.nullIfBlank(),
        details = details?.toModelDetails()
    )
}

internal fun OllamaModelDetails.toModelDetails(): ModelDetails {
    return ModelDetails(
        parentModel = parentModel.nullIfBlank(),
        format = format.nullIfBlank(),
        family = family.nullIfBlank(),
        families = families,
        parameterSize = parameterSize.nullIfBlank(),
        quantizationLevel = quantizationLevel.nullIfBlank()
    )
}

private fun String?.nullIfBlank(): String? = this?.takeIf { it.isNotBlank() }

@Serializable
internal data class OllamaTagsResponse(
    val models: List<OllamaModel>
)

@Serializable
internal data class OllamaModel(
    val name: String,
    val model: String? = null,
    @SerialName("modified_at")
    val modifiedAt: String? = null,
    val size: Long? = null,
    val digest: String? = null,
    val details: OllamaModelDetails? = null
)

@Serializable
internal data class OllamaModelDetails(
    @SerialName("parent_model")
    val parentModel: String? = null,
    val format: String? = null,
    val family: String? = null,
    val families: List<String>? = null,
    @SerialName("parameter_size")
    val parameterSize: String? = null,
    @SerialName("quantization_level")
    val quantizationLevel: String? = null
)
