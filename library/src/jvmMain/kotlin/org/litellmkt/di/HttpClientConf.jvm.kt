package org.litellmkt.di

import io.ktor.client.engine.HttpClientEngineFactory
import io.ktor.client.engine.okhttp.OkHttp

internal actual fun getActualDeviceEngine(): HttpClientLocalEngine {
    return object: HttpClientLocalEngine{
        override fun invoke(): HttpClientEngineFactory<*> = OkHttp
    }
}