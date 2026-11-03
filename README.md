# litellmkts

A **Kotlin Multiplatform** library that wraps the [Ollama](https://ollama.com) REST API, enabling local LLM integration across every major platform from a single shared codebase.

`litellmkts` provides a consistent, developer-friendly interface for communicating with local language models — so you can build AI-powered features that run on-device or on a local server, without tying your code to any single platform.

## Features

- **Full multiplatform coverage** — one API, shared across:
  - Android
  - iOS (arm64, x64, and simulator)
  - JVM / Desktop
  - Linux (x64)
  - macOS (arm64, x64)
  - WebAssembly (Wasm/JS)
- **Local-first AI** — talk to local LLMs through Ollama, no cloud APIs required.
- **Idiomatic Kotlin** — coroutine- and Flow-based API for streaming responses.
- **Clean, extensible design** — a `HandlerFactory` abstraction lets you plug in different handlers and back-ends as your needs grow.
- **DI-friendly** — integrates cleanly with Koin (or any DI framework).

## Installation

### Gradle

```kotlin
implementation("io.github.lauro299:litellmkts:0.1.0-beta01")
```

### Manual

```bash
git clone https://github.com/lauro299/litellmkts.git
```

Then import the project into your development environment.

## Basic Usage

### Setup (with Koin)

```kotlin
single<ChatHandler> {
    val factory = get<HandlerFactory>()
    factory.createChatHandler("ollama")
}
```

### Sending a chat request

```kotlin
val chatHandler by inject<ChatHandler>()

chatHandler.chat(
    BaseParamsModel().also {
        it["model"] = modelName
        it["messages"] = _state.value.messages
    }
)
    .catch { onEvent(Error) }
    .map { it.message?.content ?: "" }
    .reduce { accumulator, value -> "$accumulator$value" }
```

Responses stream back as a `Flow`, so you can render tokens as they arrive.

### Configuring the transport

Declare an optional `LitellmktConfig` in your Koin module to tune timeouts and logging:

```kotlin
startKoin {
    modules(
        getLitellmktModule(),
        module {
            single(named("baseUrl")) { "http://localhost:11434" }
            single { LitellmktConfig(socketTimeoutMillis = 60_000, logLevel = LogLevel.HEADERS) }
        }
    )
}
```

| Setting | Default | Notes |
| --- | --- | --- |
| `connectTimeoutMillis` | 30 s | Time to establish the connection. |
| `socketTimeoutMillis` | 120 s | Maximum inactivity between two packets. Not supported by the Js engine. |
| `logLevel` | `LogLevel.NONE` | The `Authorization` header is always masked. |
| `logger` | `Logger.SIMPLE` | Destination of the HTTP logs. |

There is no total request timeout, so long generations are never cut off while tokens keep arriving. To bound a call,
cancel the collecting coroutine or wrap it in `withTimeout`.

### Handling errors

Every provider, HTTP and parsing failure ends the flow with a `LiteLlmException`:

```kotlin
chatHandler.chat(params)
    .catch { error ->
        when (error) {
            is ProviderHttpException -> println("HTTP ${error.status}: ${error.providerMessage}")
            is ProviderStreamException -> println("Provider error: ${error.providerMessage}")
            is ResponseParseException -> println("Unexpected response: ${error.cause}")
            else -> throw error
        }
    }
```

Invalid parameters (missing `model`, a non-numeric `temperature`, …) fail with `IllegalArgumentException` before any
request is sent. Numeric parameters accept any `Number` (`Int`, `Long`, `Float`, `Double`).

### Listing available models

```kotlin
val modelsHandler = handlerFactory.createModelsHandler(LLMProvider.Ollama) // or LLMProvider.OpenAI (needs apiKey)

modelsHandler.listModels()
    .collect { model -> println("${model.name} ${model.details?.parameterSize ?: ""}") }
```

Each `ModelInfo` always has a `name`; the other fields are `null` when the provider does not report them. Ollama
(`/api/tags`) fills `model`, `modifiedAt`, `size`, `digest` and `details`; OpenAI (`/v1/models`) fills `modifiedAt`
(creation date) and `ownedBy`. OpenAI returns every model of the account, including embedding and audio models, so
filter the list if you only need chat models.

## Migrating to 0.1.0

- **Errors:** flows used to fail with `IllegalStateException("StreamError(...)")`, or completed empty on HTTP errors. They now
  fail with `LiteLlmException` (`ProviderHttpException`, `ProviderStreamException`, `ResponseParseException`) in
  `org.litellmkt.errors`.
- **Metrics:** missing metrics in `BaseResultModel` (`totalDuration`, `evalCount`, …) are now `null` instead of `-1`.
- **Embedding handlers:** `OllamaEmbeddingHandler` and `OpenAiEmbeddingHandler` now take a `parser: Json` as first
  constructor argument (`HandlerFactory` callers are unaffected).
- **Roles:** `MessageRole.TOOL` was added; unknown roles map to `ASSISTANT` and are kept in
  `ChatResponse.metadata["original_role"]`.
- **Removed public types** (they were unused by the handlers):

  | Removed | Replacement |
  | --- | --- |
  | `StreamError`, `OllamaError`, `toStreamError()` | `LiteLlmException` subtypes |
  | `OllamaCompletionResponse`, `Choice`, `Usage`, `OllamaStreamResponse`, `toStreamResult()`, `StreamResult`, `CompletionResult` | `BaseResultModel` / `CompletionResponse` |
  | `OllamaEmbeddingsResult`, `EmbeddingsResult`, `HandlerResultOllamaEmbeddings` | `BaseResultModel.embeddings` / `EmbeddingResponse` |
  | `HandlerResultOllama`, `HandlerOllamaResultChatModel` | `BaseResultModel` |
  | `HandlerResultModel`, `HandlerEmbeddingsParamsModel`, `HandlerResultEmbeddingsModel` | `BaseResultModel` / `BaseParamsModel` |
  | `Features` | `LLMProvider` and the handler interfaces |

## Architecture

The library is organized around a `HandlerFactory` that creates the appropriate handler for each type of API functionality (chat, and extensible to others). Platform-specific networking lives in each target's source set (`androidMain`, `iosArm64Main`, `jvmMain`, `wasmJsMain`, etc.), while the shared logic and public API live in `commonMain` — the standard Kotlin Multiplatform structure that keeps a single source of truth across all platforms.

## Contributing

Contributions are welcome:

1. Fork the repository.
2. Create a branch for your feature or fix: `git checkout -b your-branch-name`
3. Make your changes and push: `git push origin your-branch-name`
4. Open a pull request.

## License

MIT — see the [`LICENSE`](LICENSE) file for details.

## Contact

- **Author:** lauro299
- **GitHub:** https://github.com/lauro299
