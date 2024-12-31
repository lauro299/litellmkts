package org.litellmkt.params

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

interface Model {
    val model: String?
}

interface Prompt {
    val prompt: String?
}

interface Stream {
    val stream: Boolean?
}

interface Messages {
    val messages: List<Message>?
}

interface Format {
    val format: String?
}

@Serializable
data class Message(
    val role: String,
    val content: String?,
    val images: List<String>? = null
)

class BaseParamsModel : Model, Stream, MutableMap<String, Any> by HashMap() {
    override val model: String? by lazy { get("model") as String? }
    override val stream: Boolean? by lazy { get("stream") as Boolean? }
}
