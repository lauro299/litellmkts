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
import org.litellmkt.handlers.ChatHandler
import org.litellmkt.handlers.internal.decodeProviderChunk
import org.litellmkt.handlers.internal.ensureSuccess
import org.litellmkt.handlers.internal.forEachLine
import org.litellmkt.params.BaseParamsModel
import org.litellmkt.params.Message
import org.litellmkt.params.boolean
import org.litellmkt.params.messages
import org.litellmkt.params.numberOrString
import org.litellmkt.params.requireModel
import org.litellmkt.params.string
import org.litellmkt.results.BaseResultModel

class OllamaChatHandler(
    private val parser: Json,
    private val baseUrl: String,
    private val httpClient: HttpClient
) : ChatHandler {
    override fun chat(params: BaseParamsModel): Flow<BaseResultModel> {
        return flow {
            val request = params.toOllamaChat()
            httpClient.preparePost(
                urlString = "${baseUrl}/api/chat"
            ) {
                contentType(Application.Json)
                setBody(request)
            }.execute { httpResponse ->
                httpResponse.ensureSuccess()
                httpResponse.body<ByteReadChannel>().forEachLine { line ->
                    emit(parser.decodeProviderChunk<HandlerOllamaResultChatModel>(line).toBaseResultModel())
                }
            }
        }
    }

}

internal fun HandlerOllamaResultChatModel.toBaseResultModel(): BaseResultModel {
    return BaseResultModel(
        model = model,
        createdAt = createdAt ?: "",
        response = message?.content ?: "",
        done = done,
        totalDuration = totalDuration,
        loadDuration = loadDuration,
        promptEvalCount = promptEvalCount,
        promptEvalDuration = promptEvalDuration,
        evalCount = evalCount,
        evalDuration = evalDuration,
        message = message
    )
}

internal fun BaseParamsModel.toOllamaChat(): RequestOllamaChat {
    return RequestOllamaChat(
        model = requireModel(),
        messages = messages(),
        stream = boolean("stream"),
        format = string("format"),
        options = toOllamaOptions(),
        keepAlive = numberOrString("keep_alive")
    )
}

@Serializable
internal data class RequestOllamaChat(
    val model: String,
    val messages: List<Message>?,
    val stream: Boolean? = null,
    val format: String? = null,
    val options: HandlerParamsOllamaOptions? = null,
    @SerialName("keep_alive")
    val keepAlive: JsonPrimitive? = null
)

@Serializable
internal data class HandlerOllamaResultChatModel(
    val model: String,
    @SerialName("created_at")
    val createdAt: String? = null,
    val message: Message? = null,
    val done: Boolean = false,
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
