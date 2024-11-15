package org.litellmkt.params

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

interface Model {
    val model: String
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

interface Format{
    val format: String?
}

sealed interface HandlerParamsModel : Model, Prompt, Stream, Format {
    val suffix: String?
    val images: List<String>?
    val system: String?
    val template: String?

    //val context: HandlerParamsModelContext? = null,
    val raw: Boolean?
    val keepAlive: Long?
}

@Serializable
data class HandlerParamsOllama(
    override val model: String,
    override val prompt: String,
    override val suffix: String? = null,
    override val images: List<String>? = null,
    override val format: String? = null,
    val options: HandlerParamsOllamaOptions? = null,
    override val system: String? = null,
    override val template: String? = null,
    override val stream: Boolean? = null,
    override val raw: Boolean? = null,
    override val keepAlive: Long? = null
) : HandlerParamsModel

@Serializable
data class HandlerParamsOllamaOptions(
    @SerialName("num_keep") // Number of keep warm-up steps
    val numKeep: Int? = null,

    @SerialName("seed") // Random seed for reproducibility
    val seed: Int? = null,

    @SerialName("num_predict") // Number of predictions to make
    val numPredict: Int? = null,

    @SerialName("top_k") // Top-k filtering
    val topK: Int? = null,

    @SerialName("top_p") // Top-p filtering
    val topP: Double? = null,

    @SerialName("min_p") // Minimum p-value for valid candidates
    val minP: Double? = null,

    @SerialName("tfs_z") // Temperature scaling factor
    val tfsZ: Double? = null,

    @SerialName("typical_p") // Typical probability threshold
    val typicalP: Double? = null,

    @SerialName("repeat_last_n") // Repeat last N tokens
    val repeatLastN: Int? = null,

    @SerialName("temperature") // Temperature for sampling
    val temperature: Double? = null,

    @SerialName("repeat_penalty") // Repeat penalty factor
    val repeatPenalty: Double? = null,

    @SerialName("presence_penalty") // Presence penalty factor
    val presencePenalty: Double? = null,

    @SerialName("frequency_penalty") // Frequency penalty factor
    val frequencyPenalty: Double? = null,

    @SerialName("mirostat") // Mirostat parameter
    val mirostat: Int? = null,

    @SerialName("mirostat_tau") // Mirostat tau parameter
    val mirostatTau: Double? = null,

    @SerialName("mirostat_eta") // Mirostat eta parameter
    val mirostatEta: Double? = null,

    @SerialName("penalize_newline") // Penalize newlines
    val penalizeNewline: Boolean? = null,

    @SerialName("stop") // Stop tokens
    val stop: List<String>? = null,

    @SerialName("numa") // Use numa
    val numa: Boolean? = null,

    @SerialName("num_ctx") // Number of context slots
    val numCtx: Int? = null,

    @SerialName("num_batch") // Number of batches
    val numBatch: Int? = null,

    @SerialName("num_gpu") // Number of GPUs
    val numGpu: Int? = null,

    @SerialName("main_gpu") // Main GPU index
    val mainGpu: Int? = 0,

    @SerialName("low_vram") // Low VRAM flag
    val lowVram: Boolean? = null,

    @SerialName("vocab_only") // Use only vocabulary
    val vocabOnly: Boolean? = null,

    @SerialName("use_mmap") // Use mmap
    val useMmap: Boolean? = null,

    @SerialName("use_mlock") // Use mlock
    val useMlock: Boolean? = null,

    @SerialName("num_thread") // Number of threads
    val numThread: Int? = null
)

@Serializable
data class Message(
    val role: String,
    val content: String?,
    val images: List<String>? = null
)