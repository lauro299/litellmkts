package org.litellmkt.handlers.openAi

import io.ktor.client.HttpClient
import io.ktor.client.request.get
import io.ktor.client.request.header
import io.ktor.client.statement.bodyAsText
import io.ktor.http.HttpHeaders
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow
import kotlinx.datetime.Instant
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import org.litellmkt.handlers.ModelInfo
import org.litellmkt.handlers.ModelsHandler
import org.litellmkt.handlers.internal.decodeProviderChunk
import org.litellmkt.handlers.internal.ensureSuccess

class OpenAiModelsHandler(
    private val parser: Json,
    private val baseUrl: String,
    private val httpClient: HttpClient,
    private val apiKey: String
) : ModelsHandler {

    override fun listModels(): Flow<ModelInfo> {
        return flow {
            val httpResponse = httpClient.get("$baseUrl/v1/models") {
                header(HttpHeaders.Authorization, "Bearer $apiKey")
            }
            httpResponse.ensureSuccess()
            parser.decodeProviderChunk<OpenAiModelsResponse>(httpResponse.bodyAsText())
                .data
                .forEach { emit(it.toModelInfo()) }
        }
    }
}

internal fun OpenAiModel.toModelInfo(): ModelInfo {
    return ModelInfo(
        name = id,
        modifiedAt = created?.let(Instant::fromEpochSeconds),
        ownedBy = ownedBy
    )
}

@Serializable
internal data class OpenAiModelsResponse(
    val data: List<OpenAiModel>
)

@Serializable
internal data class OpenAiModel(
    val id: String,
    val created: Long? = null,
    @SerialName("owned_by")
    val ownedBy: String? = null
)
