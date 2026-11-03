package org.litellmkt.handlers.openAi

import io.ktor.client.HttpClient
import io.ktor.client.request.header
import io.ktor.client.request.post
import io.ktor.client.request.setBody
import io.ktor.client.statement.bodyAsText
import io.ktor.http.ContentType.Application
import io.ktor.http.HttpHeaders
import io.ktor.http.contentType
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import org.litellmkt.handlers.EmbeddingHandler
import org.litellmkt.handlers.internal.decodeProviderChunk
import org.litellmkt.handlers.internal.ensureSuccess
import org.litellmkt.params.BaseParamsModel
import org.litellmkt.params.int
import org.litellmkt.params.requireModel
import org.litellmkt.params.string
import org.litellmkt.params.stringOrList
import org.litellmkt.results.BaseResultModel

class OpenAiEmbeddingHandler(
    private val parser: Json,
    private val baseUrl: String,
    private val httpClient: HttpClient,
    private val apiKey: String
) : EmbeddingHandler {

    override fun embeddings(params: BaseParamsModel): Flow<BaseResultModel> {
        return flow {
            val request = params.toOpenAiEmbedding()
            val httpResponse = httpClient.post(
                urlString = "$baseUrl/v1/embeddings"
            ) {
                contentType(Application.Json)
                header(HttpHeaders.Authorization, "Bearer $apiKey")
                setBody(request)
            }
            httpResponse.ensureSuccess()
            emit(parser.decodeProviderChunk<OpenAiEmbeddingResponse>(httpResponse.bodyAsText()).toBaseResultModel())
        }
    }
}

internal fun BaseParamsModel.toOpenAiEmbedding(): OpenAiEmbeddingRequest {
    return OpenAiEmbeddingRequest(
        model = requireModel(),
        input = stringOrList("input") ?: throw IllegalArgumentException("Missing required parameter 'input'"),
        encodingFormat = string("encoding_format"),
        dimensions = int("dimensions"),
        user = string("user")
    )
}

internal fun OpenAiEmbeddingResponse.toBaseResultModel(): BaseResultModel {
    return BaseResultModel(
        model = model,
        createdAt = "",
        response = "",
        done = true,
        promptEvalCount = usage?.promptTokens,
        embeddings = data.sortedBy { it.index }.map { it.embedding }
    )
}

@Serializable
internal data class OpenAiEmbeddingRequest(
    val model: String,
    val input: List<String>,
    @SerialName("encoding_format")
    val encodingFormat: String? = null,
    val dimensions: Int? = null,
    val user: String? = null
)

@Serializable
internal data class OpenAiEmbeddingResponse(
    @SerialName("object")
    val objectType: String,
    val data: List<OpenAiEmbeddingData>,
    val model: String,
    val usage: OpenAiEmbeddingUsage? = null
)

@Serializable
internal data class OpenAiEmbeddingData(
    @SerialName("object")
    val objectType: String,
    val embedding: List<Double>,
    val index: Int
)

@Serializable
internal data class OpenAiEmbeddingUsage(
    @SerialName("prompt_tokens")
    val promptTokens: Int,
    @SerialName("total_tokens")
    val totalTokens: Int
)
