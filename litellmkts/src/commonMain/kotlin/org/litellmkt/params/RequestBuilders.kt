package org.litellmkt.params

import org.litellmkt.types.ChatMessage
import org.litellmkt.types.MessageRole

/**
 * Builder for chat completion requests
 */
class ChatRequestBuilder {
    private var model: String? = null
    private var messages: MutableList<ChatMessage> = mutableListOf()
    private var temperature: Double? = null
    private var maxTokens: Int? = null
    private var topP: Double? = null
    private var frequencyPenalty: Double? = null
    private var presencePenalty: Double? = null
    private var stop: List<String>? = null
    private var stream: Boolean = true
    private var user: String? = null
    private val additionalParams = mutableMapOf<String, Any>()

    fun model(model: String) = apply { 
        this.model = model 
    }

    fun message(role: MessageRole, content: String, images: List<String>? = null) = apply {
        messages.add(ChatMessage(role, content, images))
    }

    fun systemMessage(content: String) = apply {
        message(MessageRole.SYSTEM, content)
    }

    fun userMessage(content: String, images: List<String>? = null) = apply {
        message(MessageRole.USER, content, images)
    }

    fun assistantMessage(content: String) = apply {
        message(MessageRole.ASSISTANT, content)
    }

    fun temperature(temperature: Double) = apply { 
        require(temperature in 0.0..2.0) { "Temperature must be between 0.0 and 2.0" }
        this.temperature = temperature 
    }

    fun maxTokens(maxTokens: Int) = apply { 
        require(maxTokens > 0) { "Max tokens must be positive" }
        this.maxTokens = maxTokens 
    }

    fun topP(topP: Double) = apply { 
        require(topP in 0.0..1.0) { "Top P must be between 0.0 and 1.0" }
        this.topP = topP 
    }

    fun frequencyPenalty(penalty: Double) = apply { 
        require(penalty in -2.0..2.0) { "Frequency penalty must be between -2.0 and 2.0" }
        this.frequencyPenalty = penalty 
    }

    fun presencePenalty(penalty: Double) = apply { 
        require(penalty in -2.0..2.0) { "Presence penalty must be between -2.0 and 2.0" }
        this.presencePenalty = penalty 
    }

    fun stop(stopSequences: List<String>) = apply { 
        this.stop = stopSequences 
    }

    fun stop(vararg stopSequences: String) = apply { 
        this.stop = stopSequences.toList() 
    }

    fun streaming(enabled: Boolean) = apply { 
        this.stream = enabled 
    }

    fun user(userId: String) = apply { 
        this.user = userId 
    }

    fun additionalParam(key: String, value: Any) = apply {
        additionalParams[key] = value
    }

    fun build(): BaseParamsModel {
        requireNotNull(model) { "Model is required" }
        require(messages.isNotEmpty()) { "At least one message is required" }

        return BaseParamsModel().apply {
            put("model", model!!)
            put("messages", messages.map { 
                Message(
                    role = it.role.value,
                    content = it.content,
                    images = it.images
                )
            })
            temperature?.let { put("temperature", it) }
            maxTokens?.let { put("max_tokens", it) }
            topP?.let { put("top_p", it) }
            frequencyPenalty?.let { put("frequency_penalty", it) }
            presencePenalty?.let { put("presence_penalty", it) }
            stop?.let { put("stop", it) }
            put("stream", stream as Any)
            user?.let { put("user", it) }
            additionalParams.forEach { (key, value) -> put(key, value) }
        }
    }
}

/**
 * Builder for completion requests
 */
class CompletionRequestBuilder {
    private var model: String? = null
    private var prompt: String? = null
    private var temperature: Double? = null
    private var maxTokens: Int? = null
    private var topP: Double? = null
    private var frequencyPenalty: Double? = null
    private var presencePenalty: Double? = null
    private var stop: List<String>? = null
    private var stream: Boolean = true
    private var suffix: String? = null
    private var user: String? = null
    private val additionalParams = mutableMapOf<String, Any>()

    fun model(model: String) = apply { 
        this.model = model 
    }

    fun prompt(prompt: String) = apply { 
        this.prompt = prompt 
    }

    fun temperature(temperature: Double) = apply { 
        require(temperature in 0.0..2.0) { "Temperature must be between 0.0 and 2.0" }
        this.temperature = temperature 
    }

