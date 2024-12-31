package org.litellmkt.handlers

import kotlinx.coroutines.flow.Flow
import org.litellmkt.params.BaseParamsModel
import org.litellmkt.params.HandlerEmbeddingsParamsModel
import org.litellmkt.results.BaseResultModel
import org.litellmkt.results.HandlerResultEmbeddingsModel

interface EmbeddingHandler {
    fun embeddings(params: BaseParamsModel): Flow<BaseResultModel>
}