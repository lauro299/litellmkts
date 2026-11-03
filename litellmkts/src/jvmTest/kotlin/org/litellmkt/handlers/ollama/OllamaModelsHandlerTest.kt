package org.litellmkt.handlers.ollama

import io.kotest.assertions.throwables.shouldThrow
import io.kotest.core.spec.style.FunSpec
import io.kotest.matchers.collections.shouldBeEmpty
import io.kotest.matchers.nulls.shouldBeNull
import io.kotest.matchers.shouldBe
import io.ktor.client.engine.mock.respond
import io.ktor.http.HttpMethod
import io.ktor.http.HttpStatusCode
import kotlinx.coroutines.flow.toList
import kotlinx.datetime.Instant
import org.litellmkt.BASE_URL
import org.litellmkt.MockedClient
import org.litellmkt.errors.ProviderHttpException
import org.litellmkt.errors.ResponseParseException
import org.litellmkt.handlers.ModelDetails
import org.litellmkt.respondJson
import org.litellmkt.testJson

class OllamaModelsHandlerTest : FunSpec({

    // Real /api/tags payload, as returned by Ollama.
    val tagsJson = """
        {
            "models": [
                {
                    "name": "qwen3:latest",
                    "model": "qwen3:latest",
                    "modified_at": "2025-08-04T10:01:39.344094522-06:00",
                    "size": 5225388164,
                    "digest": "500a1f067a9f782620b40bee6f7b0c89e17ae61f686b92c24933e4ca4b2b8b41",
                    "details": {
                        "parent_model": "",
                        "format": "gguf",
                        "family": "qwen3",
                        "families": ["qwen3"],
                        "parameter_size": "8.2B",
                        "quantization_level": "Q4_K_M"
                    }
                },
                {
                    "name": "llama3.1:latest",
                    "model": "llama3.1:latest",
                    "modified_at": "2024-11-07T21:55:49.762580859-06:00",
                    "size": 4661230766,
                    "digest": "42182419e9508c30c4b1fe55015f06b65f4ca4b9e28a744be55008d21998a093",
                    "details": {
                        "parent_model": "",
                        "format": "gguf",
                        "family": "llama",
                        "families": ["llama"],
                        "parameter_size": "8.0B",
                        "quantization_level": "Q4_0"
                    }
                }
            ]
        }
    """.trimIndent()

    fun handler(mocked: MockedClient) = OllamaModelsHandler(testJson, BASE_URL, mocked.client)

    test("lists every model in order with its details") {
        val mocked = MockedClient { respondJson(tagsJson) }
        val (qwen, llama) = handler(mocked).listModels().toList()

        qwen.name shouldBe "qwen3:latest"
        qwen.model shouldBe "qwen3:latest"
        qwen.modifiedAt shouldBe Instant.parse("2025-08-04T16:01:39.344094522Z")
        qwen.size shouldBe 5225388164
        qwen.digest shouldBe "500a1f067a9f782620b40bee6f7b0c89e17ae61f686b92c24933e4ca4b2b8b41"
        qwen.ownedBy.shouldBeNull()
        qwen.details shouldBe ModelDetails(
            parentModel = null, // sent as ""
            format = "gguf",
            family = "qwen3",
            families = listOf("qwen3"),
            parameterSize = "8.2B",
            quantizationLevel = "Q4_K_M"
        )

        llama.name shouldBe "llama3.1:latest"
        llama.size shouldBe 4661230766
        llama.details?.parameterSize shouldBe "8.0B"
        llama.details?.quantizationLevel shouldBe "Q4_0"
    }

    test("an empty list emits nothing") {
        val mocked = MockedClient { respondJson("""{"models":[]}""") }
        handler(mocked).listModels().toList().shouldBeEmpty()
    }

    test("calls GET /api/tags") {
        val mocked = MockedClient { respondJson("""{"models":[]}""") }
        handler(mocked).listModels().toList()
        val request = mocked.requests.single()
        request.method shouldBe HttpMethod.Get
        request.url.toString() shouldBe "$BASE_URL/api/tags"
    }

    test("missing details fields become null") {
        val mocked = MockedClient {
            respondJson("""{"models":[{"name":"tiny","details":{"format":"gguf"}}]}""")
        }
        val model = handler(mocked).listModels().toList().single()
        model.details?.parentModel.shouldBeNull()
        model.details?.families.shouldBeNull()
        model.details?.format shouldBe "gguf"
        model.size.shouldBeNull()
    }

    test("an unparseable modified_at becomes null without dropping the model") {
        val mocked = MockedClient {
            respondJson("""{"models":[{"name":"tiny","modified_at":"ayer","size":42}]}""")
        }
        val model = handler(mocked).listModels().toList().single()
        model.modifiedAt.shouldBeNull()
        model.size shouldBe 42
    }

    test("HTTP errors end the flow with ProviderHttpException") {
        val mocked = MockedClient { respond("boom", HttpStatusCode.InternalServerError) }
        val error = shouldThrow<ProviderHttpException> { handler(mocked).listModels().toList() }
        error.status shouldBe 500
    }

    test("a response without models ends the flow with ResponseParseException") {
        val mocked = MockedClient { respondJson("""{"status":"ok"}""") }
        shouldThrow<ResponseParseException> { handler(mocked).listModels().toList() }
    }
})
