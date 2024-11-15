package org.litellmkt.handlers

import kotlinx.coroutines.flow.Flow
import org.litellmkt.handlers.params.HandlerParamsModel
import org.litellmkt.handlers.results.HandlerResultModel

interface HandlerGenerate<T : HandlerParamsModel, R : HandlerResultModel> {
    fun stream(
        params: T
    ): Flow<R>
}