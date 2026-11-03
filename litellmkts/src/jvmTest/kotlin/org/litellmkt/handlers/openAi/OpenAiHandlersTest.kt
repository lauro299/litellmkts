package org.litellmkt.handlers.openAi

import io.kotest.assertions.throwables.shouldThrow
import io.kotest.core.spec.style.FunSpec
import io.kotest.matchers.nulls.shouldBeNull
import io.kotest.matchers.shouldBe
import io.ktor.http.HttpHeaders
import io.ktor.http.HttpStatusCode
import kotlinx.coroutines.flow.toList
import kotlinx.serialization.json.boolean
import kotlinx.serialization.json.jsonPrimitive
import org.litellmkt.BASE_URL
import org.litellmkt.MockedClient
import org.litellmkt.errors.ProviderHttpException
import org.litellmkt.handlers.HandlerFactory
import org.litellmkt.openAiChatChunk
import org.litellmkt.params
import org.litellmkt.params.Message
import org.litellmkt.respondJson
import org.litellmkt.respondLines
import org.litellmkt.testJson
import org.litellmkt.types.LLMProvider

class OpenAiHandlersTest : FunSpec({

    val messages = listOf(Message("user", "hi"))
    val chatParams = params("model" to "gpt-4o", "messages" to messages)

    fun sseMock() = MockedClient { respondLines("data: " + openAiChatChunk("ok", "stop"), "data: [DONE]") }

    test("chat sends stream=true by default") {
        val mocked = sseMock()
        OpenAiChatHandler(testJson, BASE_URL, mocked.client, "sk").chat(chatParams).toList()
        mocked.lastBody()["stream"]!!.jsonPrimitive.boolean shouldBe true
    }

    test("completion sends stream=false when requested") {
        val mocked = MockedClient {
            respondJson("""{"id":"x","object":"text_completion","created":1,"model":"gpt-3.5-turbo-instruct","choices":[{"index":0,"text":"42","finish_reason":"stop"}]}""")
        }
        OpenAiGenerationHandler(testJson, BASE_URL, mocked.client, "sk")
            .stream(params("model" to "gpt-3.5-turbo-instruct", "prompt" to "q", "stream" to false))
            .toList()
        mocked.lastBody()["stream"]!!.jsonPrimitive.boolean shouldBe false
    }

    test("SSE chunks are emitted one by one and only the last one is done") {
        val mocked = MockedClient {
            respondLines(
                ": keep-alive",
                "data: " + openAiChatChunk("Hola"),
                "",
                "data:" + openAiChatChunk(" mundo"),
                "data: " + openAiChatChunk(null, "stop"),
                "data: [DONE]"
            )
        }
        val results = OpenAiChatHandler(testJson, BASE_URL, mocked.client, "sk").chat(chatParams).toList()
        results.map { it.response } shouldBe listOf("Hola", " mundo", "")
        results.map { it.done } shouldBe listOf(false, false, true)
    }

    test("a non-streamed chat response is a single done result") {
        val mocked = MockedClient {
            respondJson("""{"id":"c1","object":"chat.completion","created":1,"model":"gpt-4o","choices":[{"index":0,"message":{"role":"assistant","content":"Hola"},"finish_reason":"stop"}]}""")
        }
        val results = OpenAiChatHandler(testJson, BASE_URL, mocked.client, "sk")
            .chat(params("model" to "gpt-4o", "messages" to messages, "stream" to false))
            .toList()
        results.size shouldBe 1
        results.single().response shouldBe "Hola"
        results.single().done shouldBe true
    }

    test("a non-streamed completion response is a single done result") {
        val mocked = MockedClient {
            respondJson("""{"id":"x","object":"text_completion","created":1,"model":"m","choices":[{"index":0,"text":"42","finish_reason":"length"}]}""")
        }
        val results = OpenAiGenerationHandler(testJson, BASE_URL, mocked.client, "sk")
            .stream(params("model" to "m", "prompt" to "q", "stream" to false))
            .toList()
        results.size shouldBe 1
        results.single().response shouldBe "42"
        results.single().done shouldBe true
    }

    test("requests are authenticated with Bearer") {
        val mocked = sseMock()
        OpenAiChatHandler(testJson, BASE_URL, mocked.client, "sk-test").chat(chatParams).toList()
        mocked.requests.last().headers[HttpHeaders.Authorization] shouldBe "Bearer sk-test"
    }

    test("the factory refuses to build an OpenAI handler without API key") {
        val factory = HandlerFactory(testJson, BASE_URL, sseMock().client, apiKey = null)
        shouldThrow<IllegalArgumentException> { factory.createChatHandler(LLMProvider.OpenAI) }
    }

    test("usage is mapped to token counts when present") {
        val mocked = MockedClient {
            respondLines(
                "data: " + openAiChatChunk("a"),
                "data: " + openAiChatChunk(null, "stop", extra = ""","usage":{"prompt_tokens":10,"completion_tokens":5,"total_tokens":15}"""),
                "data: [DONE]"
            )
        }
        val (first, last) = OpenAiChatHandler(testJson, BASE_URL, mocked.client, "sk").chat(chatParams).toList()
        first.promptEvalCount.shouldBeNull()
        first.evalCount.shouldBeNull()
        last.promptEvalCount shouldBe 10
        last.evalCount shouldBe 5
    }

    test("embeddings keep the input order") {
        val mocked = MockedClient {
            respondJson(
                """{"object":"list","model":"text-embedding-3-small","data":[""" +
                    """{"object":"embedding","index":1,"embedding":[2.0]},""" +
                    """{"object":"embedding","index":0,"embedding":[1.0]}],""" +
                    """"usage":{"prompt_tokens":2,"total_tokens":2}}"""
            )
        }
        val result = OpenAiEmbeddingHandler(testJson, BASE_URL, mocked.client, "sk")
            .embeddings(params("model" to "text-embedding-3-small", "input" to listOf("a", "b")))
            .toList()
            .single()
        result.embeddings shouldBe listOf(listOf(1.0), listOf(2.0))
    }

    test("embeddings report HTTP errors") {
        val mocked = MockedClient {
            respondJson("""{"error":{"message":"Incorrect API key provided"}}""", HttpStatusCode.Unauthorized)
        }
        val error = shouldThrow<ProviderHttpException> {
            OpenAiEmbeddingHandler(testJson, BASE_URL, mocked.client, "sk")
                .embeddings(params("model" to "m", "input" to "a"))
                .toList()
        }
        error.status shouldBe 401
    }
})
