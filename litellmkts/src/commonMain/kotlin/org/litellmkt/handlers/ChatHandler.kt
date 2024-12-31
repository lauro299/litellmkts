package org.litellmkt.handlers

import kotlinx.coroutines.flow.Flow
import org.litellmkt.params.BaseParamsModel
import org.litellmkt.results.BaseResultModel

interface ChatHandler {
    fun chat(params: BaseParamsModel): Flow<BaseResultModel>
}