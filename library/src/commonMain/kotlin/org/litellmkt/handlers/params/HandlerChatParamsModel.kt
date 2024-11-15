package org.litellmkt.handlers.params

import kotlinx.serialization.Serializable

sealed interface HandlerChatParamsModel : Model, Stream, Messages {
    val tools: String?
}

@Serializable
data class HandlerParamsChatOllama(
    override val model: String,
    override val messages: List<Message>?,
    override val tools: String? = null,
    override val stream: Boolean? = null,
) : HandlerChatParamsModel

