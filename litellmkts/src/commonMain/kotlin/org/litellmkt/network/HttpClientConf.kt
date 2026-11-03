package org.litellmkt.network

import io.ktor.client.HttpClient
import io.ktor.client.HttpClientConfig
import io.ktor.client.engine.HttpClientEngineFactory
import io.ktor.client.plugins.HttpTimeout
import io.ktor.client.plugins.contentnegotiation.ContentNegotiation
import io.ktor.client.plugins.logging.Logging
import io.ktor.http.HttpHeaders
import io.ktor.serialization.kotlinx.json.json
import kotlinx.serialization.json.Json
import org.koin.core.annotation.Single

@Single
fun HttpClientConf(
    engine: HttpClientLocalEngine,
    parser: Json,
    config: LitellmktConfig?
): HttpClient = HttpClient(engine()) {
    applyLitellmktDefaults(parser, config ?: LitellmktConfig())
}

internal fun HttpClientConfig<*>.applyLitellmktDefaults(parser: Json, config: LitellmktConfig) {
    install(Logging) {
        logger = config.logger
        level = config.logLevel
        sanitizeHeader { header -> header == HttpHeaders.Authorization }
    }
    install(ContentNegotiation) {
        json(parser)
    }
    // No requestTimeoutMillis: it would cut off streaming responses that are still sending data.
    install(HttpTimeout) {
        connectTimeoutMillis = config.connectTimeoutMillis
        socketTimeoutMillis = config.socketTimeoutMillis
    }
}

@Single
internal fun jsonParser() = Json {
    ignoreUnknownKeys = true
    explicitNulls = false
}

interface HttpClientLocalEngine {
    operator fun invoke(): HttpClientEngineFactory<*>
}

@Single(binds = [HttpClientLocalEngine::class])
internal fun getDeviceEngineFactory(): HttpClientLocalEngine {
    return getActualDeviceEngine()
}

internal expect fun getActualDeviceEngine(): HttpClientLocalEngine
