package org.litellmkt.handlers.internal

import io.ktor.client.statement.HttpResponse
import io.ktor.client.statement.bodyAsText
import io.ktor.http.isSuccess
import io.ktor.utils.io.ByteReadChannel
import io.ktor.utils.io.readUTF8Line
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.decodeFromJsonElement
import org.litellmkt.errors.ProviderHttpException
import org.litellmkt.errors.ProviderStreamException
import org.litellmkt.errors.ResponseParseException

/**
 * Throws [ProviderHttpException] when the response status is not 2xx.
 */
internal suspend fun HttpResponse.ensureSuccess() {
    if (status.isSuccess()) return
    val body = runCatching { bodyAsText() }.getOrDefault("")
    throw ProviderHttpException(
        status = status.value,
        providerMessage = extractProviderMessage(body),
        body = body
    )
}

/**
 * Calls [action] for every non-blank line until the channel is exhausted.
 */
internal suspend inline fun ByteReadChannel.forEachLine(action: (String) -> Unit) {
    while (true) {
        val line = readUTF8Line() ?: break
        if (line.isNotBlank()) action(line)
    }
}

/**
 * Returns the payload of an SSE `data:` line, or null for `[DONE]`, comments and other fields.
 */
internal fun sseData(line: String): String? {
    if (!line.startsWith("data:")) return null
    return line.removePrefix("data:").trim().takeUnless { it.isEmpty() || it == "[DONE]" }
}

/**
 * Decodes one provider payload. An `error` object becomes [ProviderStreamException] and any
 * deserialization failure becomes [ResponseParseException].
 */
internal inline fun <reified T> Json.decodeProviderChunk(payload: String): T {
    val element = try {
        parseToJsonElement(payload)
    } catch (e: IllegalArgumentException) {
        throw ResponseParseException(e)
    }
    (element as? JsonObject)?.get("error")?.let { error ->
        throw ProviderStreamException(errorMessage(error) ?: error.toString())
    }
    return try {
        decodeFromJsonElement<T>(element)
    } catch (e: IllegalArgumentException) {
        throw ResponseParseException(e)
    }
}

internal fun extractProviderMessage(body: String): String? {
    val json = runCatching { Json.parseToJsonElement(body) }.getOrNull() as? JsonObject
    return json?.get("error")?.let(::errorMessage) ?: body.takeIf { it.isNotBlank() }
}

/** OpenAI sends `{"error": {"message": ...}}`, Ollama sends `{"error": "..."}`. */
internal fun errorMessage(error: JsonElement): String? = when (error) {
    is JsonPrimitive -> error.contentOrNull
    is JsonObject -> (error["message"] as? JsonPrimitive)?.contentOrNull
    else -> null
}
