package org.litellmkt.results

import org.litellmkt.params.Message
import org.litellmkt.params.Model

interface Response {
    val response: String?
}

interface Evaluation {
    val totalDuration: Long?
    val loadDuration: Long?
    val promptEvalCount: Int?
    val promptEvalDuration: Long?
    val evalCount: Int?
    val evalDuration: Long?
}

sealed interface HandlerResultModel : Model, Evaluation, Response {
    val createdAt: String
    val context: List<Int>?
    val done: Boolean
}

data class BaseResultModel(
    override val model: String,
    val createdAt: String,
    override val response: String,
    val done: Boolean,
    override val totalDuration: Long? = -1, // Time spent generating the response
    override val loadDuration: Long? = -1, // Time spent loading the model in nanoseconds
    override val promptEvalCount: Int? = -1, // Number of tokens in the prompt
    override val promptEvalDuration: Long? = -1, // Time spent evaluating the prompt in nanoseconds
    override val evalCount: Int? = -1, // Number of tokens in the response
    override val evalDuration: Long? = -1, // Time spent generating the response in nanoseconds
    val context: List<Int>? = null, // Encoding of th
    val message: Message? = null, // Chat
    val embeddings: List<List<Double>> = emptyList()
) : Model, Evaluation, Response

