package org.litellmkt.handlers.openAi

import io.ktor.client.HttpClient
import io.ktor.client.call.body
import io.ktor.client.request.header
import io.ktor.client.request.preparePost
import io.ktor.client.request.setBody
import io.ktor.http.ContentType.Application
import io.ktor.http.HttpHeaders
import io.ktor.http.contentType
import io.ktor.utils.io.ByteReadChannel
import io.ktor.utils.io.readUTF8Line
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import org.litellmkt.handlers.GenerationHandler
import org.litellmkt.params.BaseParamsModel
import org.litellmkt.results.BaseResultModel

class OpenAiGenerationHandler(
    private val parser: Json,
    private val baseUrl: String,
    private val httpClient: HttpClient,
    private val apiKey: String
) : GenerationHandler {
    override fun stream(params: BaseParamsModel): Flow<BaseResultModel> {
        return flow {
            httpClient.preparePost(
                "${baseUrl}/v1/completions"
            ) {
                contentType(Application.Json)
                header(HttpHeaders.Authorization, "Bearer $apiKey")
                setBody(params.toOpenAiCompletion())
            }.execute { httpResponse ->
                val channel: ByteReadChannel = httpResponse.body()
                while (!channel.isClosedForRead) {
                    channel.readUTF8Line()
                        ?.takeIf { it.startsWith("data: ") }
                        ?.removePrefix("data: ")
                        ?.takeIf { it != "[DONE]" }
                        ?.runCatching {
                            parser.decodeFromString<OpenAiCompletionResponse>(this)
                        }?.onFailure { error ->
                            error(StreamError(error = error.message ?: ""))
                        }?.getOrThrow()
                        ?.let {
                            emit(it.toBaseResultModel())
                        }
                }
            }
        }
    }
}

internal fun OpenAiCompletionResponse.toBaseResultModel(): BaseResultModel {
    val choice = choices?.firstOrNull()
    return BaseResultModel(
        model = model,
        createdAt = created.toString(),
        response = choice?.text ?: "",
        done = choice?.finishReason != null
    )
}

internal fun BaseParamsModel.toOpenAiCompletion(): OpenAiCompletionRequest {
    return OpenAiCompletionRequest(
        model = model ?: throw IllegalArgumentException("Invalid Model"),
        prompt = get("prompt") as? String ?: throw IllegalArgumentException("Invalid prompt"),
        stream = stream ?: true,
        temperature = get("temperature") as? Double,
        maxTokens = get("max_tokens") as? Int,
        topP = get("top_p") as? Double,
        frequencyPenalty = get("frequency_penalty") as? Double,
        presencePenalty = get("presence_penalty") as? Double,
        stop = get("stop") as? List<String>,
        suffix = get("suffix") as? String
    )
}

@Serializable
internal data class OpenAiCompletionRequest(
    val model: String,
    val prompt: String,
    val stream: Boolean = true,
    val temperature: Double? = null,
    @SerialName("max_tokens")
    val maxTokens: Int? = null,
    @SerialName("top_p")
    val topP: Double? = null,
    @SerialName("frequency_penalty")
    val frequencyPenalty: Double? = null,
    @SerialName("presence_penalty")
    val presencePenalty: Double? = null,
    val stop: List<String>? = null,
    val suffix: String? = null
)

@Serializable
internal data class OpenAiCompletionResponse(
    val id: String,
    @SerialName("object")
    val objectType: String,
    val created: Long,
    val model: String,
    val choices: List<OpenAiCompletionChoice>?
)

@Serializable
internal data class OpenAiCompletionChoice(
    val text: String? = null,
    val index: Int,
    @SerialName("finish_reason")
    val finishReason: String? = null
)