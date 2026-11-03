package org.litellmkt.handlers.ollama

import io.kotest.core.spec.style.FunSpec
import io.kotest.matchers.nulls.shouldBeNull
import io.kotest.matchers.shouldBe
import kotlinx.coroutines.flow.toList
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.double
import kotlinx.serialization.json.int
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import org.litellmkt.BASE_URL
import org.litellmkt.MockedClient
import org.litellmkt.ollamaGenerateChunk
import org.litellmkt.params
import org.litellmkt.params.Message
import org.litellmkt.params.completionRequest
import org.litellmkt.respondJson
import org.litellmkt.respondLines
import org.litellmkt.testJson

class OllamaHandlersTest : FunSpec({

    val messages = listOf(Message("user", "hi"))

    fun generateMock() = MockedClient { respondLines(ollamaGenerateChunk("ok", done = true)) }
    fun chatChunk(content: String, done: Boolean, extra: String = "") =
        """{"model":"llama3","created_at":"2024-01-01T00:00:00Z","message":{"role":"assistant","content":"$content"},"done":$done$extra}"""

    test("a request without options sends no options nor main_gpu") {
        val mocked = generateMock()
        OllamaGenerateGenerationHandler(testJson, BASE_URL, mocked.client)
            .stream(params("model" to "llama3", "prompt" to "hi"))
            .toList()
        mocked.lastBody().containsKey("options") shouldBe false
    }

    test("max_tokens from the builder becomes num_predict") {
        val mocked = generateMock()
        val request = completionRequest {
            model("llama3")
            prompt("hi")
            maxTokens(100)
        }
        OllamaGenerateGenerationHandler(testJson, BASE_URL, mocked.client).stream(request).toList()
        mocked.lastBody()["options"]!!.jsonObject["num_predict"]!!.jsonPrimitive.int shouldBe 100
    }

    test("num_predict wins over max_tokens") {
        val mocked = generateMock()
        OllamaGenerateGenerationHandler(testJson, BASE_URL, mocked.client)
            .stream(params("model" to "llama3", "prompt" to "hi", "max_tokens" to 100, "num_predict" to 7))
            .toList()
        mocked.lastBody()["options"]!!.jsonObject["num_predict"]!!.jsonPrimitive.int shouldBe 7
    }

    test("chat sends options too") {
        val mocked = MockedClient { respondLines(chatChunk("ok", done = true)) }
        OllamaChatHandler(testJson, BASE_URL, mocked.client)
            .chat(params("model" to "llama3", "messages" to messages, "temperature" to 0.2))
            .toList()
        mocked.lastBody()["options"]!!.jsonObject["temperature"]!!.jsonPrimitive.double shouldBe 0.2
    }

    test("keep_alive accepts a duration string") {
        val mocked = generateMock()
        OllamaGenerateGenerationHandler(testJson, BASE_URL, mocked.client)
            .stream(params("model" to "llama3", "prompt" to "hi", "keep_alive" to "5m"))
            .toList()
        mocked.lastBody()["keep_alive"] shouldBe JsonPrimitive("5m")
    }

    test("keep_alive accepts an Int") {
        val mocked = generateMock()
        OllamaGenerateGenerationHandler(testJson, BASE_URL, mocked.client)
            .stream(params("model" to "llama3", "prompt" to "hi", "keep_alive" to 300))
            .toList()
        mocked.lastBody()["keep_alive"] shouldBe JsonPrimitive(300)
    }

    test("chat reports done and metrics on the last chunk") {
        val mocked = MockedClient {
            respondLines(
                chatChunk("Ho", done = false),
                chatChunk("la", done = false),
                chatChunk("", done = true, extra = ""","eval_count":42,"total_duration":1000""")
            )
        }
        val results = OllamaChatHandler(testJson, BASE_URL, mocked.client)
            .chat(params("model" to "llama3", "messages" to messages))
            .toList()
        results.map { it.done } shouldBe listOf(false, false, true)
        results.last().evalCount shouldBe 42
        results.last().totalDuration shouldBe 1000
        results.first().evalCount.shouldBeNull()
    }

    test("embeddings read snake_case metrics") {
        val mocked = MockedClient {
            respondJson("""{"model":"nomic","embeddings":[[0.1,0.2]],"prompt_eval_count":7}""")
        }
        val result = OllamaEmbeddingHandler(testJson, BASE_URL, mocked.client)
            .embeddings(params("model" to "nomic", "input" to listOf("a")))
            .toList()
            .single()
        result.promptEvalCount shouldBe 7
        result.embeddings shouldBe listOf(listOf(0.1, 0.2))
    }

    test("intermediate generate chunks have null metrics") {
        val mocked = MockedClient {
            respondLines(ollamaGenerateChunk("a"), ollamaGenerateChunk("", done = true, extra = ""","eval_count":3"""))
        }
        val (first, last) = OllamaGenerateGenerationHandler(testJson, BASE_URL, mocked.client)
            .stream(params("model" to "llama3", "prompt" to "hi"))
            .toList()
        first.evalCount.shouldBeNull()
        first.totalDuration.shouldBeNull()
        last.evalCount shouldBe 3
    }

    test("generate with stream=false emits a single done result") {
        val mocked = MockedClient { respondJson(ollamaGenerateChunk("full answer", done = true)) }
        val results = OllamaGenerateGenerationHandler(testJson, BASE_URL, mocked.client)
            .stream(params("model" to "llama3", "prompt" to "hi", "stream" to false))
            .toList()
        results.size shouldBe 1
        results.single().response shouldBe "full answer"
        results.single().done shouldBe true
    }
})
