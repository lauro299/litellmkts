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
implementation("io.github.lauro299:litellmkts:0.0.3")
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
