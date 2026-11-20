package org.litellmkt.onnx

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

@Serializable
internal data class TokenizerConfig(
    val model: TokenizerModel,
    @SerialName("added_tokens") val addedTokens: List<AddedToken> = emptyList()
)

@Serializable
internal data class TokenizerModel(
    val type: String = "BPE",
    val vocab: Map<String, Int> = emptyMap(),
    val merges: List<String> = emptyList()
)

@Serializable
internal data class AddedToken(
    val id: Int,
    val content: String,
    val special: Boolean = false
)