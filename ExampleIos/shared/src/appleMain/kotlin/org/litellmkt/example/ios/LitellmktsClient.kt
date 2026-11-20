package org.litellmkt.example.ios

import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancelChildren
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.toList
import kotlinx.coroutines.launch
import org.koin.core.Koin
import org.koin.core.component.KoinComponent
import org.koin.core.component.get
import org.koin.mp.KoinPlatformTools
import org.litellmkt.handlers.HandlerFactory
import org.litellmkt.params.BaseParamsModel
import org.litellmkt.params.Message
import org.litellmkt.types.LLMProvider

/** A chat message as seen from Swift. [role] is `user`, `assistant` or `system`. */
data class ChatMessage(val role: String, val content: String)

/**
 * Generation stats reported by the server at the end of a reply; each value is null when the server does not report it.
 *
 * @property generatedTokens tokens of the reply (Ollama `eval_count`, OpenAI `usage.completion_tokens`).
 * @property generationMillis time spent generating them (Ollama `eval_duration`).
 */
data class ChatStats(val generatedTokens: Int?, val generationMillis: Long?)

/** Handle returned by every call so Swift can stop it (e.g. when the user taps stop or the view disappears). */
interface Cancellable {
    fun cancel()
}

/**
 * Swift-friendly facade over litellmkts.
 *
 * Kotlin `Flow`s and suspend functions are awkward to consume from Swift, so this class exposes plain callbacks that
 * run on the main thread. Once a call is cancelled none of its callbacks run. Errors are [LitellmktsException]s
 * carrying an [ErrorCategory], so the app can localize them.
 *
 * Dependencies come from the Koin started by [configureLitellmkts] and are resolved on every call, so a client keeps
 * working after the server is reconfigured.
 */
class LitellmktsClient internal constructor(dispatcher: CoroutineDispatcher) : KoinComponent {

    constructor() : this(Dispatchers.Main)

    private val scope = CoroutineScope(SupervisorJob() + dispatcher)

    override fun getKoin(): Koin = KoinPlatformTools.defaultContext().getOrNull()
        ?: throw LitellmktsException(ErrorCategory.CONFIGURATION, null, "configureLitellmkts() has not been called")

    /** Fetches the names of the models available on the server, in the provider's order. */
    fun listModels(
        onSuccess: (List<String>) -> Unit,
        onError: (LitellmktsException) -> Unit
    ): Cancellable = launch(onError) {
        val models = get<HandlerFactory>().createModelsHandler(get<LLMProvider>())
            .listModels()
            .map { it.name }
            .toList()
        ensureActive()
        onSuccess(models)
    }

    /**
     * Streams a chat completion. [onToken] receives each non-empty chunk as it arrives, then [onComplete] is called
     * once with the stats the server reported. On failure only [onError] is called.
     */
    fun chat(
        model: String,
        messages: List<ChatMessage>,
        onToken: (String) -> Unit,
        onComplete: (ChatStats) -> Unit,
        onError: (LitellmktsException) -> Unit
    ): Cancellable = launch(onError) {
        val params = BaseParamsModel().also {
            it["model"] = model
            it["messages"] = messages.map { message -> Message(role = message.role, content = message.content) }
        }
        var generatedTokens: Int? = null
        var generationNanos: Long? = null
        get<HandlerFactory>().createChatHandler(get<LLMProvider>())
            .chat(params)
            .collect { chunk ->
                // Servers report the stats in the last chunk only.
                chunk.evalCount?.let { generatedTokens = it }
                chunk.evalDuration?.let { generationNanos = it }
                val token = chunk.message?.content
                if (!token.isNullOrEmpty()) {
                    ensureActive()
                    onToken(token)
                }
            }
        ensureActive()
        onComplete(ChatStats(generatedTokens, generationNanos?.let { it / 1_000_000 }))
    }

    /** Cancels every running call of this client. */
    fun cancelAll() {
        scope.coroutineContext.cancelChildren()
    }

    private fun launch(
        onError: (LitellmktsException) -> Unit,
        block: suspend CoroutineScope.() -> Unit
    ): Cancellable {
        val job = scope.launch {
            try {
                block()
            } catch (e: CancellationException) {
                throw e
            } catch (e: Throwable) {
                ensureActive()
                onError(e.toLitellmktsException())
            }
        }
        return object : Cancellable {
            override fun cancel() = job.cancel()
        }
    }
}
