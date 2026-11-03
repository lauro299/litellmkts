package org.litellmkt.network

import io.kotest.assertions.throwables.shouldThrowAny
import io.kotest.core.spec.style.FunSpec
import io.kotest.matchers.collections.shouldBeEmpty
import io.kotest.matchers.collections.shouldHaveSize
import io.kotest.matchers.shouldBe
import io.kotest.matchers.string.shouldContain
import io.kotest.matchers.string.shouldContainIgnoringCase
import io.kotest.matchers.string.shouldNotContain
import io.ktor.client.HttpClient
import io.ktor.client.engine.okhttp.OkHttp
import io.ktor.client.plugins.logging.LogLevel
import io.ktor.client.plugins.logging.Logger
import kotlinx.coroutines.flow.toList
import org.litellmkt.BASE_URL
import org.litellmkt.MockedClient
import org.litellmkt.handlers.ollama.OllamaGenerateGenerationHandler
import org.litellmkt.handlers.openAi.OpenAiChatHandler
import org.litellmkt.ollamaGenerateChunk
import org.litellmkt.params
import org.litellmkt.params.Message
import org.litellmkt.respondJson
import org.litellmkt.respondLines
import org.litellmkt.testJson
import java.net.ServerSocket
import kotlin.concurrent.thread

class HttpTransportTest : FunSpec({

    val generateParams = params("model" to "llama3", "prompt" to "hi")

    test("unknown response fields are ignored") {
        val mocked = MockedClient { respondLines(ollamaGenerateChunk("ok", done = true, extra = ""","brand_new_field":{"a":1}""")) }
        val results = OllamaGenerateGenerationHandler(testJson, BASE_URL, mocked.client).stream(generateParams).toList()
        results.single().response shouldBe "ok"
    }

    test("null parameters are not serialized") {
        val mocked = MockedClient { respondLines(ollamaGenerateChunk("ok", done = true)) }
        OllamaGenerateGenerationHandler(testJson, BASE_URL, mocked.client).stream(generateParams).toList()
        mocked.lastBody().keys shouldBe setOf("model", "prompt")
    }

    test("logging is off by default") {
        val logs = mutableListOf<String>()
        val mocked = MockedClient(LitellmktConfig(logger = CapturingLogger(logs))) {
            respondLines(ollamaGenerateChunk("ok", done = true))
        }
        OllamaGenerateGenerationHandler(testJson, BASE_URL, mocked.client).stream(generateParams).toList()
        logs.shouldBeEmpty()
    }

    test("the Authorization header is masked when logging is enabled") {
        val logs = mutableListOf<String>()
        val mocked = MockedClient(LitellmktConfig(logLevel = LogLevel.ALL, logger = CapturingLogger(logs))) {
            respondJson("""{"id":"c1","object":"chat.completion","created":1,"model":"gpt-4o","choices":[{"index":0,"message":{"role":"assistant","content":"hi"},"finish_reason":"stop"}]}""")
        }
        OpenAiChatHandler(testJson, BASE_URL, mocked.client, apiKey = "sk-secret-123")
            .chat(params("model" to "gpt-4o", "stream" to false, "messages" to listOf(Message("user", "hi"))))
            .toList()
        val output = logs.joinToString("\n")
        output shouldContain "Authorization"
        output shouldNotContain "sk-secret-123"
    }

    test("a stream lasting longer than 10 seconds is not cut off") {
        val chunks = 11
        val port = streamingServer(chunks = chunks, delayBetweenChunksMillis = 1_000)
        val client = HttpClient(OkHttp) { applyLitellmktDefaults(testJson, LitellmktConfig()) }
        val results = OllamaGenerateGenerationHandler(testJson, "http://127.0.0.1:$port", client)
            .stream(generateParams)
            .toList()
        results shouldHaveSize chunks
    }

    test("the configured inactivity timeout fails a stalled stream") {
        val port = streamingServer(chunks = 2, delayBetweenChunksMillis = 3_000)
        val client = HttpClient(OkHttp) {
            applyLitellmktDefaults(testJson, LitellmktConfig(socketTimeoutMillis = 1_000))
        }
        val error = shouldThrowAny {
            OllamaGenerateGenerationHandler(testJson, "http://127.0.0.1:$port", client).stream(generateParams).toList()
        }
        (error.message ?: error::class.simpleName.orEmpty()) shouldContainIgnoringCase "timeout"
    }
})

private class CapturingLogger(private val logs: MutableList<String>) : Logger {
    override fun log(message: String) {
        logs += message
    }
}

/**
 * Serves a single NDJSON response that writes [chunks] lines, waiting [delayBetweenChunksMillis] before each one.
 * Returns the port.
 */
private fun streamingServer(chunks: Int, delayBetweenChunksMillis: Long): Int {
    val server = ServerSocket(0)
    thread(isDaemon = true) {
        server.use {
            server.accept().use { socket ->
                val reader = socket.getInputStream().bufferedReader()
                var header = reader.readLine() // request line, then headers until the blank line
                while (!header.isNullOrEmpty()) header = reader.readLine()
                val out = socket.getOutputStream()
                out.write("HTTP/1.1 200 OK\r\nContent-Type: application/x-ndjson\r\nConnection: close\r\n\r\n".toByteArray())
                out.flush()
                runCatching {
                    repeat(chunks) { index ->
                        Thread.sleep(delayBetweenChunksMillis)
                        out.write((ollamaGenerateChunk("t$index", done = index == chunks - 1) + "\n").toByteArray())
                        out.flush()
                    }
                }
            }
        }
    }
    return server.localPort
}
