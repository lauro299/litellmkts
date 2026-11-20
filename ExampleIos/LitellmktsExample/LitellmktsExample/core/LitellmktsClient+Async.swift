import LitellmktsKit

/// What a streamed chat emits: reply chunks, then the stats reported by the server.
enum LitellmktsChatEvent {
    case token(String)
    case finished(ChatStats)
}

/// Swift concurrency wrappers over the callback API of `LitellmktsClient`.
///
/// Cancelling the calling task (or stopping iteration) cancels the Kotlin call.
extension LitellmktsClient {
    /// Names of the models available on the server. Throws `AppError` or `CancellationError`.
    func models() async throws -> [String] {
        let stream = AsyncThrowingStream<[String], Error> { continuation in
            let handle = listModels(
                onSuccess: { models in
                    continuation.yield(models)
                    continuation.finish()
                },
                onError: { continuation.finish(throwing: AppError($0)) }
            )
            continuation.onTermination = { _ in handle.cancel() }
        }
        for try await models in stream {
            return models
        }
        throw CancellationError()
    }

    /// Reply chunks for `messages`, in order, then `.finished`. Throws `AppError` on failure.
    func chatStream(model: String, messages: [ChatMessage]) -> AsyncThrowingStream<LitellmktsChatEvent, Error> {
        AsyncThrowingStream { continuation in
            let handle = chat(
                model: model,
                messages: messages,
                onToken: { continuation.yield(.token($0)) },
                onComplete: { stats in
                    continuation.yield(.finished(stats))
                    continuation.finish()
                },
                onError: { continuation.finish(throwing: AppError($0)) }
            )
            continuation.onTermination = { _ in handle.cancel() }
        }
    }
}
