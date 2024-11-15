package org.litellmkt.handlers

import kotlinx.coroutines.flow.Flow
import org.litellmkt.handlers.params.HandlerChatParamsModel
import org.litellmkt.handlers.results.HandlerResultChatModel

interface HandlerChat<T : HandlerChatParamsModel, R : HandlerResultChatModel> {
    fun chat(params: T): Flow<R>
}