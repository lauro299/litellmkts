package org.litellmkt.example.ios

import kotlinx.io.IOException
import org.litellmkt.errors.ProviderHttpException
import org.litellmkt.errors.ProviderStreamException
import org.litellmkt.errors.ResponseParseException

/** Kind of failure, so Swift can show a localized description without parsing messages. */
enum class ErrorCategory {
    /** The server answered with a non-2xx status; see [LitellmktsException.httpStatus]. */
    HTTP,

    /** The server could not be reached (connection, DNS, timeout). */
    NETWORK,

    /** The server answered something that is not a valid provider response. */
    INVALID_RESPONSE,

    /** The provider reported an error in the middle of a response. */
    PROVIDER,

    /** The client was configured or called with invalid values. */
    CONFIGURATION,
    UNKNOWN
}

/**
 * Error delivered to Swift. It carries no user-facing text: [detail] is the untranslated technical message, meant to
 * be shown below a localized description of [category].
 *
 * @property httpStatus HTTP status code, only for [ErrorCategory.HTTP].
 */
class LitellmktsException(
    val category: ErrorCategory,
    val httpStatus: Int?,
    val detail: String,
    cause: Throwable? = null
) : Exception(detail, cause)

internal fun Throwable.toLitellmktsException(): LitellmktsException = when (this) {
    is LitellmktsException -> this
    is ProviderHttpException -> LitellmktsException(ErrorCategory.HTTP, status, detail(), this)
    is ProviderStreamException -> LitellmktsException(ErrorCategory.PROVIDER, null, detail(), this)
    is ResponseParseException -> LitellmktsException(ErrorCategory.INVALID_RESPONSE, null, detail(), this)
    is IOException -> LitellmktsException(ErrorCategory.NETWORK, null, detail(), this)
    is IllegalArgumentException -> LitellmktsException(ErrorCategory.CONFIGURATION, null, detail(), this)
    else -> LitellmktsException(ErrorCategory.UNKNOWN, null, detail(), this)
}

private fun Throwable.detail(): String = message ?: toString()
