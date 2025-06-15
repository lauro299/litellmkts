package org.litellmkt.network

import io.ktor.client.engine.HttpClientEngineFactory
import io.ktor.client.engine.darwin.Darwin

internal actual fun getActualDeviceEngine(): HttpClientLocalEngine {
    return object : HttpClientLocalEngine {
        override operator fun invoke(): HttpClientEngineFactory<*> = Darwin
    }
}