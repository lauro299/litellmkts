package org.litellmkt.handlers.ollama

import io.ktor.client.HttpClient
import io.ktor.client.call.body
import io.ktor.client.request.preparePost
import io.ktor.client.request.setBody
import io.ktor.http.ContentType.Application
import io.ktor.http.contentType
import io.ktor.utils.io.ByteReadChannel
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonPrimitive
import org.litellmkt.handlers.GenerationHandler
import org.litellmkt.handlers.internal.decodeProviderChunk
import org.litellmkt.handlers.internal.ensureSuccess
import org.litellmkt.handlers.internal.forEachLine
import org.litellmkt.params.BaseParamsModel
import org.litellmkt.params.boolean
import org.litellmkt.params.numberOrString
import org.litellmkt.params.requireModel
import org.litellmkt.params.string
import org.litellmkt.params.stringList
import org.litellmkt.results.BaseResultModel

class OllamaGenerateGenerationHandler(
    private val parser: Json,
    private val baseUrl: String,
    private val httpClient: HttpClient
) : GenerationHandler {
    override fun stream(params: BaseParamsModel): Flow<BaseResultModel> {
        return flow {
            val request = params.toOllamaGeneration()
            httpClient.preparePost(
                "${baseUrl}/api/generate"
            ) {
                contentType(Application.Json)
                setBody(request)
            }.execute { httpResponse ->
                httpResponse.ensureSuccess()
                httpResponse.body<ByteReadChannel>().forEachLine { line ->
                    emit(parser.decodeProviderChunk<HandlerResultOllama>(line).mapToBaseResultModel())
                }
            }
        }
    }
}

internal fun HandlerResultOllama.mapToBaseResultModel(): BaseResultModel {
    return BaseResultModel(
        model = model,
        createdAt = createdAt,
        response = response,
        done = done,
        totalDuration = totalDuration,
        loadDuration = loadDuration,
        promptEvalCount = promptEvalCount,
        promptEvalDuration = promptEvalDuration,
        evalCount = evalCount,
        evalDuration = evalDuration,
        context = context
    )
}

internal fun BaseParamsModel.toOllamaGeneration(): HandlerParamsOllama {
    return HandlerParamsOllama(
        model = requireModel(),
        prompt = string("prompt") ?: throw IllegalArgumentException("Missing required parameter 'prompt'"),
        suffix = string("suffix"),
        images = stringList("images"),
        format = string("format"),
        options = toOllamaOptions(),
        system = string("system"),
        template = string("template"),
        stream = boolean("stream"),
        raw = boolean("raw"),
        keepAlive = numberOrString("keep_alive")
    )
}

@Serializable
internal data class HandlerParamsOllama(
    val model: String,
    val prompt: String,
    val suffix: String? = null,
    val images: List<String>? = null,
    val format: String? = null,
    val options: HandlerParamsOllamaOptions? = null,
    val system: String? = null,
    val template: String? = null,
    val stream: Boolean? = null,
    val raw: Boolean? = null,
    @SerialName("keep_alive")
    val keepAlive: JsonPrimitive? = null
)

@Serializable
internal data class HandlerResultOllama(
    val model: String,
    @SerialName("created_at")
    val createdAt: String,
    val response: String,
    val done: Boolean,
    @SerialName("total_duration") // Time spent generating the response
    val totalDuration: Long? = null,
    @SerialName("load_duration") // Time spent loading the model in nanoseconds
    val loadDuration: Long? = null,
    @SerialName("prompt_eval_count") // Number of tokens in the prompt
    val promptEvalCount: Int? = null,
    @SerialName("prompt_eval_duration") // Time spent evaluating the prompt in nanoseconds
    val promptEvalDuration: Long? = null,
    @SerialName("eval_count") // Number of tokens in the response
    val evalCount: Int? = null,
    @SerialName("eval_duration") // Time spent generating the response in nanoseconds
    val evalDuration: Long? = null,
    val context: List<Int>? = null // Encoding of the conversation used in this response, that can be sent in the next request to keep a conversational memory
)
