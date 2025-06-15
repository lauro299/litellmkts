package org.litellmkt.network

import io.ktor.client.engine.HttpClientEngineFactory
import io.ktor.client.engine.js.Js

internal actual fun getActualDeviceEngine(): HttpClientLocalEngine {
    return object: HttpClientLocalEngine {
        override fun invoke(): HttpClientEngineFactory<*> = Js
    }
}