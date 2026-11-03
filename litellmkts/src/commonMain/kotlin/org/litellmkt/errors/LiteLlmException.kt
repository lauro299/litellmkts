package org.litellmkt.errors

/**
 * Base type of every provider, HTTP and parsing error surfaced by the handlers' flows.
 */
sealed class LiteLlmException(message: String, cause: Throwable? = null) : RuntimeException(message, cause)

/**
 * The provider answered with a non-2xx status.
 *
 * @property status HTTP status code.
 * @property providerMessage error message extracted from the body (`error.message` for OpenAI, `error` for Ollama),
 * or the raw body when it is not JSON.
 * @property body full response body.
 */
class ProviderHttpException(
    val status: Int,
    val providerMessage: String?,
    val body: String,
) : LiteLlmException("HTTP $status: ${(providerMessage ?: body).take(MAX_MESSAGE_LENGTH)}")

/**
 * The provider sent an error object in the middle of a successful (2xx) response stream.
 */
class ProviderStreamException(
    val providerMessage: String,
) : LiteLlmException(providerMessage.take(MAX_MESSAGE_LENGTH))

/**
 * A response chunk could not be deserialized. The original exception is kept as [cause].
 */
class ResponseParseException(
    cause: Throwable,
) : LiteLlmException("Could not parse provider response: ${cause.message}", cause)

private const val MAX_MESSAGE_LENGTH = 2048
