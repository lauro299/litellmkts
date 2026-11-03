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

data class BaseResultModel(
    override val model: String,
    val createdAt: String,
    override val response: String,
    val done: Boolean,
    override val totalDuration: Long? = null, // Time spent generating the response, null if not reported
    override val loadDuration: Long? = null, // Time spent loading the model in nanoseconds
    override val promptEvalCount: Int? = null, // Number of tokens in the prompt
    override val promptEvalDuration: Long? = null, // Time spent evaluating the prompt in nanoseconds
    override val evalCount: Int? = null, // Number of tokens in the response
    override val evalDuration: Long? = null, // Time spent generating the response in nanoseconds
    val context: List<Int>? = null, // Encoding of the conversation (Ollama generate)
    val message: Message? = null, // Chat
    val embeddings: List<List<Double>> = emptyList()
) : Model, Evaluation, Response

