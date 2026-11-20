import LitellmktsKit

/// `ChatRepository` backed by the litellmkts Kotlin library.
final class LitellmktsChatRepository: ChatRepository {
    private let client = LitellmktsClient()

    func configure(serverType: ServerType, url: String, apiKey: String?) throws {
        client.cancelAll()
        switch serverType {
        case .ollama:
            try ConfigurationKt.configureLitellmkts(baseUrl: url, apiKey: nil, provider: "ollama")
        case .openAICompatible:
            try ConfigurationKt.configureLitellmkts(baseUrl: url, apiKey: apiKey, provider: "openai")
        }
    }

    func models() async throws -> [String] {
        try await client.models()
    }

    func chat(model: String, messages: [ChatTurn]) -> AsyncThrowingStream<ChatEvent, Error> {
        let events = client.chatStream(
            model: model,
            messages: messages.map { ChatMessage(role: $0.role.rawValue, content: $0.content) }
        )
        return AsyncThrowingStream { continuation in
            let task = Task {
                do {
                    for try await event in events {
                        switch event {
                        case .token(let token):
                            continuation.yield(.token(token))
                        case .finished(let stats):
                            continuation.yield(.finished(ServerStats(
                                generatedTokens: stats.generatedTokens?.intValue,
                                generationTime: stats.generationMillis.map { .milliseconds($0.int64Value) }
                            )))
                        }
                    }
                    continuation.finish()
                } catch {
                    continuation.finish(throwing: error)
                }
            }
            continuation.onTermination = { _ in task.cancel() }
        }
    }
}
