package org.litellmkt.handlers

import io.ktor.client.HttpClient
import kotlinx.serialization.json.Json
import org.koin.core.annotation.Named
import org.koin.core.annotation.Singleton
import org.litellmkt.handlers.ollama.OllamaChatHandler
import org.litellmkt.handlers.ollama.OllamaEmbeddingHandler
import org.litellmkt.handlers.ollama.OllamaGenerateGenerationHandler
import org.litellmkt.handlers.openAi.OpenAiChatHandler
import org.litellmkt.handlers.openAi.OpenAiEmbeddingHandler
import org.litellmkt.handlers.openAi.OpenAiGenerationHandler
import org.litellmkt.types.LLMProvider

/**
 *The HandlerFactory class is a factory that creates instances of different types of handlers based on the provided
 *instance string. It has three methods for creating chat, embedding, and generation handlers. The factory uses
 *dependency injection to provide the required dependencies to the handler constructors.
 */
@Singleton
class HandlerFactory(
    private val parser: Json,
    @Named("baseUrl") private val baseUrl: String,
    private val httpClient: HttpClient,
    @Named("apiKey") private val apiKey: String? = null
) {
    fun createChatHandler(instance: String): ChatHandler {
        return createChatHandler(parseProvider(instance))
    }
    
    fun createChatHandler(provider: LLMProvider): ChatHandler {
        return when (provider) {
            is LLMProvider.Ollama -> OllamaChatHandler(
                parser = parser,
                baseUrl = baseUrl,
                httpClient = httpClient
            )
            is LLMProvider.OpenAI -> OpenAiChatHandler(
                parser = parser,
                baseUrl = baseUrl,
                httpClient = httpClient,
                apiKey = apiKey ?: throw IllegalArgumentException("API key required for OpenAI")
            )
            is LLMProvider.Custom -> throw IllegalArgumentException("Custom provider not yet supported: ${provider.name}")
        }
    }

    fun createEmbeddingHandler(instance: String): EmbeddingHandler {
        return createEmbeddingHandler(parseProvider(instance))
    }
    
    fun createEmbeddingHandler(provider: LLMProvider): EmbeddingHandler {
        return when (provider) {
            is LLMProvider.Ollama -> OllamaEmbeddingHandler(
                baseUrl = baseUrl,
                httpClient = httpClient
            )
            is LLMProvider.OpenAI -> OpenAiEmbeddingHandler(
                baseUrl = baseUrl,
                httpClient = httpClient,
                apiKey = apiKey ?: throw IllegalArgumentException("API key required for OpenAI")
            )
            is LLMProvider.Custom -> throw IllegalArgumentException("Custom provider not yet supported: ${provider.name}")
        }
    }

    fun createGenerationHandler(instance: String): GenerationHandler {
        return createGenerationHandler(parseProvider(instance))
    }
    
    fun createGenerationHandler(provider: LLMProvider): GenerationHandler {
        return when (provider) {
            is LLMProvider.Ollama -> OllamaGenerateGenerationHandler(
                parser = parser,
                baseUrl = baseUrl,
                httpClient = httpClient
            )
            is LLMProvider.OpenAI -> OpenAiGenerationHandler(
                parser = parser,
                baseUrl = baseUrl,
                httpClient = httpClient,
                apiKey = apiKey ?: throw IllegalArgumentException("API key required for OpenAI")
            )
            is LLMProvider.Custom -> throw IllegalArgumentException("Custom provider not yet supported: ${provider.name}")
        }
    }
    
    private fun parseProvider(instance: String): LLMProvider {
        return when (instance.lowercase()) {
            "ollama" -> LLMProvider.Ollama
            "openai" -> LLMProvider.OpenAI
            else -> LLMProvider.Custom(instance)
        }
    }
}