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
import org.litellmkt.handlers.ChatHandler
import org.litellmkt.params.BaseParamsModel
import org.litellmkt.params.Message
import org.litellmkt.results.BaseResultModel

class OpenAiChatHandler(
    private val parser: Json,
    private val baseUrl: String,
    private val httpClient: HttpClient,
    private val apiKey: String
) : ChatHandler {
    override fun chat(params: BaseParamsModel): Flow<BaseResultModel> {
        return flow {
            httpClient.preparePost(
                urlString = "${baseUrl}/v1/chat/completions"
            ) {
                contentType(Application.Json)
                header(HttpHeaders.Authorization, "Bearer $apiKey")
                setBody(params.toOpenAiChat())
            }.execute { httpResponse ->
                val channel: ByteReadChannel = httpResponse.body()
                while (!channel.isClosedForRead) {
                    channel.readUTF8Line()
                        ?.takeIf { it.startsWith("data: ") }
                        ?.removePrefix("data: ")
                        ?.takeIf { it != "[DONE]" }
                        ?.runCatching {
                            parser.decodeFromString<OpenAiChatResponse>(this)
                        }?.onFailure {
                            error(StreamError(error = it.message ?: ""))
                        }?.getOrThrow()
                        ?.let {
                            emit(it.toBaseResultModel())
                        }
                }
            }
        }
    }
}

internal fun OpenAiChatResponse.toBaseResultModel(): BaseResultModel {
    val choice = choices?.firstOrNull()
    val delta = choice?.delta
    return BaseResultModel(
        model = model,
        createdAt = created.toString(),
        response = delta?.content ?: "",
        done = choice?.finishReason != null,
        message = delta?.let { 
            Message(
                role = it.role ?: "assistant",
                content = it.content
            )
        }
    )
}

internal fun BaseParamsModel.toOpenAiChat(): OpenAiChatRequest {
    return OpenAiChatRequest(
        model = model ?: throw IllegalArgumentException("Invalid Model"),
        messages = get("messages") as? List<Message> ?: throw IllegalArgumentException("Invalid messages"),
        stream = stream ?: true,
        temperature = get("temperature") as? Double,
        maxTokens = get("max_tokens") as? Int,
        topP = get("top_p") as? Double,
        frequencyPenalty = get("frequency_penalty") as? Double,
        presencePenalty = get("presence_penalty") as? Double,
        stop = get("stop") as? List<String>
    )
}

@Serializable
internal data class OpenAiChatRequest(
    val model: String,
    val messages: List<Message>,
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
    val stop: List<String>? = null
)

@Serializable
internal data class OpenAiChatResponse(
    val id: String,
    @SerialName("object")
    val objectType: String,
    val created: Long,
    val model: String,
    val choices: List<OpenAiChoice>?
)

@Serializable
internal data class OpenAiChoice(
    val index: Int,
    val delta: OpenAiDelta? = null,
    @SerialName("finish_reason")
    val finishReason: String? = null
)

@Serializable
internal data class OpenAiDelta(
    val role: String? = null,
    val content: String? = null
)

internal data class StreamError(
    val error: String
)