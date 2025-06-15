package org.litellmkt.network

import io.ktor.client.HttpClient
import io.ktor.client.engine.HttpClientEngineFactory
import io.ktor.client.plugins.HttpTimeout
import io.ktor.client.plugins.contentnegotiation.ContentNegotiation
import io.ktor.client.plugins.logging.LogLevel.ALL
import io.ktor.client.plugins.logging.Logger
import io.ktor.client.plugins.logging.Logging
import io.ktor.client.plugins.logging.SIMPLE
import io.ktor.serialization.kotlinx.json.json
import kotlinx.serialization.json.Json
import org.koin.core.annotation.Singleton

@Singleton
fun HttpClientConf(
    engine: HttpClientLocalEngine
): HttpClient = HttpClient(engine()) {
    install(Logging) {
        logger = Logger.SIMPLE
        level = ALL
    }
    install(ContentNegotiation) {
        json(
            Json {
                ignoreUnknownKeys = true
            }
        )
    }
    install(HttpTimeout){
        requestTimeoutMillis = 10000
    }
}

@Singleton
internal fun jsonParser() = Json {
    ignoreUnknownKeys = true
}

interface HttpClientLocalEngine {
    operator fun invoke(): HttpClientEngineFactory<*>
}

@Singleton(binds = [HttpClientLocalEngine::class])
internal fun getDeviceEngineFactory(): HttpClientLocalEngine {
    return getActualDeviceEngine()
}

internal expect fun getActualDeviceEngine(): HttpClientLocalEngine
