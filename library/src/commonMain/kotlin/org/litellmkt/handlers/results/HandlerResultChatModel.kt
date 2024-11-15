package org.litellmkt.handlers.results

import kotlinx.serialization.Serializable
import org.litellmkt.handlers.params.Message
import org.litellmkt.handlers.params.Model

sealed interface HandlerResultChatModel : Model, Evaluation {
    val message: Message?
}

@Serializable
class HandlerOllamaResultChatModel(
    override val model: String,
    override val message: Message?,
    override val totalDuration: Long? = null,
    override val loadDuration: Long? = null,
    override val promptEvalCount: Int? = null,
    override val promptEvalDuration: Long? = null,
    override val evalCount: Int? = null,
    override val evalDuration: Long? = null
) : HandlerResultChatModel
