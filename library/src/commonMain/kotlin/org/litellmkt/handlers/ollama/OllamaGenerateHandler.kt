package org.litellmkt.handlers.ollama

import io.ktor.client.HttpClient
import io.ktor.client.call.body
import io.ktor.client.request.preparePost
import io.ktor.client.request.setBody
import io.ktor.http.ContentType
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
import org.litellmkt.handlers.HandlerGenerate
import org.litellmkt.handlers.params.HandlerParamsOllama
import org.litellmkt.handlers.results.HandlerResultOllama

@Singleton
class OllamaGenerateHandler(
    private val parser: Json,
    @Named("baseUrl") private val baseUrl: String,
    private val httpClient: HttpClient
) : HandlerGenerate<HandlerParamsOllama, HandlerResultOllama> {
    override fun stream(params: HandlerParamsOllama): Flow<HandlerResultOllama> {
        return flow {
            httpClient.preparePost(
                "${baseUrl}/api/generate"
            ) {
                contentType(ContentType.Application.Json)
                setBody(params)
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
                            emit(it)
                        }
                }
            }
        }
    }

    /*override fun embeddings(params: HandlerParamsOllama): Flow<Double> {
        return flow<String> {
            httpClient.preparePost(
                "${baseUrl}/api/embeddings"
            ) {
                contentType(ContentType.Application.Json)
                setBody(OllamaRequest(model = params.model, prompt = params.prompt))
            }.execute { response: HttpResponse ->
                var firsTime = true
                val channel: ByteReadChannel = response.body()
                while (!channel.isClosedForRead) {
                    val packet = channel.readUTF8Line()
                    var lastItem: String = ""
                    if (!firsTime) {
                        packet?.split(",")
                            ?.let {
                                val isConcatenated =
                                    if (lastItem.isNotEmpty()) {
                                        emit("$lastItem${it.first()}")
                                        true
                                    } else false
                                lastItem = it.last()
                                it.subList(takeIf { isConcatenated }?.let { 1 } ?: 0, it.size - 1)
                            }?.let { subList ->
                                emitAll(subList.asFlow())
                            }
                    } else {
                        firsTime = false
                        packet?.split("[")
                            ?.last()
                            ?.split(",")
                            ?.let { list ->
                                lastItem = list.last()
                                emitAll(list.subList(0, list.size - 1).asFlow())
                            }
                    }
                    if (lastItem.isNotEmpty()) {
                        emit(lastItem)
                    }
                }
            }
        }.map { numberString ->
            println(numberString)
            if (!numberString.contains("]}")) {
                numberString.toDouble()
            } else {
                numberString.removeSuffix("]}")
                    .toDouble()
            }
        }
    }*/

    /*@OptIn(ExperimentalCoroutinesApi::class)
    override fun embeddings(params: HandlerParamsBase): Flow<Double> {
        return flow {
            httpClient.preparePost(
                "${params.baseUrl}/api/embeddings"
            ) {
                contentType(ContentType.Application.Json)
                setBody(GenerateBody(model = params.model, prompt = combineMessages(params.messages)))
            }.execute { response: HttpResponse ->
                val channel: ByteReadChannel = response.body()
                while (!channel.isClosedForRead){
                    val packet = channel.readUTF8Line()
                    emit(packet ?: "")
                }
            }
        }.runningReduce{ accumulator, value ->
            "$accumulator$value"
        }.mapLatest {
            runCatching {
                EmbeddingsResult(
                    embeddings = localJson.decodeFromString<OllamaEmbeddingsResult>(it).embedding
                )
            }.onFailure { throwable ->
                error(throwable.message ?: "Error")
            }.getOrThrow()
        }
    }*/

    /*suspend fun getOllamaResponse(
        model: String,
        prompt: String,
        baseUrl: String
    ) {
        httpClient.post("$baseUrl/api/generate") {
            contentType(ContentType.Application.Json)
            setBody(
                OllamaRequest(
                    model = model,
                    prompt = prompt
                )
            )
        }
    }*/

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


