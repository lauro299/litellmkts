package org.litellmkt.onnx

/**
 * Configuration for [OnnxChatHandler].
 *
 * @param modelName       ONNX graph file inside the model directory. Default: "model.onnx"
 * @param tokenizerName   HF tokenizer file inside the model directory. Default: "tokenizer.json"
 * @param kvCache         KV-cache strategy. Default: [KvCacheConfig.Auto]
 * @param maxNewTokens    Generation limit when not overridden per-request. Default: 256
 */
data class OnnxModelConfig(
    val modelName: String = "model.onnx",
    val tokenizerName: String = "tokenizer.json",
    val kvCache: KvCacheConfig = KvCacheConfig.Auto,
    val maxNewTokens: Int = 256,
)

/**
 * Strategy for KV-cache (past/present key-value tensors).
 *
 * - [Auto]     — detect automatically from the model's input names (recommended).
 * - [Disabled] — never use KV-cache; re-feed the full context on every token.
 * - [Enabled]  — force KV-cache on, with optional shape and naming overrides.
 */
sealed interface KvCacheConfig {

    /** Detect KV-cache support from the model's input names at runtime. */
    data object Auto : KvCacheConfig

    /**
     * Disable KV-cache even if the model has past_key_values inputs.
     * The handler will re-feed the full context each step (slower, compatible with any model).
     */
    data object Disabled : KvCacheConfig

    /**
     * Enable KV-cache with optional overrides for naming and shapes.
     *
     * @param inputPrefix  Prefix of past-cache input tensors.
     *                     Pattern: "<inputPrefix>.{layer}.key" / ".value"
     *                     Default: "past_key_values"
     * @param outputPrefix Prefix of present-cache output tensors.
     *                     Pattern: "<outputPrefix>.{layer}.key" / ".value"
     *                     Default: "present"
     * @param numKvHeads   Number of KV heads per layer. null = read from model metadata.
     * @param headDim      Size of each KV head. null = read from model metadata.
     */
    data class Enabled(
        val inputPrefix: String = "past_key_values",
        val outputPrefix: String = "present",
        val numKvHeads: Int? = null,
        val headDim: Int? = null,
    ) : KvCacheConfig
}