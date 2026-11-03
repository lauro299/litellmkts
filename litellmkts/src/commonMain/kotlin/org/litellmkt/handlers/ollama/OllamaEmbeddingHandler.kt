package org.litellmkt.handlers.ollama

import io.ktor.client.HttpClient
import io.ktor.client.request.post
import io.ktor.client.request.setBody
import io.ktor.client.statement.bodyAsText
import io.ktor.http.ContentType.Application
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
import org.litellmkt.params.boolean
import org.litellmkt.params.requireModel
import org.litellmkt.params.stringOrList
import org.litellmkt.results.BaseResultModel

class OllamaEmbeddingHandler(
    private val parser: Json,
    private val baseUrl: String,
    private val httpClient: HttpClient
) : EmbeddingHandler {

    override fun embeddings(params: BaseParamsModel): Flow<BaseResultModel> {
        return flow {
            val request = params.toOllamaEmbeddingsParam()
            val httpResponse = httpClient.post(
                urlString = "$baseUrl/api/embed"
            ) {
                contentType(Application.Json)
                setBody(request)
            }
            httpResponse.ensureSuccess()
            emit(parser.decodeProviderChunk<HandlerResultOllamaEmbeddings>(httpResponse.bodyAsText()).toBaseResultModel())
        }
    }
}

internal fun BaseParamsModel.toOllamaEmbeddingsParam(): HandlerEmbeddingsOllamaParams {
    return HandlerEmbeddingsOllamaParams(
        model = requireModel(),
        input = stringOrList("input") ?: throw IllegalArgumentException("Missing required parameter 'input'"),
        truncate = boolean("truncate"),
        options = toOllamaOptions()
    )
}

@Serializable
internal data class HandlerEmbeddingsOllamaParams(
    val model: String,
    val input: List<String>,
    val truncate: Boolean? = null,
    val options: HandlerParamsOllamaOptions? = null
)

@Serializable
internal data class HandlerResultOllamaEmbeddings(
    val model: String,
    val embeddings: List<List<Double>>,
    @SerialName("total_duration")
    val totalDuration: Long? = null,
    @SerialName("load_duration")
    val loadDuration: Long? = null,
    @SerialName("prompt_eval_count")
    val promptEvalCount: Int? = null,
    @SerialName("prompt_eval_duration")
    val promptEvalDuration: Long? = null,
    @SerialName("eval_count")
    val evalCount: Int? = null,
    @SerialName("eval_duration")
    val evalDuration: Long? = null
)

internal fun HandlerResultOllamaEmbeddings.toBaseResultModel(): BaseResultModel {
    return BaseResultModel(
        model = model,
        createdAt = "",
        response = "",
        done = true,
        totalDuration = totalDuration,
        loadDuration = loadDuration,
        promptEvalCount = promptEvalCount,
        promptEvalDuration = promptEvalDuration,
        evalCount = evalCount,
        evalDuration = evalDuration,
        embeddings = embeddings
    )
}
