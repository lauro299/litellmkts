package org.litellmkt

import io.ktor.client.HttpClient
import io.ktor.client.engine.mock.MockEngine
import io.ktor.client.engine.mock.MockRequestHandleScope
import io.ktor.client.engine.mock.respond
import io.ktor.client.engine.mock.toByteArray
import io.ktor.client.request.HttpRequestData
import io.ktor.client.request.HttpResponseData
import io.ktor.http.HttpHeaders
import io.ktor.http.HttpStatusCode
import io.ktor.http.headersOf
import io.ktor.utils.io.ByteReadChannel
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.jsonObject
import org.litellmkt.network.LitellmktConfig
import org.litellmkt.network.applyLitellmktDefaults
import org.litellmkt.network.jsonParser
import org.litellmkt.params.BaseParamsModel

internal val testJson: Json = jsonParser()

internal const val BASE_URL = "http://llm.test"

/** A client configured exactly like the library's, backed by a [MockEngine]. */
internal class MockedClient(
    config: LitellmktConfig = LitellmktConfig(),
    handler: suspend MockRequestHandleScope.(HttpRequestData) -> HttpResponseData,
) {
    val engine = MockEngine { request -> handler(request) }
    val client = HttpClient(engine) { applyLitellmktDefaults(testJson, config) }

    val requests: List<HttpRequestData> get() = engine.requestHistory

    suspend fun lastBody(): JsonObject =
        Json.parseToJsonElement(requests.last().body.toByteArray().decodeToString()).jsonObject
}

internal fun MockRequestHandleScope.respondLines(
    vararg lines: String,
    status: HttpStatusCode = HttpStatusCode.OK,
) = respond(
    content = ByteReadChannel(lines.joinToString(separator = "\n", postfix = "\n")),
    status = status,
    headers = headersOf(HttpHeaders.ContentType, "application/x-ndjson")
)

internal fun MockRequestHandleScope.respondJson(
    body: String,
    status: HttpStatusCode = HttpStatusCode.OK,
) = respond(
    content = body,
    status = status,
    headers = headersOf(HttpHeaders.ContentType, "application/json")
)

internal fun params(vararg entries: Pair<String, Any>) = BaseParamsModel().apply { putAll(entries) }

internal fun ollamaGenerateChunk(response: String, done: Boolean = false, extra: String = "") =
    """{"model":"llama3","created_at":"2024-01-01T00:00:00Z","response":"$response","done":$done$extra}"""

internal fun openAiChatChunk(content: String?, finishReason: String? = null, extra: String = ""): String {
    val delta = if (content == null) "{}" else """{"content":"$content"}"""
    val finish = finishReason?.let { "\"$it\"" } ?: "null"
    return """{"id":"c1","object":"chat.completion.chunk","created":1700000000,"model":"gpt-4o",""" +
        """"choices":[{"index":0,"delta":$delta,"finish_reason":$finish}]$extra}"""
}
