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
import kotlinx.serialization.json.Json
import org.koin.core.annotation.Named
import org.koin.core.annotation.Singleton
import org.litellmkt.handlers.HandlerChat
import org.litellmkt.handlers.results.HandlerOllamaResultChatModel
import org.litellmkt.handlers.params.HandlerParamsChatOllama

@Singleton
class OllamaChatHandler(
    private val parser: Json,
    @Named("baseUrl") private val baseUrl: String,
    private val httpClient: HttpClient
) : HandlerChat<HandlerParamsChatOllama, HandlerOllamaResultChatModel> {
    override fun chat(params: HandlerParamsChatOllama): Flow<HandlerOllamaResultChatModel> {
        return flow {
            httpClient.preparePost(
                urlString = "${baseUrl}/api/chat"
            ) {
                contentType(ContentType.Application.Json)
                setBody(params)
            }.execute { httpResponse ->
                val channel: ByteReadChannel = httpResponse.body()
                while (!channel.isClosedForRead) {
                    channel.readUTF8Line()
                        ?.runCatching {
                            parser.decodeFromString<HandlerOllamaResultChatModel>(this)
                        }?.onFailure {
                            error(StreamError(error = it.message ?: ""))
                        }?.getOrThrow()
                        ?.let {
                            emit(it)
                        }
                }
            }
        }
    }

}