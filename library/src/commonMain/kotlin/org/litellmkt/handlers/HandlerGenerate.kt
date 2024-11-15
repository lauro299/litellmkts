package org.litellmkt.handlers

import kotlinx.coroutines.flow.Flow
import org.litellmkt.HandlerParamsModel
import org.litellmkt.HandlerResultModel
import org.litellmkt.Message

interface HandlerStream<T : HandlerParamsModel, R : HandlerResultModel> {
    fun stream(
        params: T
    ): Flow<R>

    /**
     * Return a flow of numbers(doubles)
     * */
    /*fun embeddings(params: T): Flow<Double>

    fun combineMessages(list: List<Message>): String {
        return list.map { it.content }.reduce { acc, s -> "$acc $s" } ?: ""
    }*/
}