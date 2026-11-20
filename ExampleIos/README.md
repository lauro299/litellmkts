# iOS example (Swift)

A SwiftUI chat app for iPhone, iPad and Mac that uses `litellmkts` from Swift: it connects to an Ollama or OpenAI-compatible server (such as
llama.cpp), lists its models and streams replies token by token.

```
ExampleIos/
├── shared/                 Kotlin module that builds the LitellmktsKit framework (Swift-friendly facade)
└── LitellmktsExample/      Xcode project of the SwiftUI app
```

## Requirements

- Xcode 16 or newer, with an iOS simulator runtime (Xcode › Settings › Components).
- A JDK to run Gradle (any version; Gradle downloads the toolchain it needs). Xcode does not read your shell profile,
  so the build looks for `JAVA_HOME`, then `/usr/libexec/java_home`, then Homebrew's `openjdk`
  (`brew install openjdk`).
- An [Ollama](https://ollama.com) server with at least one model (`ollama pull llama3.1`), or any OpenAI-compatible
  server such as [llama.cpp](https://github.com/ggml-org/llama.cpp)'s `llama-server`.

## Run it

1. Open `ExampleIos/LitellmktsExample/LitellmktsExample.xcodeproj`.
2. Pick an iPhone or iPad simulator, or **My Mac**, and press Run.

The same target builds the iOS and the native macOS app (Mac Catalyst is not supported by Kotlin/Native). On narrow
screens the app opens on the chat with the server settings one step back; on iPad and Mac they sit in a sidebar.

The *Compile Kotlin Framework* build phase runs `./gradlew :ExampleIos:shared:embedAndSignAppleFrameworkForXcode`, so
there is nothing to build beforehand. The first build takes a few minutes (Kotlin/Native compilation); later builds
reuse the Gradle cache.

From the command line:

```bash
xcodebuild -project ExampleIos/LitellmktsExample/LitellmktsExample.xcodeproj -scheme LitellmktsExample \
  -destination 'generic/platform=iOS Simulator' CODE_SIGNING_ALLOWED=NO build

# macOS (signed ad hoc, runs locally)
xcodebuild -project ExampleIos/LitellmktsExample/LitellmktsExample.xcodeproj -scheme LitellmktsExample \
  -destination 'platform=macOS' build
```

If `xcodebuild` says it requires Xcode, select it first: `sudo xcode-select -s /Applications/Xcode.app/Contents/Developer`.

### Which server URL to use

- **Simulator:** `http://localhost:11434` reaches the Ollama running on your Mac.
- **Device:** use your Mac's IP (`http://192.168.x.x:11434`) and start Ollama listening on all interfaces with
  `OLLAMA_HOST=0.0.0.0 ollama serve`. iOS asks for the local network permission the first time.

Plain HTTP to `localhost` and the local network is allowed by `NSAllowsLocalNetworking` in `Info.plist`.

### OpenAI-compatible servers (llama.cpp, LM Studio, vLLM)

Select **OpenAI-compatible** and enter the server's base URL, without `/v1`: for example `http://192.168.1.10:8080` for
`llama-server --host 0.0.0.0 --port 8080`. The API key is optional; leave it empty for servers started without one.
llama.cpp reports models by their GGUF path, so the model list shows only the file name. Tapping the model button
reloads the list, so models added to the server appear without reconnecting.

Reasoning models (Qwen3, DeepSeek-R1, …) stream their thinking separately before the answer. The library only reads the
answer, so the reply shows a progress indicator while the model thinks.

### Reply metrics

Below each reply the app shows the time to the first token, the total response time and the generation speed, for
example `0.9 sec to first token · 1.4 sec total · ≈ 38.3 tokens/s`. The times are measured by the app. The speed is
exact when the server reports its token count and generation time (Ollama does); otherwise it is estimated from the
received fragments and marked with `≈` (llama.cpp sends its `timings` outside the OpenAI fields, which the library does
not read yet). Stopped replies show the metrics up to the moment they were stopped.

## Using the framework from Swift

`LitellmktsKit` wraps the library in callbacks that always run on the main thread, because Kotlin `Flow`s and
`suspend` functions are awkward to consume from Swift.

```swift
import LitellmktsKit

// Starts Koin with the litellmkts module. Call it again to switch servers.
try ConfigurationKt.configureLitellmkts(baseUrl: "http://localhost:11434", apiKey: nil, provider: "ollama")
// or, for llama.cpp: configureLitellmkts(baseUrl: "http://192.168.1.10:8080", apiKey: nil, provider: "openai")

let client = LitellmktsClient()

client.listModels(
    onSuccess: { models in print(models) },
    onError: { error in print(error.category, error.detail) }
)

let handle = client.chat(
    model: "llama3.1",
    messages: [ChatMessage(role: "user", content: "Why is the sky blue?")],
    onToken: { token in print(token, terminator: "") },
    onComplete: { stats in print("\ntokens:", stats.generatedTokens?.intValue ?? 0) },
    onError: { error in print(error.category, error.httpStatus?.intValue ?? 0, error.detail) }
)
handle.cancel() // no callback of this call runs afterwards
```

[`LitellmktsClient+Async.swift`](LitellmktsExample/LitellmktsExample/core/LitellmktsClient+Async.swift) turns those callbacks
into `async`/`AsyncThrowingStream`, so the app can write `for try await event in client.chatStream(...)`, receiving
each `.token` and then `.finished` with the server's stats.

Errors are `LitellmktsException`s with a `category` (`http`, `network`, `invalidResponse`, `provider`,
`configuration`, `unknown`), the `httpStatus` when there is one and an untranslated `detail`. They carry no
user-facing text: [`AppError.swift`](LitellmktsExample/LitellmktsExample/core/AppError.swift) maps each category to a
localized description.

## Project structure

The Swift sources are grouped by feature, so each feature can grow without touching the others:

```
LitellmktsExample/LitellmktsExample/
├── app/            LitellmktsExampleApp: creates each feature's repository and injects it into its view
├── core/           Shared by features: AppError, async wrappers over LitellmktsClient
└── features/
    └── chat/       ChatRepository (protocol), LitellmktsChatRepository, ChatViewModel,
                    ChatView, ModelPicker, MessageRow, PreviewChatRepository
```

`ChatViewModel` only knows the `ChatRepository` protocol and never imports `LitellmktsKit`. The app injects
`LitellmktsChatRepository`, which talks to the Kotlin library; the SwiftUI preview injects a fake that needs no server.

### Adding a feature

1. Create `features/<feature>/` (the Xcode project picks up new folders and files by itself).
2. Declare what the feature needs in a `<Feature>Repository` protocol, using the feature's own types.
3. Implement it over `LitellmktsClient` in `Litellmkts<Feature>Repository`, reusing the wrappers in `core/`. This is the
   only file of the feature that imports `LitellmktsKit`.
4. Write the view model against the protocol and the view, then create both in `app/`.

Existing features need no changes.

## Adding a language

Every user-visible string lives in two String Catalogs, with English as the source language:

- `Localizable.xcstrings`: the UI and the error descriptions.
- `InfoPlist.xcstrings`: the app name and the local network permission prompt.

Keys are semantic identifiers (`chat.title`, `error.http.status`), and Xcode generates a symbol for each one
(`STRING_CATALOG_GENERATE_SYMBOLS`), so the code holds no user-facing text:

```swift
Text(.chatTitle)
String(localized: .errorHttpStatus(404))   // "The server returned HTTP %lld."
```

To add a text, add the key to `Localizable.xcstrings` with its English value and a comment for translators, then use the
generated symbol (`chat.empty.title` → `.chatEmptyTitle`). Using a key that is not in the catalog does not compile.

To add a language, open either catalog in Xcode, press **+** and pick the language, then translate the entries (or
export an `.xcloc` with *Product › Export Localizations…*, translate it and import it back). No code changes are
needed. Chat messages, model names and technical error details are shown as they are and never translated.

To check that no string is missing from the catalogs, run the app with the scheme's *App Language* set to
*Double-Length Pseudolanguage*: every localized string appears doubled, so any text that is not doubled bypasses the
catalog.

## Tests

The Kotlin facade is tested against a mocked server. The tests run on the Mac, no simulator needed:

```bash
./gradlew :ExampleIos:shared:macosArm64Test
```
