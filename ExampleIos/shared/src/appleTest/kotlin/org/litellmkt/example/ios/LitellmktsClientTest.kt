package org.litellmkt.example.ios

import io.ktor.client.engine.mock.respond
import io.ktor.http.HttpHeaders
import io.ktor.http.HttpStatusCode
import io.ktor.http.headersOf
import io.ktor.utils.io.ByteChannel
import io.ktor.utils.io.writeStringUtf8
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout
import kotlinx.io.IOException
import kotlin.concurrent.AtomicInt
import kotlin.test.AfterTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue

class LitellmktsClientTest {

    private val client = LitellmktsClient(Dispatchers.Default)

    @AfterTest
    fun tearDown() {
        client.cancelAll()
        resetKoin()
    }

    @Test
    fun listModelsReturnsNamesInOrder() = runBlocking {
        configureWith(MockServer { respondJson(OLLAMA_TAGS) })
        val result = CompletableDeferred<List<String>>()

        client.listModels(onSuccess = { result.complete(it) }, onError = { result.completeExceptionally(it) })

        assertEquals(listOf("llama3.1", "qwen2.5"), withTimeout(5_000) { result.await() })
    }

    @Test
    fun chatDeliversNonEmptyTokensThenCompletesOnce() = runBlocking {
        val server = MockServer {
            respondLines(ollamaChatChunk("Hello"), ollamaChatChunk(" world"), ollamaChatChunk("", done = true))
        }
        configureWith(server)
        val tokens = mutableListOf<String>()
        val completions = AtomicInt(0)
        val done = CompletableDeferred<Unit>()

        client.chat(
            model = "llama3.1",
            messages = listOf(
                ChatMessage("user", "Hi"),
                ChatMessage("assistant", "Hello!"),
                ChatMessage("user", "Say hello world")
            ),
            onToken = { tokens += it },
            onComplete = { completions.incrementAndGet(); done.complete(Unit) },
            onError = { done.completeExceptionally(it) }
        )
        withTimeout(5_000) { done.await() }
        delay(100)

        assertEquals(listOf("Hello", " world"), tokens)
        assertEquals(1, completions.value)
        assertEquals("http://llm.test/api/chat", server.requests.single().url.toString())
    }

    @Test
    fun completionCarriesServerStats() = runBlocking {
        configureWith(MockServer {
            respondLines(
                ollamaChatChunk("Hi"),
                ollamaChatChunk("", done = true, extra = ""","eval_count":42,"eval_duration":2000000000""")
            )
        })

        assertEquals(ChatStats(generatedTokens = 42, generationMillis = 2000), chatStats())
    }

    @Test
    fun completionWithoutServerStats() = runBlocking {
        configureWith(MockServer { respondLines(ollamaChatChunk("Hi"), ollamaChatChunk("", done = true)) })

        assertEquals(ChatStats(generatedTokens = null, generationMillis = null), chatStats())
    }

    @Test
    fun httpErrorIsReportedWithStatus() = runBlocking {
        configureWith(MockServer { respondJson("""{"error":"model 'nope' not found"}""", HttpStatusCode.NotFound) })

        val error = chatError(onComplete = { error("completion must not be called") })

        assertEquals(ErrorCategory.HTTP, error.category)
        assertEquals(404, error.httpStatus)
        assertTrue("model 'nope' not found" in error.detail)
    }

    @Test
    fun unreachableServerIsANetworkError() = runBlocking {
        configureWith(MockServer { throw IOException("Connection refused") })

        val error = chatError()

        assertEquals(ErrorCategory.NETWORK, error.category)
        assertNull(error.httpStatus)
    }

    @Test
    fun callsBeforeConfigurationFailWithConfigurationError() = runBlocking {
        resetKoin()
        val result = CompletableDeferred<LitellmktsException>()

        client.listModels(onSuccess = { error("must not succeed") }, onError = { result.complete(it) })

        assertEquals(ErrorCategory.CONFIGURATION, withTimeout(5_000) { result.await() }.category)
    }

    @Test
    fun cancelledChatCallsNothingElse() = runBlocking {
        val body = ByteChannel(autoFlush = true)
        configureWith(MockServer {
            respond(body, HttpStatusCode.OK, headersOf(HttpHeaders.ContentType, "application/x-ndjson"))
        })
        val tokens = mutableListOf<String>()
        val firstToken = CompletableDeferred<Unit>()
        val otherCallbacks = AtomicInt(0)

        val handle = client.chat(
            model = "llama3.1",
            messages = listOf(ChatMessage("user", "Hi")),
            onToken = { tokens += it; firstToken.complete(Unit) },
            onComplete = { otherCallbacks.incrementAndGet() },
            onError = { otherCallbacks.incrementAndGet() }
        )
        body.writeStringUtf8(ollamaChatChunk("Hello") + "\n")
        withTimeout(5_000) { firstToken.await() }

        handle.cancel()
        body.writeStringUtf8(ollamaChatChunk(" world") + "\n" + ollamaChatChunk("", done = true) + "\n")
        body.flushAndClose()
        delay(300)

        assertEquals(listOf("Hello"), tokens)
        assertEquals(0, otherCallbacks.value)
    }

    private suspend fun chatStats(): ChatStats {
        val result = CompletableDeferred<ChatStats>()
        client.chat(
            model = "llama3.1",
            messages = listOf(ChatMessage("user", "Hi")),
            onToken = {},
            onComplete = { result.complete(it) },
            onError = { result.completeExceptionally(it) }
        )
        return withTimeout(5_000) { result.await() }
    }

    private suspend fun chatError(onComplete: (ChatStats) -> Unit = {}): LitellmktsException {
        val result = CompletableDeferred<LitellmktsException>()
        client.chat(
            model = "llama3.1",
            messages = listOf(ChatMessage("user", "Hi")),
            onToken = {},
            onComplete = onComplete,
            onError = { result.complete(it) }
        )
        return withTimeout(5_000) { result.await() }
    }
}
