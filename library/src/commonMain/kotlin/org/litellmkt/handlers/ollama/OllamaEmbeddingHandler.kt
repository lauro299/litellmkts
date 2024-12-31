package org.litellmkt.handlers.ollama

import io.ktor.client.HttpClient
import io.ktor.client.call.body
import io.ktor.client.request.post
import io.ktor.client.request.setBody
import io.ktor.http.ContentType.Application
import io.ktor.http.contentType
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.map
import kotlinx.serialization.Serializable
import org.litellmkt.handlers.EmbeddingHandler
import org.litellmkt.params.BaseParamsModel
import org.litellmkt.results.BaseResultModel

class OllamaEmbeddingHandler(
    private val baseUrl: String,
    private val httpClient: HttpClient
) : EmbeddingHandler {

    override fun embeddings(params: BaseParamsModel): Flow<BaseResultModel> {
        return flow {
            emit(
                httpClient.post(
                    urlString = "$baseUrl/api/embed"
                ) {
                    contentType(Application.Json)
                    setBody(params.toOllamaEmbeddingsParam())
                }.body<HandlerResultOllamaEmbeddings>()
            )
        }.map {
            it.toBaseResultModel()
        }
    }
}

internal fun BaseParamsModel.toOllamaEmbeddingsParam(): HandlerEmbeddingsOllamaParams {
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
    return HandlerEmbeddingsOllamaParams(
        model = model ?: throw IllegalArgumentException("Invalid model"),
        input = get("input") as? List<String> ?: throw IllegalArgumentException("Invalid Input"),
        truncate = get("truncate") as? Boolean,
        options = options
    )
}

@Serializable
internal data class HandlerEmbeddingsOllamaParams(
    val model: String,
    val input: List<String>,
    val truncate: Boolean? = null,
    //val options: Map<String, String>? = null,
    val options: HandlerParamsOllamaOptions? = null
)

@Serializable
data class HandlerResultOllamaEmbeddings(
    val model: String,
    val embeddings: List<List<Double>>,
    val totalDuration: Long? = null,
    val loadDuration: Long? = null,
    val promptEvalCount: Int? = null,
    val promptEvalDuration: Long? = null,
    val evalCount: Int? = null,
    val evalDuration: Long? = null
)


internal fun HandlerResultOllamaEmbeddings.toBaseResultModel(): BaseResultModel {
    return BaseResultModel(
        model = model,
        createdAt = "",
        response = "",
        done = false,
        totalDuration = totalDuration,
        loadDuration = loadDuration,
        promptEvalCount = promptEvalCount,
        promptEvalDuration = promptEvalDuration,
        evalCount = evalCount,
        evalDuration = evalDuration,
        embeddings = embeddings
    )
}
