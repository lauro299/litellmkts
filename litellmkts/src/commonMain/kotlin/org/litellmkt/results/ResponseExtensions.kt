package org.litellmkt.results

import org.litellmkt.types.ChatMessage
import org.litellmkt.types.FinishReason
import org.litellmkt.types.MessageRole
import org.litellmkt.types.PerformanceMetrics
import org.litellmkt.types.TokenUsage
import kotlin.random.Random

/**
 * Extension functions to convert BaseResultModel to enhanced response models
 */

fun BaseResultModel.toChatResponse(): ChatResponse {
    return ChatResponse(
        id = generateId(),
        model = model,
        content = response,
        message = message?.let { 
            ChatMessage(
                role = MessageRole.valueOf(it.role.uppercase()),
                content = it.content,
                images = it.images
            )
        },
        finishReason = when {
            done -> FinishReason.STOP
            else -> null
        },
        usage = createTokenUsage(),
        performance = createPerformanceMetrics(),
        metadata = createMetadata(),
        isStreaming = !done,
        isDone = done,
        createdAt = parseCreatedAt()
    )
}

fun BaseResultModel.toCompletionResponse(): CompletionResponse {
    return CompletionResponse(
        id = generateId(),
        model = model,
        text = response,
        finishReason = when {
            done -> FinishReason.STOP
            else -> null
        },
        usage = createTokenUsage(),
        performance = createPerformanceMetrics(),
        metadata = createMetadata(),
        isStreaming = !done,
        isDone = done,
        createdAt = parseCreatedAt()
    )
}

fun BaseResultModel.toEmbeddingResponse(): EmbeddingResponse {
    return EmbeddingResponse(
        id = generateId(),
        model = model,
        embeddings = embeddings ?: emptyList(),
        usage = createTokenUsage(),
        performance = createPerformanceMetrics(),
        metadata = createMetadata(),
        createdAt = parseCreatedAt()
    )
}

private fun BaseResultModel.createTokenUsage(): TokenUsage? {
    val promptTokens = promptEvalCount ?: return null
    val completionTokens = evalCount ?: return null
    return TokenUsage(
        promptTokens = promptTokens,
        completionTokens = completionTokens,
        totalTokens = promptTokens + completionTokens
    )
}

private fun BaseResultModel.createPerformanceMetrics(): PerformanceMetrics {
    return PerformanceMetrics(
        totalDuration = totalDuration,
        loadDuration = loadDuration,
        promptEvalCount = promptEvalCount,
        promptEvalDuration = promptEvalDuration,
        evalCount = evalCount,
        evalDuration = evalDuration
    )
}

private fun BaseResultModel.createMetadata(): Map<String, String> {
    val metadata = mutableMapOf<String, String>()
    
    context?.let { metadata["context_size"] = it.size.toString() }
    if (createdAt.isNotEmpty()) metadata["created_at"] = createdAt
    
    return metadata
}

private fun BaseResultModel.parseCreatedAt(): Long {
    return try {
        if (createdAt.isNotEmpty() && createdAt.toLongOrNull() != null) {
            createdAt.toLong()
        } else {
            kotlinx.datetime.Clock.System.now().toEpochMilliseconds()
        }
    } catch (e: Exception) {
        kotlinx.datetime.Clock.System.now().toEpochMilliseconds()
    }
}

/**
 * Wrapper functions to create API responses
 */
fun <T> T.toApiResponse(requestId: String? = null): ApiResponse<T> {
    return ApiResponse(
        success = true,
        data = this,
        requestId = requestId
    )
}

fun createErrorResponse(code: String, message: String, details: Map<String, String> = emptyMap()): ApiResponse<Nothing> {
    return ApiResponse(
        success = false,
        error = ApiError(code, message, details)
    )
}

private fun generateId(): String {
    return "req_${Random.nextLong().toString(36)}_${Random.nextLong().toString(36)}"
}