package org.litellmkt.handlers.ollama

import io.ktor.client.HttpClient
import io.ktor.client.call.body
import io.ktor.client.request.preparePost
import io.ktor.client.request.setBody
import io.ktor.http.ContentType.Application
import io.ktor.http.contentType
import io.ktor.utils.io.ByteReadChannel
import io.ktor.utils.io.readUTF8Line
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import org.koin.core.annotation.Named
import org.koin.core.annotation.Singleton
import org.litellmkt.handlers.GenerationHandler
import org.litellmkt.params.BaseParamsModel
import org.litellmkt.results.BaseResultModel

@Singleton
class OllamaGenerateGenerationHandler(
    private val parser: Json,
    @Named("baseUrl") private val baseUrl: String,
    private val httpClient: HttpClient
) : GenerationHandler {
    override fun stream(params: BaseParamsModel): Flow<BaseResultModel> {
        return flow {
            httpClient.preparePost(
                "${baseUrl}/api/generate"
            ) {
                contentType(Application.Json)
                setBody(params.toOllamaGeneration())
            }.execute { httpResponse ->
                val channel: ByteReadChannel = httpResponse.body()
                while (!channel.isClosedForRead) {
                    channel.readUTF8Line()
                        ?.let {
                            runCatching {
                                parser.decodeFromString<HandlerResultOllama>(it)
                            }.onFailure { error ->
                                error(StreamError(error = error.message ?: ""))
                            }.getOrThrow()
                        }?.let {
                            emit(it.mapToBaseResultModel())
                        }
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

fun OllamaStreamResponse.toStreamResult(): StreamResult {
    return StreamResult(
        model = this.model,
        data = this.response
    )
}

data class StreamResult(
    val model: String,
    val data: String
)

data class CompletionResult(
    val model: String,
    val data: String
)

data class StreamError(
    val error: String
)

fun OllamaError.toStreamError(): StreamError {
    return StreamError(error = error)
}

@Serializable
data class OllamaError(
    val error: String
)

@Serializable
data class OllamaCompletionResponse(
    val id: String,
    @SerialName("object")
    val objectName: String?,
    val created: Long?,
    val model: String?,
    @SerialName("system_fingerprint")
    val systemFingerprint: String?,
    val choices: List<Choice>?,
    val usage: Usage?
)

@Serializable
data class Choice(
    val text: String?,
    val index: Int?,
    @SerialName("finish_reason")
    val finishReason: String?
)

@Serializable
data class Usage(
    @SerialName("prompt_tokens")
    val promptTokens: Int?,
    @SerialName("completion_tokens")
    val completionTokens: Int?,
    @SerialName("total_tokens")
    val totalTokens: Int?
)

@Serializable
data class OllamaStreamResponse(
    val model: String,
    val created_at: String,
    val response: String,
    val done: Boolean
)

@Serializable
internal data class OllamaRequest(
    val model: String,
    val prompt: String
)

@Serializable
internal data class GenerateResponse(
    @SerialName("model")
    val model: String,
    @SerialName("created_at")
    val createdAt: String,
    /**
     *  empty if the response was streamed, if not streamed, this will contain the full response
     */
    @SerialName("response")
    val response: String,
    @SerialName("done")
    val done: Boolean,

    /**
     * reason for the conversation ending
     * E.g "length"
     */
    @SerialName("done_reason")
    val doneReason: String? = null,
    /**
     *  an encoding of the conversation used in this response, this can be sent in the next request to keep a conversational mem
     */
    @SerialName("context")
    val context: List<Int>? = null,
    /**
     * time spent generating the response
     */
    @SerialName("total_duration")
    val totalDuration: Long? = null,
    /**
     * time spent in nanoseconds loading the model
     */
    @SerialName("load_duration")
    val loadDuration: Long? = null,
    /**
     * number of tokens in the prompt
     */
    @SerialName("prompt_eval_count")
    val promptEvalCount: Int? = null,
    /**
     * time spent in nanoseconds evaluating the prompt
     */
    @SerialName("prompt_eval_duration")
    val promptEvalDuration: Long? = null,
    /**
     * number of tokens in the response
     */
    @SerialName("eval_count")
    val evalCount: Int? = null,
    /**
     * time in nanoseconds spent generating the response
     */
    @SerialName("eval_duration")
    val evalDuration: Long? = null,
)

@Serializable
data class OllamaEmbeddingsResult(
    val embedding: List<Double>
)

data class EmbeddingsResult(
    val embeddings: List<Double>
)


@Serializable
data class HandlerResultOllama(
    val model: String,
    @SerialName("created_at")
    val createdAt: String,
    val response: String,
    val done: Boolean,
    @SerialName("total_duration") // Time spent generating the response
    val totalDuration: Long? = -1, // Time spent generating the response
    @SerialName("load_duration") // Time spent loading the model in nanoseconds
    val loadDuration: Long? = -1, // Time spent loading the model in nanoseconds
    @SerialName("prompt_eval_count") // Number of tokens in the prompt
    val promptEvalCount: Int? = -1, // Number of tokens in the prompt
    @SerialName("prompt_eval_duration") // Time spent evaluating the prompt in nanoseconds
    val promptEvalDuration: Long? = -1, // Time spent evaluating the prompt in nanoseconds
    @SerialName("eval_count") // Number of tokens in the response
    val evalCount: Int? = -1, // Number of tokens in the response
    @SerialName("eval_duration") // Time spent generating the response in nanoseconds
    val evalDuration: Long? = -1, // Time spent generating the response in nanoseconds
    val context: List<Int>? = null // Encoding of the conversation used in this response, that can be sent in the next request to keep a conversational memory
)
