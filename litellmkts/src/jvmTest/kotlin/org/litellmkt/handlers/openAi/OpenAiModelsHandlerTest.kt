package org.litellmkt.handlers.openAi

import io.kotest.assertions.throwables.shouldThrow
import io.kotest.core.spec.style.FunSpec
import io.kotest.matchers.nulls.shouldBeNull
import io.kotest.matchers.shouldBe
import io.kotest.matchers.types.shouldBeInstanceOf
import io.ktor.http.HttpHeaders
import io.ktor.http.HttpMethod
import io.ktor.http.HttpStatusCode
import kotlinx.coroutines.flow.toList
import kotlinx.datetime.Instant
import org.litellmkt.BASE_URL
import org.litellmkt.MockedClient
import org.litellmkt.errors.ProviderHttpException
import org.litellmkt.errors.ResponseParseException
import org.litellmkt.handlers.HandlerFactory
import org.litellmkt.handlers.ollama.OllamaModelsHandler
import org.litellmkt.respondJson
import org.litellmkt.testJson
import org.litellmkt.types.LLMProvider

class OpenAiModelsHandlerTest : FunSpec({

    val modelsJson = """
        {"object":"list","data":[
            {"id":"gpt-4o","object":"model","created":1715367049,"owned_by":"system"},
            {"id":"text-embedding-3-small","object":"model","created":1705948997,"owned_by":"system"}
        ]}
    """.trimIndent()

    fun handler(mocked: MockedClient, apiKey: String = "sk-test") =
        OpenAiModelsHandler(testJson, BASE_URL, mocked.client, apiKey)

    test("maps id, owner and creation date") {
        val mocked = MockedClient { respondJson(modelsJson) }
        val models = handler(mocked).listModels().toList()

        models.map { it.name } shouldBe listOf("gpt-4o", "text-embedding-3-small")
        val gpt = models.first()
        gpt.ownedBy shouldBe "system"
        gpt.modifiedAt shouldBe Instant.parse("2024-05-10T18:50:49Z")
        gpt.size.shouldBeNull()
        gpt.digest.shouldBeNull()
        gpt.details.shouldBeNull()
    }

    test("calls GET /v1/models with Bearer auth") {
        val mocked = MockedClient { respondJson(modelsJson) }
        handler(mocked).listModels().toList()
        val request = mocked.requests.single()
        request.method shouldBe HttpMethod.Get
        request.url.toString() shouldBe "$BASE_URL/v1/models"
        request.headers[HttpHeaders.Authorization] shouldBe "Bearer sk-test"
    }

    test("an invalid API key ends the flow with the provider message") {
        val mocked = MockedClient {
            respondJson("""{"error":{"message":"Incorrect API key provided"}}""", HttpStatusCode.Unauthorized)
        }
        val error = shouldThrow<ProviderHttpException> { handler(mocked, "sk-bad").listModels().toList() }
        error.status shouldBe 401
        error.providerMessage shouldBe "Incorrect API key provided"
    }

    test("a response without data ends the flow with ResponseParseException") {
        val mocked = MockedClient { respondJson("""{"object":"list"}""") }
        shouldThrow<ResponseParseException> { handler(mocked).listModels().toList() }
    }

    context("HandlerFactory.createModelsHandler") {
        val client = MockedClient { respondJson(modelsJson) }.client

        test("by name returns the Ollama handler") {
            HandlerFactory(testJson, BASE_URL, client).createModelsHandler("ollama")
                .shouldBeInstanceOf<OllamaModelsHandler>()
        }

        test("OpenAI with an API key returns the OpenAI handler") {
            HandlerFactory(testJson, BASE_URL, client, apiKey = "sk").createModelsHandler(LLMProvider.OpenAI)
                .shouldBeInstanceOf<OpenAiModelsHandler>()
        }

        test("OpenAI without an API key fails") {
            shouldThrow<IllegalArgumentException> {
                HandlerFactory(testJson, BASE_URL, client, apiKey = null).createModelsHandler(LLMProvider.OpenAI)
            }
        }

        test("custom providers fail") {
            shouldThrow<IllegalArgumentException> {
                HandlerFactory(testJson, BASE_URL, client).createModelsHandler("lmstudio")
            }
        }
    }
})
