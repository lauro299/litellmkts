package org.litellmkt.handlers.results

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import org.litellmkt.handlers.params.Model

interface Response{
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

@Serializable
data class HandlerResultOllama(
    override val model: String,
    @SerialName("created_at")
    override val createdAt: String,
    override val response: String,
    override val done: Boolean,
    @SerialName("total_duration") // Time spent generating the response
    override val totalDuration: Long? = -1, // Time spent generating the response
    @SerialName("load_duration") // Time spent loading the model in nanoseconds
    override val loadDuration: Long? = -1, // Time spent loading the model in nanoseconds
    @SerialName("prompt_eval_count") // Number of tokens in the prompt
    override val promptEvalCount: Int? = -1, // Number of tokens in the prompt
    @SerialName("prompt_eval_duration") // Time spent evaluating the prompt in nanoseconds
    override val promptEvalDuration: Long? = -1, // Time spent evaluating the prompt in nanoseconds
    @SerialName("eval_count") // Number of tokens in the response
    override val evalCount: Int? = -1, // Number of tokens in the response
    @SerialName("eval_duration") // Time spent generating the response in nanoseconds
    override val evalDuration: Long? = -1, // Time spent generating the response in nanoseconds
    override val context: List<Int>? = null // Encoding of the conversation used in this response, that can be sent in the next request to keep a conversational memory
) : HandlerResultModel

