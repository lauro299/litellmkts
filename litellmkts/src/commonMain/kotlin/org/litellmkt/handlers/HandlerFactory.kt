package org.litellmkt.handlers

import io.ktor.client.HttpClient
import kotlinx.serialization.json.Json
import org.koin.core.annotation.Named
import org.koin.core.annotation.Singleton
import org.litellmkt.handlers.ollama.OllamaChatHandler
import org.litellmkt.handlers.ollama.OllamaEmbeddingHandler
import org.litellmkt.handlers.ollama.OllamaGenerateGenerationHandler

/**
 *The HandlerFactory class is a factory that creates instances of different types of handlers based on the provided
 *instance string. It has three methods for creating chat, embedding, and generation handlers. The factory uses
 *dependency injection to provide the required dependencies to the handler constructors.
 */
@Singleton
class HandlerFactory(
    private val parser: Json,
    @Named("baseUrl") private val baseUrl: String,
    private val httpClient: HttpClient
) {
    fun createChatHandler(instance: String): ChatHandler {
        return when (instance) {
            "ollama" -> OllamaChatHandler(
                parser = parser,
                baseUrl = baseUrl,
                httpClient = httpClient
            )

            else -> throw IllegalArgumentException("Invalid name: $instance")
        }

    }

    fun createEmbeddingHandler(instance: String): EmbeddingHandler {
        return when (instance) {
            "ollama" -> OllamaEmbeddingHandler(
                baseUrl = baseUrl,
                httpClient = httpClient
            )

            else -> throw IllegalArgumentException("Invalid name: $instance")
        }
    }

    fun createGenerationHandler(instance: String): GenerationHandler {
        return when (instance) {
            "ollama" -> OllamaGenerateGenerationHandler(
                parser = parser,
                baseUrl = baseUrl,
                httpClient = httpClient
            )

            else -> throw IllegalArgumentException("Invalid name: $instance")
        }
    }
}