package org.litellmkt.handlers.openAi

import io.ktor.client.HttpClient
import io.ktor.client.call.body
import io.ktor.client.request.header
import io.ktor.client.request.post
import io.ktor.client.request.setBody
import io.ktor.http.ContentType.Application
import io.ktor.http.HttpHeaders
import io.ktor.http.contentType
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.map
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import org.litellmkt.handlers.EmbeddingHandler
import org.litellmkt.params.BaseParamsModel
import org.litellmkt.results.BaseResultModel

class OpenAiEmbeddingHandler(
    private val baseUrl: String,
    private val httpClient: HttpClient,
    private val apiKey: String
) : EmbeddingHandler {

    override fun embeddings(params: BaseParamsModel): Flow<BaseResultModel> {
        return flow {
            emit(
                httpClient.post(
                    urlString = "$baseUrl/v1/embeddings"
                ) {
                    contentType(Application.Json)
                    header(HttpHeaders.Authorization, "Bearer $apiKey")
                    setBody(params.toOpenAiEmbedding())
                }.body<OpenAiEmbeddingResponse>()
            )
        }.map {
            it.toBaseResultModel()
        }
    }
}

internal fun BaseParamsModel.toOpenAiEmbedding(): OpenAiEmbeddingRequest {
    val input = get("input")
    val processedInput = when (input) {
        is String -> listOf(input)
        is List<*> -> input.filterIsInstance<String>()
        else -> throw IllegalArgumentException("Invalid input - must be string or list of strings")
    }
    
    return OpenAiEmbeddingRequest(
        model = model ?: throw IllegalArgumentException("Invalid model"),
        input = processedInput,
        encodingFormat = get("encoding_format") as? String,
        dimensions = get("dimensions") as? Int,
        user = get("user") as? String
    )
}

internal fun OpenAiEmbeddingResponse.toBaseResultModel(): BaseResultModel {
    return BaseResultModel(
        model = model,
        createdAt = "",
        response = "",
        done = true,
        embeddings = data.map { it.embedding }
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
    val usage: OpenAiEmbeddingUsage
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