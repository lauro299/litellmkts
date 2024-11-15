package org.litellmkt.handlers.ollama

import io.ktor.client.HttpClient
import io.ktor.client.call.body
import io.ktor.client.request.post
import io.ktor.client.request.setBody
import io.ktor.http.ContentType
import io.ktor.http.contentType
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow
import org.koin.core.annotation.Named
import org.koin.core.annotation.Singleton
import org.litellmkt.handlers.HandlerEmbeddings
import org.litellmkt.handlers.params.HandlerEmbeddingsOllamaParams
import org.litellmkt.handlers.results.HandlerResultOllamaEmbeddings

@Singleton
class OllamaEmbeddingsHandler(
    @Named("baseUrl") private val baseUrl: String,
    private val httpClient: HttpClient
) : HandlerEmbeddings<HandlerEmbeddingsOllamaParams, HandlerResultOllamaEmbeddings> {

    override fun embeddings(params: HandlerEmbeddingsOllamaParams): Flow<HandlerResultOllamaEmbeddings> {
        return flow {
            emit(
                httpClient.post(
                    urlString = "$baseUrl/api/embed"
                ) {
                    contentType(ContentType.Application.Json)
                    setBody(params)
                }.body()
            )
        }
    }

}