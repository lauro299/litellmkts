package org.litellmkt.types

import kotlinx.serialization.Serializable

/**
 * Sealed class representing different LLM providers
 */
sealed class LLMProvider {
    abstract val name: String
    
    object Ollama : LLMProvider() {
        override val name: String = "ollama"
    }
    
    object OpenAI : LLMProvider() {
        override val name: String = "openai"
    }
    
    data class Custom(override val name: String) : LLMProvider()
}

/**
 * Enum representing different finish reasons for completions
 */
@Serializable
enum class FinishReason {
    STOP,
    LENGTH,
    FUNCTION_CALL,
    CONTENT_FILTER,
    NULL
}

/**
 * Enum representing different message roles
 */
@Serializable
enum class MessageRole {
    SYSTEM,
    USER,
    ASSISTANT,
    FUNCTION;
    
    val value: String get() = name.lowercase()
}

/**
 * Data class for token usage statistics
 */
@Serializable
data class TokenUsage(
    val promptTokens: Int,
    val completionTokens: Int,
    val totalTokens: Int
)

/**
 * Data class for performance metrics
 */
@Serializable
data class PerformanceMetrics(
    val totalDuration: Long? = null,
    val loadDuration: Long? = null,
    val promptEvalCount: Int? = null,
    val promptEvalDuration: Long? = null,
    val evalCount: Int? = null,
    val evalDuration: Long? = null
)

/**
 * Enhanced message data class with better type safety
 */
@Serializable
data class ChatMessage(
    val role: MessageRole,
    val content: String?,
    val images: List<String>? = null,
    val functionCall: FunctionCall? = null
)

/**
 * Function call data class for function calling capabilities
 */
@Serializable
data class FunctionCall(
    val name: String,
    val arguments: String
)