    fun maxTokens(maxTokens: Int) = apply { 
        require(maxTokens > 0) { "Max tokens must be positive" }
        this.maxTokens = maxTokens 
    }

    fun topP(topP: Double) = apply { 
        require(topP in 0.0..1.0) { "Top P must be between 0.0 and 1.0" }
        this.topP = topP 
    }

    fun frequencyPenalty(penalty: Double) = apply { 
        require(penalty in -2.0..2.0) { "Frequency penalty must be between -2.0 and 2.0" }
        this.frequencyPenalty = penalty 
    }

    fun presencePenalty(penalty: Double) = apply { 
        require(penalty in -2.0..2.0) { "Presence penalty must be between -2.0 and 2.0" }
        this.presencePenalty = penalty 
    }

    fun stop(stopSequences: List<String>) = apply { 
        this.stop = stopSequences 
    }

    fun stop(vararg stopSequences: String) = apply { 
        this.stop = stopSequences.toList() 
    }

    fun streaming(enabled: Boolean) = apply { 
        this.stream = enabled 
    }

    fun suffix(suffix: String) = apply { 
        this.suffix = suffix 
    }

    fun user(userId: String) = apply { 
        this.user = userId 
    }

    fun additionalParam(key: String, value: Any) = apply {
        additionalParams[key] = value
    }

    fun build(): BaseParamsModel {
        requireNotNull(model) { "Model is required" }
        requireNotNull(prompt) { "Prompt is required" }

        return BaseParamsModel().apply {
            put("model", model!!)
            put("prompt", prompt!!)
            temperature?.let { put("temperature", it) }
            maxTokens?.let { put("max_tokens", it) }
            topP?.let { put("top_p", it) }
            frequencyPenalty?.let { put("frequency_penalty", it) }
            presencePenalty?.let { put("presence_penalty", it) }
            stop?.let { put("stop", it) }
            put("stream", stream as Any)
            suffix?.let { put("suffix", it) }
            user?.let { put("user", it) }
            additionalParams.forEach { (key, value) -> put(key, value) }
        }
    }
}

/**
 * Builder for embedding requests
 */
class EmbeddingRequestBuilder {
    private var model: String? = null
    private var input: MutableList<String> = mutableListOf()
    private var encodingFormat: String? = null
    private var dimensions: Int? = null
    private var user: String? = null
    private var truncate: Boolean? = null
    private val additionalParams = mutableMapOf<String, Any>()

    fun model(model: String) = apply { 
        this.model = model 
    }

    fun input(text: String) = apply { 
        this.input.add(text) 
    }

    fun input(texts: List<String>) = apply { 
        this.input.addAll(texts) 
    }

    fun input(vararg texts: String) = apply { 
        this.input.addAll(texts) 
    }

    fun encodingFormat(format: String) = apply { 
        this.encodingFormat = format 
    }

    fun dimensions(dimensions: Int) = apply { 
        require(dimensions > 0) { "Dimensions must be positive" }
        this.dimensions = dimensions 
    }

    fun user(userId: String) = apply { 
        this.user = userId 
    }

    fun truncate(truncate: Boolean) = apply { 
        this.truncate = truncate 
    }

    fun additionalParam(key: String, value: Any) = apply {
        additionalParams[key] = value
    }

    fun build(): BaseParamsModel {
        requireNotNull(model) { "Model is required" }
        require(input.isNotEmpty()) { "At least one input text is required" }

        return BaseParamsModel().apply {
            put("model", model!!)
            put("input", input.toList())
            encodingFormat?.let { put("encoding_format", it) }
            dimensions?.let { put("dimensions", it) }
            user?.let { put("user", it) }
            truncate?.let { put("truncate", it) }
            additionalParams.forEach { (key, value) -> put(key, value) }
        }
    }
}

/**
 * DSL functions for creating builders
 */
fun chatRequest(block: ChatRequestBuilder.() -> Unit): BaseParamsModel {
    return ChatRequestBuilder().apply(block).build()
}

fun completionRequest(block: CompletionRequestBuilder.() -> Unit): BaseParamsModel {
    return CompletionRequestBuilder().apply(block).build()
}

fun embeddingRequest(block: EmbeddingRequestBuilder.() -> Unit): BaseParamsModel {
    return EmbeddingRequestBuilder().apply(block).build()
}