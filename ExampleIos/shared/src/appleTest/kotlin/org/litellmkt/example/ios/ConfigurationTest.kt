package org.litellmkt.example.ios

import io.ktor.http.HttpStatusCode
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout
import kotlin.test.AfterTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertTrue

class ConfigurationTest {

    private val server = MockServer { respondJson(OLLAMA_TAGS) }

    @AfterTest
    fun tearDown() = resetKoin()

    @Test
    fun callsGoToTheConfiguredServer() = runBlocking {
        configureWith(server, baseUrl = "http://server-a.test")

        listModels()

        assertEquals("http://server-a.test/api/tags", server.requests.single().url.toString())
    }

    @Test
    fun reconfiguringSwitchesServer() = runBlocking {
        configureWith(server, baseUrl = "http://server-a.test")
        configureWith(server, baseUrl = "http://server-b.test")

        listModels()

        assertTrue(server.requests.all { it.url.host == "server-b.test" })
        assertEquals(1, server.requests.size)
    }

    @Test
    fun openAiWorksWithoutApiKey() = runBlocking {
        val server = MockServer { respondJson(OPENAI_MODELS) }
        configureWith(server, provider = "openai")

        assertEquals(listOf("/models/Qwen3-8B.gguf"), listModels())
        assertEquals("http://llm.test/v1/models", server.requests.single().url.toString())
    }

    @Test
    fun rejectedKeyIsAnHttpError() = runBlocking {
        configureWith(MockServer { respondJson("""{"error":{"message":"Invalid API Key"}}""", HttpStatusCode.Unauthorized) }, provider = "openai")
        val result = CompletableDeferred<LitellmktsException>()

        LitellmktsClient(Dispatchers.Default).listModels(onSuccess = {}, onError = { result.complete(it) })

        val error = withTimeout(5_000) { result.await() }
        assertEquals(ErrorCategory.HTTP, error.category)
        assertEquals(401, error.httpStatus)
    }

    @Test
    fun unknownProviderIsAConfigurationError() {
        val error = assertFailsWith<LitellmktsException> {
            configureLitellmkts("http://llm.test", apiKey = null, provider = "gemini")
        }

        assertEquals(ErrorCategory.CONFIGURATION, error.category)
        assertTrue("gemini" in error.detail)
    }

    private suspend fun listModels(): List<String> {
        val result = CompletableDeferred<List<String>>()
        LitellmktsClient(Dispatchers.Default).listModels(
            onSuccess = { result.complete(it) },
            onError = { result.completeExceptionally(it) }
        )
        return withTimeout(5_000) { result.await() }
    }
}
