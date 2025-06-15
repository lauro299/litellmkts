package org.litellmkt.results

import kotlinx.serialization.Serializable
import org.litellmkt.types.ChatMessage
import org.litellmkt.types.FinishReason
import org.litellmkt.types.PerformanceMetrics
import org.litellmkt.types.TokenUsage

/**
 * Enhanced chat response with comprehensive information
 */
@Serializable
data class ChatResponse(
    val id: String,
    val model: String,
    val content: String,
    val message: ChatMessage? = null,
    val finishReason: FinishReason? = null,
    val usage: TokenUsage? = null,
    val performance: PerformanceMetrics? = null,
    val metadata: Map<String, String> = emptyMap(),
    val isStreaming: Boolean = false,
    val isDone: Boolean = false,
    val createdAt: Long = kotlinx.datetime.Clock.System.now().toEpochMilliseconds()
)

/**
 * Enhanced completion response for text generation
 */
@Serializable
data class CompletionResponse(
    val id: String,
    val model: String,
    val text: String,
    val finishReason: FinishReason? = null,
    val usage: TokenUsage? = null,
    val performance: PerformanceMetrics? = null,
    val metadata: Map<String, String> = emptyMap(),
    val isStreaming: Boolean = false,
    val isDone: Boolean = false,
    val createdAt: Long = kotlinx.datetime.Clock.System.now().toEpochMilliseconds()
)

/**
 * Enhanced embedding response with comprehensive information
 */
@Serializable
data class EmbeddingResponse(
    val id: String,
    val model: String,
    val embeddings: List<List<Double>>,
    val usage: TokenUsage? = null,
    val performance: PerformanceMetrics? = null,
    val metadata: Map<String, String> = emptyMap(),
    val createdAt: Long = kotlinx.datetime.Clock.System.now().toEpochMilliseconds()
)

/**
 * Generic API response wrapper
 */
@Serializable
data class ApiResponse<T>(
    val success: Boolean,
    val data: T? = null,
    val error: ApiError? = null,
    val requestId: String? = null,
    val timestamp: Long = kotlinx.datetime.Clock.System.now().toEpochMilliseconds()
)

/**
 * API error information
 */
@Serializable
data class ApiError(
    val code: String,
    val message: String,
    val details: Map<String, String> = emptyMap()
)