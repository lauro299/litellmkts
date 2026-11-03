package org.litellmkt.network

import io.ktor.client.plugins.logging.LogLevel
import io.ktor.client.plugins.logging.Logger
import io.ktor.client.plugins.logging.SIMPLE

/**
 * Optional transport configuration. Declare it in a Koin module to override the defaults:
 *
 * ```
 * single { LitellmktConfig(socketTimeoutMillis = 60_000, logLevel = LogLevel.HEADERS) }
 * ```
 *
 * There is no total request timeout, so long streaming generations are never cut off while data keeps
 * arriving. Cancel the collecting coroutine (or wrap it in `withTimeout`) to bound a call.
 *
 * @param connectTimeoutMillis time allowed to establish the connection.
 * @param socketTimeoutMillis maximum inactivity between two data packets. Not supported by every engine (e.g. Js).
 * @param logLevel HTTP logging level. Logging is off by default; the `Authorization` header is always masked.
 * @param logger destination of the HTTP logs.
 */
data class LitellmktConfig(
    val connectTimeoutMillis: Long = 30_000,
    val socketTimeoutMillis: Long = 120_000,
    val logLevel: LogLevel = LogLevel.NONE,
    val logger: Logger = Logger.SIMPLE,
)
