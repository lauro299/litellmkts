package org.litellmkt.handlers.ollama

import io.ktor.client.HttpClient
import io.ktor.client.call.body
import io.ktor.client.request.preparePost
import io.ktor.client.request.setBody
import io.ktor.http.ContentType.Application
import io.ktor.http.contentType
import io.ktor.utils.io.ByteReadChannel
import io.ktor.utils.io.readUTF8Line
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import org.koin.core.annotation.Named
import org.koin.core.annotation.Singleton
import org.litellmkt.handlers.ChatHandler
import org.litellmkt.params.BaseParamsModel
import org.litellmkt.params.Message
import org.litellmkt.results.BaseResultModel

@Singleton
class OllamaChatHandler(
    private val parser: Json,
    @Named("baseUrl") private val baseUrl: String,
    private val httpClient: HttpClient
) : ChatHandler {
    override fun chat(params: BaseParamsModel): Flow<BaseResultModel> {
        return flow {
            httpClient.preparePost(
                urlString = "${baseUrl}/api/chat"
            ) {
                contentType(Application.Json)
                setBody(params.toOllamaChat())
            }.execute { httpResponse ->
                val channel: ByteReadChannel = httpResponse.body()
                while (!channel.isClosedForRead) {
                    channel.readUTF8Line()
                        ?.runCatching {
                            parser.decodeFromString<HandlerOllamaResultChatModel>(this)
                        }?.onFailure {
                            error(StreamError(error = it.message ?: ""))
                        }?.getOrThrow()
                        ?.let {
                            emit(it.toBaseResultModel())
                        }
                }
            }
        }
    }

}

internal fun HandlerOllamaResultChatModel.toBaseResultModel(): BaseResultModel {
    return BaseResultModel(
        model = model,
        createdAt = "",
        response = message?.content ?: "",
        done = false,
        message = message
    )
}

internal fun BaseParamsModel.toOllamaGeneration(): HandlerParamsOllama {
    val options = HandlerParamsOllamaOptions(
        numKeep = get("num_keep") as? Int,
        seed = get("seed") as? Int,
        numPredict = get("num_predict") as? Int,
        topK = get("top_k") as? Int,
        topP = get("top_p") as? Double,
        minP = get("min_p") as? Double,
        tfsZ = get("tfs_z") as? Double,
        typicalP = get("typical_p") as? Double,
        repeatLastN = get("repeat_last_n") as? Int,
        temperature = get("temperature") as? Double,
        repeatPenalty = get("repeat_penalty") as? Double,
        presencePenalty = get("presence_penalty") as? Double,
        frequencyPenalty = get("frequency_penalty") as? Double,
        mirostat = get("mirostat") as? Int,
        mirostatTau = get("mirostat_tau") as? Double,
        mirostatEta = get("mirostat_eta") as? Double,
        penalizeNewline = get("penalize_newline") as? Boolean,
        stop = get("stop") as? List<String>,
        numa = get("numa") as? Boolean,
        numCtx = get("num_ctx") as? Int,
        numBatch = get("num_batch") as? Int,
        numGpu = get("num_gpu") as? Int,
        mainGpu = get("main_gpu") as? Int ?: 0,
        lowVram = get("low_vram") as? Boolean,
        vocabOnly = get("vocab_only") as? Boolean,
        useMmap = get("use_mmap") as? Boolean,
        useMlock = get("use_mlock") as? Boolean,
        numThread = get("num_thread") as? Int
    )

    return HandlerParamsOllama(
        model = get("model") as String,
        prompt = get("prompt") as? String ?: throw IllegalArgumentException("Invalid prompt"),
        suffix = get("suffix") as? String,
        images = get("images") as? List<String>,
        format = get("format") as? String,
        options = options,
        system = get("system") as? String,
        template = get("template") as? String,
        stream = get("stream") as? Boolean,
        raw = get("raw") as? Boolean,
        keepAlive = get("keep_alive") as? Long
    )
}

internal fun BaseParamsModel.toOllamaChat(): RequestOllamaChat {
    return RequestOllamaChat(
        model = model ?: throw IllegalArgumentException("Invalid Model"),
        stream = stream,
        tools = null,
        messages = get("messages") as? List<Message>
    )
}

@Serializable
internal data class RequestOllamaChat(
    val model: String,
    val messages: List<Message>?,
    val tools: String? = null,
    val stream: Boolean? = null,
)

@Serializable
internal data class HandlerParamsOllama(
    val model: String,
    val prompt: String,
    val suffix: String? = null,
    val images: List<String>? = null,
    val format: String? = null,
    val options: HandlerParamsOllamaOptions? = null,
    val system: String? = null,
    val template: String? = null,
    val stream: Boolean? = null,
    val raw: Boolean? = null,
    val keepAlive: Long? = null
)


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
class HandlerOllamaResultChatModel(
    val model: String,
    val message: Message?,
    val totalDuration: Long? = null,
    val loadDuration: Long? = null,
    val promptEvalCount: Int? = null,
    val promptEvalDuration: Long? = null,
    val evalCount: Int? = null,
    val evalDuration: Long? = null
)
