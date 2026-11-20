package org.litellmkt.example.ios

import io.ktor.client.engine.HttpClientEngine
import io.ktor.client.engine.HttpClientEngineFactory
import io.ktor.client.engine.mock.MockEngine
import io.ktor.client.engine.mock.MockEngineConfig
import io.ktor.client.engine.mock.MockRequestHandler
import io.ktor.client.engine.mock.respond
import io.ktor.client.engine.mock.MockRequestHandleScope
import io.ktor.client.request.HttpRequestData
import io.ktor.http.HttpHeaders
import io.ktor.http.HttpStatusCode
import io.ktor.http.headersOf
import io.ktor.utils.io.ByteReadChannel
import org.koin.core.context.stopKoin
import org.koin.dsl.module
import org.litellmkt.network.HttpClientLocalEngine

/** Records every request sent through the engines it creates. */
internal class MockServer(private val handler: MockRequestHandler) {
    val requests = mutableListOf<HttpRequestData>()

    private val factory = object : HttpClientEngineFactory<MockEngineConfig> {
        override fun create(block: MockEngineConfig.() -> Unit): HttpClientEngine =
            MockEngine(MockEngineConfig().apply(block).apply {
                addHandler { request ->
                    requests += request
                    handler(request)
                }
            })
    }

    /** Koin module that replaces the platform engine with this server. */
    val module = module {
        single<HttpClientLocalEngine> {
            object : HttpClientLocalEngine {
                override fun invoke(): HttpClientEngineFactory<*> = factory
            }
        }
    }
}

internal fun configureWith(server: MockServer, baseUrl: String = "http://llm.test", provider: String = "ollama") =
    configureLitellmkts(baseUrl, apiKey = null, provider = provider, extraModules = listOf(server.module))

internal fun resetKoin() = stopKoin()

internal fun MockRequestHandleScope.respondLines(vararg lines: String, status: HttpStatusCode = HttpStatusCode.OK) =
    respond(
        content = ByteReadChannel(lines.joinToString(separator = "\n", postfix = "\n")),
        status = status,
        headers = headersOf(HttpHeaders.ContentType, "application/x-ndjson")
    )

internal fun MockRequestHandleScope.respondJson(body: String, status: HttpStatusCode = HttpStatusCode.OK) =
    respond(content = body, status = status, headers = headersOf(HttpHeaders.ContentType, "application/json"))

internal fun ollamaChatChunk(content: String, done: Boolean = false, extra: String = "") =
    """{"model":"llama3.1","created_at":"2024-01-01T00:00:00Z","message":{"role":"assistant","content":"$content"},"done":$done$extra}"""

internal const val OPENAI_MODELS = """{"object":"list","data":[{"id":"/models/Qwen3-8B.gguf","object":"model","created":0,"owned_by":"llamacpp"}]}"""

internal const val OLLAMA_TAGS = """{"models":[{"name":"llama3.1"},{"name":"qwen2.5"}]}"""
