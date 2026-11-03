package org.litellmkt.handlers.ollama

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import org.litellmkt.params.BaseParamsModel
import org.litellmkt.params.boolean
import org.litellmkt.params.double
import org.litellmkt.params.int
import org.litellmkt.params.stringList

/**
 * Ollama `options` shared by generate, chat and embeddings. Returns null when the caller set none,
 * so the field is left out of the request. `max_tokens` is translated to `num_predict`.
 */
internal fun BaseParamsModel.toOllamaOptions(): HandlerParamsOllamaOptions? {
    val options = HandlerParamsOllamaOptions(
        numKeep = int("num_keep"),
        seed = int("seed"),
        numPredict = int("num_predict") ?: int("max_tokens"),
        topK = int("top_k"),
        topP = double("top_p"),
        minP = double("min_p"),
        tfsZ = double("tfs_z"),
        typicalP = double("typical_p"),
        repeatLastN = int("repeat_last_n"),
        temperature = double("temperature"),
        repeatPenalty = double("repeat_penalty"),
        presencePenalty = double("presence_penalty"),
        frequencyPenalty = double("frequency_penalty"),
        mirostat = int("mirostat"),
        mirostatTau = double("mirostat_tau"),
        mirostatEta = double("mirostat_eta"),
        penalizeNewline = boolean("penalize_newline"),
        stop = stringList("stop"),
        numa = boolean("numa"),
        numCtx = int("num_ctx"),
        numBatch = int("num_batch"),
        numGpu = int("num_gpu"),
        mainGpu = int("main_gpu"),
        lowVram = boolean("low_vram"),
        vocabOnly = boolean("vocab_only"),
        useMmap = boolean("use_mmap"),
        useMlock = boolean("use_mlock"),
        numThread = int("num_thread")
    )
    return options.takeIf { it != HandlerParamsOllamaOptions() }
}

@Serializable
internal data class HandlerParamsOllamaOptions(
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
    val mainGpu: Int? = null,

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
