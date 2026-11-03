package org.litellmkt.errors

import io.kotest.assertions.throwables.shouldThrow
import io.kotest.core.spec.style.FunSpec
import io.kotest.matchers.collections.shouldBeEmpty
import io.kotest.matchers.collections.shouldHaveSize
import io.kotest.matchers.shouldBe
import io.kotest.matchers.string.shouldContain
import io.kotest.matchers.types.shouldBeInstanceOf
import io.ktor.client.engine.mock.respond
import io.ktor.http.HttpStatusCode
import kotlinx.coroutines.flow.toList
import kotlinx.serialization.SerializationException
import org.litellmkt.BASE_URL
import org.litellmkt.MockedClient
import org.litellmkt.handlers.ollama.OllamaChatHandler
import org.litellmkt.handlers.ollama.OllamaGenerateGenerationHandler
import org.litellmkt.handlers.openAi.OpenAiChatHandler
import org.litellmkt.ollamaGenerateChunk
import org.litellmkt.params
import org.litellmkt.params.Message
import org.litellmkt.respondJson
import org.litellmkt.respondLines
import org.litellmkt.results.BaseResultModel
import org.litellmkt.testJson

class ProviderErrorsTest : FunSpec({

    val generateParams = params("model" to "llama3", "prompt" to "hi")
    val chatParams = params("model" to "gpt-4o", "messages" to listOf(Message("user", "hi")))

    test("OpenAI 401 ends the flow with the provider message") {
        val mocked = MockedClient {
            respondJson("""{"error":{"message":"Incorrect API key provided","type":"invalid_request_error"}}""", HttpStatusCode.Unauthorized)
        }
        val error = shouldThrow<ProviderHttpException> {
            OpenAiChatHandler(testJson, BASE_URL, mocked.client, "sk-bad").chat(chatParams).toList()
        }
        error.status shouldBe 401
        error.providerMessage shouldBe "Incorrect API key provided"
    }

    test("Ollama 404 ends the flow with the provider message") {
        val mocked = MockedClient { respondJson("""{"error":"model 'foo' not found"}""", HttpStatusCode.NotFound) }
        val error = shouldThrow<ProviderHttpException> {
            OllamaGenerateGenerationHandler(testJson, BASE_URL, mocked.client).stream(generateParams).toList()
        }
        error.status shouldBe 404
        error.providerMessage shouldBe "model 'foo' not found"
    }

    test("a non-JSON 500 body becomes the message") {
        val mocked = MockedClient { respond("upstream exploded", HttpStatusCode.InternalServerError) }
        val error = shouldThrow<ProviderHttpException> {
            OllamaGenerateGenerationHandler(testJson, BASE_URL, mocked.client).stream(generateParams).toList()
        }
        error.status shouldBe 500
        error.providerMessage shouldBe "upstream exploded"
        error.message shouldContain "upstream exploded"
    }

    test("an error object in the middle of the stream is reported after the valid chunks") {
        val mocked = MockedClient {
            respondLines(
                ollamaGenerateChunk("a"),
                ollamaGenerateChunk("b"),
                """{"error":"llama runner process has terminated"}"""
            )
        }
        val emitted = mutableListOf<BaseResultModel>()
        val error = shouldThrow<ProviderStreamException> {
            OllamaGenerateGenerationHandler(testJson, BASE_URL, mocked.client).stream(generateParams).collect { emitted += it }
        }
        emitted shouldHaveSize 2
        error.message shouldContain "llama runner process has terminated"
    }

    test("a corrupt chunk keeps the deserialization error as cause") {
        val mocked = MockedClient { respondLines("{not json") }
        val error = shouldThrow<ResponseParseException> {
            OllamaGenerateGenerationHandler(testJson, BASE_URL, mocked.client).stream(generateParams).toList()
        }
        error.cause.shouldBeInstanceOf<SerializationException>()
    }

    test("errors from every provider share the LiteLlmException type") {
        val failing = MockedClient { respondJson("""{"error":"boom"}""", HttpStatusCode.BadGateway) }
        shouldThrow<LiteLlmException> {
            OllamaChatHandler(testJson, BASE_URL, failing.client).chat(chatParams).toList()
        }
        shouldThrow<LiteLlmException> {
            OpenAiChatHandler(testJson, BASE_URL, failing.client, "sk").chat(chatParams).toList()
        }
    }

    test("a missing model fails before any request is sent") {
        val mocked = MockedClient { respondLines(ollamaGenerateChunk("never")) }
        val error = shouldThrow<IllegalArgumentException> {
            OllamaGenerateGenerationHandler(testJson, BASE_URL, mocked.client).stream(params("prompt" to "hi")).toList()
        }
        error.message shouldContain "model"
        mocked.requests.shouldBeEmpty()
    }
})
