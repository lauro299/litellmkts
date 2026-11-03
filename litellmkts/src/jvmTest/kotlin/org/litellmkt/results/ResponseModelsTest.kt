package org.litellmkt.results

import io.kotest.core.spec.style.FunSpec
import io.kotest.matchers.nulls.shouldBeNull
import io.kotest.matchers.shouldBe
import kotlinx.coroutines.flow.toList
import org.litellmkt.BASE_URL
import org.litellmkt.MockedClient
import org.litellmkt.handlers.openAi.OpenAiChatHandler
import org.litellmkt.openAiChatChunk
import org.litellmkt.params
import org.litellmkt.params.Message
import org.litellmkt.respondLines
import org.litellmkt.testJson
import org.litellmkt.types.MessageRole
import org.litellmkt.types.TokenUsage

class ResponseModelsTest : FunSpec({

    fun result(role: String = "assistant", promptTokens: Int? = null, completionTokens: Int? = null) = BaseResultModel(
        model = "m",
        createdAt = "",
        response = "hi",
        done = true,
        promptEvalCount = promptTokens,
        evalCount = completionTokens,
        message = Message(role, "hi")
    )

    test("metrics missing from an OpenAI chunk are null and usage is absent") {
        val mocked = MockedClient { respondLines("data: " + openAiChatChunk("hi"), "data: [DONE]") }
        val chunk = OpenAiChatHandler(testJson, BASE_URL, mocked.client, "sk")
            .chat(params("model" to "gpt-4o", "messages" to listOf(Message("user", "hi"))))
            .toList()
            .single()
        listOf(
            chunk.totalDuration, chunk.loadDuration, chunk.promptEvalCount,
            chunk.promptEvalDuration, chunk.evalCount, chunk.evalDuration
        ).forEach { it.shouldBeNull() }
        chunk.toChatResponse().usage.shouldBeNull()
    }

    test("usage is computed from real token counts") {
        result(promptTokens = 10, completionTokens = 5).toChatResponse().usage shouldBe
            TokenUsage(promptTokens = 10, completionTokens = 5, totalTokens = 15)
    }

    test("the tool role is mapped") {
        result(role = "tool").toChatResponse().message!!.role shouldBe MessageRole.TOOL
    }

    test("an unknown role falls back to assistant and keeps the original") {
        val response = result(role = "developer").toChatResponse()
        response.message!!.role shouldBe MessageRole.ASSISTANT
        response.metadata["original_role"] shouldBe "developer"
    }

    test("role lookup ignores case") {
        MessageRole.fromValue("USER") shouldBe MessageRole.USER
    }
})
