/// Kind of server the chat talks to.
enum ServerType: CaseIterable, Identifiable {
    case ollama
    /// Any server with the OpenAI API, such as llama.cpp, LM Studio or vLLM.
    case openAICompatible

    var id: Self { self }
}

/// One message of the conversation sent to the model.
struct ChatTurn {
    enum Role: String {
        case user
        case assistant
    }

    let role: Role
    let content: String
}

/// Generation stats reported by the server; each value is nil when the server does not report it.
struct ServerStats {
    let generatedTokens: Int?
    let generationTime: Duration?
}

/// What a reply stream emits: chunks of the reply, then the server's stats.
enum ChatEvent {
    case token(String)
    case finished(ServerStats)
}

/// Everything the chat feature needs from a language model server.
///
/// The view model only knows this protocol, so it can be backed by litellmkts in the app and by a fake in previews.
protocol ChatRepository {
    /// Points the repository at a server. Cancel running calls before calling it.
    func configure(serverType: ServerType, url: String, apiKey: String?) throws

    /// Names of the models available on the server.
    func models() async throws -> [String]

    /// Reply chunks for `messages`, in order, followed by `.finished`.
    func chat(model: String, messages: [ChatTurn]) -> AsyncThrowingStream<ChatEvent, Error>
}
