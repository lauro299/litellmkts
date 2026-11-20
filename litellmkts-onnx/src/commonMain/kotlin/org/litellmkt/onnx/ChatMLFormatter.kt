package org.litellmkt.onnx

import org.litellmkt.params.BaseParamsModel
import org.litellmkt.params.Message

/**
 * Formats prompts using the ChatML template used by Phi-3, Mistral-instruct, and similar models.
 *
 * Chat format:
 *   <|system|>\n...<|end|>\n<|user|>\n...<|end|>\n<|assistant|>\n
 *
 * Generation format: raw prompt string as-is.
 */
internal object ChatMLFormatter {

    fun formatChat(params: BaseParamsModel): String {
        @Suppress("UNCHECKED_CAST")
        val messages = (params["messages"] as? List<*>)
            ?.filterIsInstance<Message>()
            ?: emptyList()
        return buildString {
            for (msg in messages) {
                append("<|${msg.role}|>\n")
                append(msg.content ?: "")
                append("<|end|>\n")
            }
            append("<|assistant|>\n")
        }
    }

    fun formatCompletion(params: BaseParamsModel): String =
        params["prompt"] as? String ?: ""
}