package org.litellmkt.handlers.openAi

import io.ktor.client.HttpClient
import io.ktor.client.call.body
import io.ktor.client.request.header
import io.ktor.client.request.preparePost
import io.ktor.client.request.setBody
import io.ktor.client.statement.bodyAsText
import io.ktor.http.ContentType.Application
import io.ktor.http.HttpHeaders
import io.ktor.http.contentType
import io.ktor.utils.io.ByteReadChannel
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import org.litellmkt.handlers.GenerationHandler
import org.litellmkt.handlers.internal.decodeProviderChunk
import org.litellmkt.handlers.internal.ensureSuccess
import org.litellmkt.handlers.internal.forEachLine
import org.litellmkt.handlers.internal.sseData
import org.litellmkt.params.BaseParamsModel
import org.litellmkt.params.boolean
import org.litellmkt.params.double
import org.litellmkt.params.int
import org.litellmkt.params.requireModel
import org.litellmkt.params.string
import org.litellmkt.params.stringList
import org.litellmkt.results.BaseResultModel

class OpenAiGenerationHandler(
    private val parser: Json,
    private val baseUrl: String,
    private val httpClient: HttpClient,
    private val apiKey: String
) : GenerationHandler {
    override fun stream(params: BaseParamsModel): Flow<BaseResultModel> {
        return flow {
            val request = params.toOpenAiCompletion()
            httpClient.preparePost(
                "${baseUrl}/v1/completions"
            ) {
                contentType(Application.Json)
                header(HttpHeaders.Authorization, "Bearer $apiKey")
                setBody(request)
            }.execute { httpResponse ->
                httpResponse.ensureSuccess()
                if (request.stream) {
                    httpResponse.body<ByteReadChannel>().forEachLine { line ->
                        val data = sseData(line) ?: return@forEachLine
                        emit(parser.decodeProviderChunk<OpenAiCompletionResponse>(data).toBaseResultModel())
                    }
                } else {
                    emit(
                        parser.decodeProviderChunk<OpenAiCompletionResponse>(httpResponse.bodyAsText())
                            .toBaseResultModel(complete = true)
                    )
                }
            }
        }
    }
}

/**
 * @param complete true for a non-streamed response, which is always the final result.
 */
internal fun OpenAiCompletionResponse.toBaseResultModel(complete: Boolean = false): BaseResultModel {
    val choice = choices?.firstOrNull()
    return BaseResultModel(
        model = model,
        createdAt = created.toString(),
        response = choice?.text ?: "",
        done = complete || choice?.finishReason != null,
        promptEvalCount = usage?.promptTokens,
        evalCount = usage?.completionTokens
    )
}

internal fun BaseParamsModel.toOpenAiCompletion(): OpenAiCompletionRequest {
    return OpenAiCompletionRequest(
        model = requireModel(),
        prompt = string("prompt") ?: throw IllegalArgumentException("Missing required parameter 'prompt'"),
        stream = boolean("stream") ?: true,
        temperature = double("temperature"),
        maxTokens = int("max_tokens"),
        topP = double("top_p"),
        frequencyPenalty = double("frequency_penalty"),
        presencePenalty = double("presence_penalty"),
        stop = stringList("stop"),
        suffix = string("suffix"),
        user = string("user")
    )
}

@Serializable
internal data class OpenAiCompletionRequest(
    val model: String,
    val prompt: String,
    // No default value: encodeDefaults = false would otherwise drop "stream": true from the body.
    val stream: Boolean,
    val temperature: Double? = null,
    @SerialName("max_tokens")
    val maxTokens: Int? = null,
    @SerialName("top_p")
    val topP: Double? = null,
    @SerialName("frequency_penalty")
    val frequencyPenalty: Double? = null,
    @SerialName("presence_penalty")
    val presencePenalty: Double? = null,
    val stop: List<String>? = null,
    val suffix: String? = null,
    val user: String? = null
)

@Serializable
internal data class OpenAiCompletionResponse(
    val id: String,
    @SerialName("object")
    val objectType: String,
    val created: Long,
    val model: String,
    val choices: List<OpenAiCompletionChoice>?,
    val usage: OpenAiUsage? = null
)

@Serializable
internal data class OpenAiCompletionChoice(
    val text: String? = null,
    val index: Int,
    @SerialName("finish_reason")
    val finishReason: String? = null
)
