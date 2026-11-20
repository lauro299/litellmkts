import Foundation
import Observation

/// Timing of one reply, measured by the app and refined with the server's stats when it reports them.
struct ResponseMetrics: Equatable {
    let timeToFirstToken: Duration
    let totalTime: Duration
    /// Nil when there is not enough data, e.g. a single fragment and no server stats.
    let tokensPerSecond: Double?
    /// True when the speed is estimated from the received fragments instead of the server's token count.
    let isApproximate: Bool

    /// - Parameters:
    ///   - start: when the message was sent.
    ///   - firstFragment: when the first fragment arrived; nil if none arrived.
    ///   - lastFragment: when the last fragment arrived.
    ///   - end: when the reply finished or was stopped.
    ///   - fragments: number of fragments received.
    ///   - server: stats reported by the server, if the reply finished.
    init?(start: ContinuousClock.Instant,
          firstFragment: ContinuousClock.Instant?,
          lastFragment: ContinuousClock.Instant?,
          end: ContinuousClock.Instant,
          fragments: Int,
          server: ServerStats?) {
        guard let firstFragment, let lastFragment else { return nil }
        timeToFirstToken = firstFragment - start
        totalTime = end - start
        let streaming = lastFragment - firstFragment
        if let tokens = server?.generatedTokens, let time = server?.generationTime, time > .zero {
            tokensPerSecond = Double(tokens) / time.seconds
            isApproximate = false
        } else if let tokens = server?.generatedTokens, streaming > .zero {
            tokensPerSecond = Double(tokens) / streaming.seconds
            isApproximate = false
        } else if fragments > 1, streaming > .zero {
            // n fragments span n - 1 intervals.
            tokensPerSecond = Double(fragments - 1) / streaming.seconds
            isApproximate = true
        } else {
            tokensPerSecond = nil
            isApproximate = true
        }
    }
}

private extension Duration {
    var seconds: Double {
        Double(components.seconds) + Double(components.attoseconds) / 1e18
    }
}

@MainActor
@Observable
final class ChatViewModel {
    struct Message: Identifiable {
        let id = UUID()
        let role: ChatTurn.Role
        var content: String
        var metrics: ResponseMetrics?
    }

    var serverType = ServerType.ollama
    var serverURL = "http://localhost:11436"
    /// Only sent to OpenAI-compatible servers; never stored.
    var apiKey = ""
    var draft = ""
    var selectedModel: String?
    var error: AppError?
    private(set) var models: [String] = []
    /// Why the last model request failed, shown in the model list; nil after a successful request.
    private(set) var modelsError: AppError?
    private(set) var messages: [Message] = []
    private(set) var isLoadingModels = false
    private(set) var isGenerating = false

    private let repository: any ChatRepository
    private var modelsTask: Task<Void, Never>?
    private var chatTask: Task<Void, Never>?

    init(repository: any ChatRepository) {
        self.repository = repository
    }

    var canSend: Bool {
        selectedModel != nil && !isGenerating && !draft.trimmingCharacters(in: .whitespacesAndNewlines).isEmpty
    }

    /// Points the library at the configured server and reloads the models.
    func applyServer() {
        stopGenerating()
        modelsTask?.cancel()
        do {
            let url = serverURL.trimmingCharacters(in: .whitespacesAndNewlines)
            let key = apiKey.trimmingCharacters(in: .whitespacesAndNewlines)
            try repository.configure(serverType: serverType, url: url, apiKey: key.isEmpty ? nil : key)
        } catch {
            self.error = AppError(error)
            return
        }
        loadModels()
    }

    /// Requests the model list again, keeping the selection if the model is still available.
    func loadModels() {
        modelsTask?.cancel()
        isLoadingModels = true
        error = nil
        modelsError = nil
        modelsTask = Task {
            do {
                let models = try await repository.models()
                self.models = models
                if selectedModel.map(models.contains) != true {
                    selectedModel = models.first
                }
            } catch is CancellationError {
                return
            } catch {
                let appError = AppError(error)
                self.error = appError
                modelsError = appError
                models = []
                selectedModel = nil
            }
            isLoadingModels = false
        }
    }

    /// Sends the draft with the whole conversation, streaming the reply into a new assistant message.
    func send() {
        guard canSend, let model = selectedModel else { return }
        let text = draft.trimmingCharacters(in: .whitespacesAndNewlines)
        draft = ""
        error = nil
        messages.append(Message(role: .user, content: text))
        let history = messages.map { ChatTurn(role: $0.role, content: $0.content) }
        let reply = Message(role: .assistant, content: "")
        messages.append(reply)
        isGenerating = true

        chatTask = Task {
            let clock = ContinuousClock()
            let start = clock.now
            var firstFragment: ContinuousClock.Instant?
            var lastFragment: ContinuousClock.Instant?
            var fragments = 0
            var serverStats: ServerStats?
            do {
                for try await event in repository.chat(model: model, messages: history) {
                    switch event {
                    case .token(let token):
                        let now = clock.now
                        if firstFragment == nil { firstFragment = now }
                        lastFragment = now
                        fragments += 1
                        update(reply.id) { $0.content += token }
                    case .finished(let stats):
                        serverStats = stats
                    }
                }
            } catch {
                self.error = AppError(error)
                messages.removeAll { $0.id == reply.id && $0.content.isEmpty }
            }
            // Also runs when the reply is stopped, so partial replies get their metrics.
            let metrics = ResponseMetrics(start: start, firstFragment: firstFragment, lastFragment: lastFragment,
                                          end: clock.now, fragments: fragments, server: serverStats)
            update(reply.id) { $0.metrics = metrics }
            // A stopped task must not touch the state of a reply started after it.
            if !Task.isCancelled {
                isGenerating = false
            }
        }
    }

    /// Stops the current reply, keeping the text received so far.
    func stopGenerating() {
        chatTask?.cancel()
        chatTask = nil
        isGenerating = false
    }

    private func update(_ id: Message.ID, _ change: (inout Message) -> Void) {
        guard let index = messages.firstIndex(where: { $0.id == id }) else { return }
        change(&messages[index])
    }
}
