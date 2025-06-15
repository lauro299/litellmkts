package org.litellmkt.network

import io.ktor.client.engine.HttpClientEngineFactory
import io.ktor.client.engine.curl.Curl

internal actual fun getActualDeviceEngine(): HttpClientLocalEngine {
    return object: HttpClientLocalEngine {
        override fun invoke(): HttpClientEngineFactory<*> = Curl
    }
}