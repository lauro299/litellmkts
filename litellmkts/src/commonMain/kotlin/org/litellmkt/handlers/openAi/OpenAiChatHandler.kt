package org.litellmkt.handlers.openAi

import io.ktor.client.HttpClient
import io.ktor.client.call.body
import io.ktor.client.request.header
import io.ktor.client.request.preparePost
import io.ktor.client.request.setBody
import io.ktor.client.statement.bodyAsText
import io.ktor.http.ContentType.Application
import io.ktor.http.HttpHeaders
import io.ktor.http.contentType
import io.ktor.utils.io.ByteReadChannel
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import org.litellmkt.handlers.ChatHandler
import org.litellmkt.handlers.internal.decodeProviderChunk
import org.litellmkt.handlers.internal.ensureSuccess
import org.litellmkt.handlers.internal.forEachLine
import org.litellmkt.handlers.internal.sseData
import org.litellmkt.params.BaseParamsModel
import org.litellmkt.params.Message
import org.litellmkt.params.boolean
import org.litellmkt.params.double
import org.litellmkt.params.int
import org.litellmkt.params.messages
import org.litellmkt.params.requireModel
import org.litellmkt.params.string
import org.litellmkt.params.stringList
import org.litellmkt.results.BaseResultModel

class OpenAiChatHandler(
    private val parser: Json,
    private val baseUrl: String,
    private val httpClient: HttpClient,
    private val apiKey: String
) : ChatHandler {
    override fun chat(params: BaseParamsModel): Flow<BaseResultModel> {
        return flow {
            val request = params.toOpenAiChat()
            httpClient.preparePost(
                urlString = "${baseUrl}/v1/chat/completions"
            ) {
                contentType(Application.Json)
                header(HttpHeaders.Authorization, "Bearer $apiKey")
                setBody(request)
            }.execute { httpResponse ->
                httpResponse.ensureSuccess()
                if (request.stream) {
                    httpResponse.body<ByteReadChannel>().forEachLine { line ->
                        val data = sseData(line) ?: return@forEachLine
                        emit(parser.decodeProviderChunk<OpenAiChatResponse>(data).toBaseResultModel())
                    }
                } else {
                    emit(
                        parser.decodeProviderChunk<OpenAiChatResponse>(httpResponse.bodyAsText())
                            .toBaseResultModel(complete = true)
                    )
                }
            }
        }
    }
}

/**
 * @param complete true for a non-streamed response, which is always the final result.
 */
internal fun OpenAiChatResponse.toBaseResultModel(complete: Boolean = false): BaseResultModel {
    val choice = choices?.firstOrNull()
    val content = choice?.delta ?: choice?.message
    return BaseResultModel(
        model = model,
        createdAt = created.toString(),
        response = content?.content ?: "",
        done = complete || choice?.finishReason != null,
        promptEvalCount = usage?.promptTokens,
        evalCount = usage?.completionTokens,
        message = content?.let {
            Message(
                role = it.role ?: "assistant",
                content = it.content
            )
        }
    )
}

internal fun BaseParamsModel.toOpenAiChat(): OpenAiChatRequest {
    return OpenAiChatRequest(
        model = requireModel(),
        messages = messages() ?: throw IllegalArgumentException("Missing required parameter 'messages'"),
        stream = boolean("stream") ?: true,
        temperature = double("temperature"),
        maxTokens = int("max_tokens"),
        topP = double("top_p"),
        frequencyPenalty = double("frequency_penalty"),
        presencePenalty = double("presence_penalty"),
        stop = stringList("stop"),
        user = string("user")
    )
}

@Serializable
internal data class OpenAiChatRequest(
    val model: String,
    val messages: List<Message>,
    // No default value: encodeDefaults = false would otherwise drop "stream": true from the body.
    val stream: Boolean,
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
    val user: String? = null
)

@Serializable
internal data class OpenAiChatResponse(
    val id: String,
    @SerialName("object")
    val objectType: String,
    val created: Long,
    val model: String,
    val choices: List<OpenAiChoice>?,
    val usage: OpenAiUsage? = null
)

@Serializable
internal data class OpenAiChoice(
    val index: Int,
    /** Streamed chunk content. */
    val delta: OpenAiDelta? = null,
    /** Non-streamed response content. */
    val message: OpenAiDelta? = null,
    @SerialName("finish_reason")
    val finishReason: String? = null
)

@Serializable
internal data class OpenAiDelta(
    val role: String? = null,
    val content: String? = null
)

@Serializable
internal data class OpenAiUsage(
    @SerialName("prompt_tokens")
    val promptTokens: Int? = null,
    @SerialName("completion_tokens")
    val completionTokens: Int? = null,
    @SerialName("total_tokens")
    val totalTokens: Int? = null
)
