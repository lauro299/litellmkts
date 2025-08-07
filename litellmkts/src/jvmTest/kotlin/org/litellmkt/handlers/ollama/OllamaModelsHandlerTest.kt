package org.litellmkt.handlers.ollama

import io.kotest.core.spec.style.FunSpec
import io.kotest.matchers.shouldBe
import io.ktor.client.HttpClient
import io.ktor.client.engine.mock.MockEngine
import io.ktor.client.engine.mock.respond
import io.ktor.client.plugins.contentnegotiation.ContentNegotiation
import io.ktor.http.ContentType
import io.ktor.http.HttpStatusCode
import io.ktor.http.headersOf
import io.ktor.serialization.kotlinx.json.json
import kotlinx.coroutines.flow.toList
import kotlinx.serialization.json.Json

class OllamaModelsHandlerTest : FunSpec({
    
    val mockJson = """
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

    test("should list models successfully") {
        val mockEngine = MockEngine { request ->
            respond(
                content = mockJson,
                status = HttpStatusCode.OK,
                headers = headersOf("Content-Type" to listOf(ContentType.Application.Json.toString()))
            )
        }

        val httpClient = HttpClient(mockEngine) {
            install(ContentNegotiation) {
                json()
            }
        }

        val parser = Json {
            ignoreUnknownKeys = true
            isLenient = true
        }

        val handler = OllamaModelsHandler(
            parser = parser,
            baseUrl = "http://localhost:11434",
            httpClient = httpClient
        )

        val models = handler.listModels().toList()

        models.size shouldBe 2
        
        val firstModel = models[0]
        firstModel.name shouldBe "qwen3:latest"
        firstModel.model shouldBe "qwen3:latest"
        firstModel.size shouldBe 5225388164
        firstModel.digest shouldBe "500a1f067a9f782620b40bee6f7b0c89e17ae61f686b92c24933e4ca4b2b8b41"
        firstModel.details.format shouldBe "gguf"
        firstModel.details.family shouldBe "qwen3"
        firstModel.details.parameterSize shouldBe "8.2B"
        firstModel.details.quantizationLevel shouldBe "Q4_K_M"
        firstModel.details.families shouldBe listOf("qwen3")

        val secondModel = models[1]
        secondModel.name shouldBe "llama3.1:latest"
        secondModel.model shouldBe "llama3.1:latest"
        secondModel.size shouldBe 4661230766
        secondModel.digest shouldBe "42182419e9508c30c4b1fe55015f06b65f4ca4b9e28a744be55008d21998a093"
        secondModel.details.format shouldBe "gguf"
        secondModel.details.family shouldBe "llama"
        secondModel.details.parameterSize shouldBe "8.0B"
        secondModel.details.quantizationLevel shouldBe "Q4_0"
        secondModel.details.families shouldBe listOf("llama")
    }

    test("should handle empty models list") {
        val emptyJson = """{"models": []}"""

        val mockEngine = MockEngine { request ->
            respond(
                content = emptyJson,
                status = HttpStatusCode.OK,
                headers = headersOf("Content-Type" to listOf(ContentType.Application.Json.toString()))
            )
        }

        val httpClient = HttpClient(mockEngine) {
            install(ContentNegotiation) {
                json()
            }
        }

        val parser = Json {
            ignoreUnknownKeys = true
            isLenient = true
        }

        val handler = OllamaModelsHandler(
            parser = parser,
            baseUrl = "http://localhost:11434",
            httpClient = httpClient
        )

        val models = handler.listModels().toList()
        models.size shouldBe 0
    }

    test("should make correct API call") {
        var requestUrl = ""

        val mockEngine = MockEngine { request ->
            requestUrl = request.url.toString()
            respond(
                content = """{"models": []}""",
                status = HttpStatusCode.OK,
                headers = headersOf("Content-Type" to listOf(ContentType.Application.Json.toString()))
            )
        }

        val httpClient = HttpClient(mockEngine) {
            install(ContentNegotiation) {
                json()
            }
        }

        val parser = Json {
            ignoreUnknownKeys = true
            isLenient = true
        }

        val baseUrl = "http://localhost:11434"
        val handler = OllamaModelsHandler(
            parser = parser,
            baseUrl = baseUrl,
            httpClient = httpClient
        )

        handler.listModels().toList()

        requestUrl shouldBe "$baseUrl/api/tags"
    }
})