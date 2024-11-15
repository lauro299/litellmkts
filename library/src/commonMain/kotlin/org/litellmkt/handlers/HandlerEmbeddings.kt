package org.litellmkt.handlers

import kotlinx.coroutines.flow.Flow
import org.litellmkt.handlers.params.HandlerEmbeddingsParamsModel
import org.litellmkt.handlers.results.HandlerResultEmbeddingsModel

interface HandlerEmbeddings<T : HandlerEmbeddingsParamsModel, R : HandlerResultEmbeddingsModel> {
    fun embeddings(params: T): Flow<R>
}