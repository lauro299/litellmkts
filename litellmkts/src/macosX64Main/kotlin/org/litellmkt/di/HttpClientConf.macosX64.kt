package org.litellmkt.di

import io.ktor.client.engine.HttpClientEngineFactory

internal actual fun getActualDeviceEngine(): HttpClientLocalEngine {
    return object : HttpClientLocalEngine {
        override operator fun invoke():HttpClientEngineFactory<*> = io.ktor.client.engine.darwin.Darwin
    }
}