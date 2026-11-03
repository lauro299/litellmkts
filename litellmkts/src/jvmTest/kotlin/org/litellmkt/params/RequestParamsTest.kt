package org.litellmkt.params

import io.kotest.assertions.throwables.shouldThrow
import io.kotest.core.spec.style.FunSpec
import io.kotest.matchers.nulls.shouldBeNull
import io.kotest.matchers.shouldBe
import io.kotest.matchers.string.shouldContain
import kotlinx.coroutines.flow.toList
import kotlinx.serialization.json.double
import kotlinx.serialization.json.int
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import org.litellmkt.BASE_URL
import org.litellmkt.MockedClient
import org.litellmkt.handlers.ollama.OllamaChatHandler
import org.litellmkt.handlers.openAi.OpenAiChatHandler
import org.litellmkt.openAiChatChunk
import org.litellmkt.respondLines
import org.litellmkt.testJson

class RequestParamsTest : FunSpec({

    val messages = listOf(Message("user", "hi"))

    fun openAiMock() = MockedClient { respondLines("data: " + openAiChatChunk("ok", "stop"), "data: [DONE]") }

    test("model reflects the map even after an earlier read") {
        val params = BaseParamsModel()
        params.model.shouldBeNull()
        params["model"] = "llama3"
        params.model shouldBe "llama3"
    }

    test("stream reflects later changes") {
        val params = BaseParamsModel()
        params["stream"] = true
        params.stream shouldBe true
        params["stream"] = false
        params.stream shouldBe false
    }

    test("a model of the wrong type fails validation instead of crashing") {
        val params = BaseParamsModel().apply {
            put("model", 123)
            put("messages", messages)
        }
        params.model.shouldBeNull()
        val mocked = MockedClient { respondLines() }
        val error = shouldThrow<IllegalArgumentException> {
            OllamaChatHandler(testJson, BASE_URL, mocked.client).chat(params).toList()
        }
        error.message shouldContain "model"
    }

    test("an Int temperature is sent as a double") {
        val mocked = openAiMock()
        OpenAiChatHandler(testJson, BASE_URL, mocked.client, "sk")
            .chat(org.litellmkt.params("model" to "gpt-4o", "messages" to messages, "temperature" to 1))
            .toList()
        mocked.lastBody()["temperature"]!!.jsonPrimitive.double shouldBe 1.0
    }

    test("a Long max_tokens is accepted") {
        val mocked = openAiMock()
        OpenAiChatHandler(testJson, BASE_URL, mocked.client, "sk")
            .chat(org.litellmkt.params("model" to "gpt-4o", "messages" to messages, "max_tokens" to 50L))
            .toList()
        mocked.lastBody()["max_tokens"]!!.jsonPrimitive.int shouldBe 50
    }

    test("a non-numeric temperature fails instead of being dropped") {
        val mocked = openAiMock()
        val error = shouldThrow<IllegalArgumentException> {
            OpenAiChatHandler(testJson, BASE_URL, mocked.client, "sk")
                .chat(org.litellmkt.params("model" to "gpt-4o", "messages" to messages, "temperature" to "alta"))
                .toList()
        }
        error.message shouldContain "temperature"
    }

    test("the chat builder works with Ollama") {
        val mocked = MockedClient {
            respondLines("""{"model":"llama3","message":{"role":"assistant","content":"hey"},"done":true}""")
        }
        val request = chatRequest {
            model("llama3")
            userMessage("hola")
            temperature(0.5)
        }
        OllamaChatHandler(testJson, BASE_URL, mocked.client).chat(request).toList()
        val body = mocked.lastBody()
        body["messages"]!!.jsonArray.single().jsonObject["content"]!!.jsonPrimitive.content shouldBe "hola"
        body["options"]!!.jsonObject["temperature"]!!.jsonPrimitive.double shouldBe 0.5
    }
})
