package org.litellmkt.example.ios

import io.ktor.client.HttpClient
import org.koin.core.context.startKoin
import org.koin.core.context.stopKoin
import org.koin.core.module.Module
import org.koin.core.qualifier.named
import org.koin.dsl.module
import org.koin.mp.KoinPlatformTools
import org.litellmkt.di.getLitellmktModule
import org.litellmkt.handlers.HandlerFactory
import org.litellmkt.types.LLMProvider

/**
 * Starts Koin with the litellmkts module for the given server. Call it at app launch and again whenever the server
 * changes: the previous configuration, and its HTTP client, are discarded. Cancel running calls before calling it.
 *
 * Swift: `try ConfigurationKt.configureLitellmkts(baseUrl:apiKey:provider:)`.
 *
 * @param baseUrl Ollama (`http://host:11434`) or OpenAI-compatible server URL.
 * @param apiKey optional; only the `openai` provider sends it. Local OpenAI-compatible servers (llama.cpp, LM Studio)
 *   need none.
 * @param provider `ollama` or `openai`.
 * @throws LitellmktsException with [ErrorCategory.CONFIGURATION] when [provider] is not supported.
 */
@Throws(LitellmktsException::class)
fun configureLitellmkts(baseUrl: String, apiKey: String?, provider: String) {
    configureLitellmkts(baseUrl, apiKey, provider, extraModules = emptyList())
}

/** [extraModules] override library definitions, e.g. the HTTP engine in tests. */
internal fun configureLitellmkts(baseUrl: String, apiKey: String?, provider: String, extraModules: List<Module>) {
    val llmProvider = parseProvider(provider)
    // HandlerFactory requires a key for OpenAI; an empty one works with servers that do not check it.
    val key = apiKey ?: "".takeIf { llmProvider == LLMProvider.OpenAI }
    KoinPlatformTools.defaultContext().getOrNull()?.let { koin ->
        // The library declares the client without onClose, so stopKoin() alone would leave it open.
        koin.getOrNull<HttpClient>()?.close()
        stopKoin()
    }
    startKoin {
        allowOverride(true)
        modules(
            listOf(
                getLitellmktModule(),
                module {
                    single(named("baseUrl")) { baseUrl }
                    // Koin Annotations skips constructor parameters with default values, so the library's own
                    // HandlerFactory definition never receives the API key; this one replaces it.
                    single { HandlerFactory(parser = get(), baseUrl = baseUrl, httpClient = get(), apiKey = key) }
                    single<LLMProvider> { llmProvider }
                }
            ) + extraModules
        )
    }
}

private fun parseProvider(provider: String): LLMProvider = when (provider.lowercase()) {
    "ollama" -> LLMProvider.Ollama
    "openai" -> LLMProvider.OpenAI
    else -> throw LitellmktsException(ErrorCategory.CONFIGURATION, null, "Unsupported provider: $provider")
}
