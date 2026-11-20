#if DEBUG
/// Answers with canned data after short delays, so previews need no server and show the loaders.
struct PreviewChatRepository: ChatRepository {
    func configure(serverType: ServerType, url: String, apiKey: String?) throws {}

    func models() async throws -> [String] {
        try await Task.sleep(for: .seconds(1))
        return ["llama3.1", "/models/qwen/Qwen3-8B-Q4_K_M.gguf"]
    }

    func chat(model: String, messages: [ChatTurn]) -> AsyncThrowingStream<ChatEvent, Error> {
        AsyncThrowingStream { continuation in
            let task = Task {
                try await Task.sleep(for: .milliseconds(600))
                for word in "Rayleigh scattering makes shorter wavelengths spread more.".split(separator: " ") {
                    try await Task.sleep(for: .milliseconds(150))
                    continuation.yield(.token(word + " "))
                }
                // Like llama.cpp: no stats, so the speed is shown as approximate.
                continuation.yield(.finished(ServerStats(generatedTokens: nil, generationTime: nil)))
                continuation.finish()
            }
            continuation.onTermination = { _ in task.cancel() }
        }
    }
}
#endif
