# Design

## Context

- `litellmkts` already builds for `iosArm64`, `iosX64` and `iosSimulatorArm64` (Ktor Darwin engine in `appleMain`), and all its configuration goes through Koin: `getLitellmktModule()` needs a `String` named `baseUrl`, optionally `apiKey` and `LitellmktConfig`, and exposes `HandlerFactory`.
- Swift sees the Kotlin API through the Objective-C export: `Flow`s cannot be iterated, `suspend` functions become completion handlers without cancellation, `koin.get<T>()` (reified) is unavailable, Kotlin default arguments do not exist in Swift, and Kotlin exceptions only reach Swift as `throws` when declared with `@Throws`.
- Library errors are `LiteLlmException` subtypes (`ProviderHttpException` with `status`, `ProviderStreamException`, `ResponseParseException`) whose messages are English and partly come from the server; Ktor network failures are `kotlinx.io.IOException`s.
- The repo has no Xcode project and no project generators (XcodeGen, Tuist). On the development machine Xcode 27.2 is installed (a simulator runtime is being installed), and there is no system JDK (Homebrew `openjdk` is available).
- CI (`.github/workflows/gradle.yml`) runs `./gradlew iosSimulatorArm64Test` on `macos-latest`, which runs that task in every included project.

## Goals / Non-Goals

**Goals:**
- Show the standard KMP-with-Koin pattern on iOS: initialization from Swift, dependency resolution on the Kotlin side.
- Keep the bridging layer small, free of new dependencies and easy to copy into a real app.
- Make the facade testable locally without a simulator.
- Keep all user-facing text on the Swift side so the app can be translated without touching Kotlin.

**Non-Goals:**
- Publishing `LitellmktsKit` as an SPM package or CocoaPod.
- Generation (`GenerationHandler`), embeddings or ONNX support in the app.
- Persisting the conversation or the configuration across launches.
- UI tests (XCUITest) and translations other than English.

## Decisions

### 1. Global Koin initialized from Swift
A top-level Kotlin function `configureLitellmkts(baseUrl, apiKey, provider)` calls `stopKoin()` if an instance already exists and then `startKoin { modules(getLitellmktModule(), module { baseUrl, apiKey?, LLMProvider }) }`. Swift calls it at app launch and every time the user applies new settings. For `openai`, a missing key is registered as an empty string, because `HandlerFactory` requires one for that provider while local OpenAI-compatible servers (llama.cpp, LM Studio, vLLM) need none; the request then carries an empty bearer token, which those servers ignore.

The module also redefines `HandlerFactory` (with `allowOverride(true)`), passing `apiKey` explicitly. The library's generated definition omits it because Koin Annotations skips constructor parameters with default values (`apiKey: String? = null`), so registering a named `apiKey` has no effect. Fixing that in the library is out of scope here and belongs in its own change. It is annotated `@Throws(LitellmktsException::class)` so Swift sees it as `throws` (unknown provider → category `configuration`). The name avoids `initKoin` because the Objective-C export prefixes functions starting with `init` with `do` (`doInitKoin`).

The `LitellmktsClient` facade is a `KoinComponent` that resolves `HandlerFactory` and `LLMProvider` with `get()` **on every call**, not with `by inject()`: a lazy `inject` would keep the instance from the previous Koin after reconfiguration.

Before `stopKoin()` the current `HttpClient` is closed (`getKoin().getOrNull<HttpClient>()?.close()`), since the library declares it as `@Single` without `onClose` and it would otherwise stay open.

*Rejected alternative:* an isolated `koinApplication {}` per client (the first draft). It works, but departs from the `startKoin` pattern documented in the README and from how Koin is integrated into a real KMP app.

### 2. Main-thread callbacks + `Cancellable` handle
Each operation takes lambdas (`onSuccess`/`onToken`/`onComplete`/`onError`) and returns a `Cancellable` (a Kotlin interface, a protocol in Swift). Internally it launches a coroutine on a `CoroutineScope(SupervisorJob() + dispatcher)` whose default dispatcher is `Dispatchers.Main`. `CancellationException` is rethrown and never reaches `onError`. `ensureActive()` is checked before each callback, so a cancelled operation calls nothing else.

In Swift, an extension wraps `chat` in an `AsyncThrowingStream<String, Error>` (cancelling the handle in `onTermination`) and `listModels` in `async throws`; the chat repository (§11) consumes them, so the rest of the app only sees `async` APIs.

*Rejected alternatives:* SKIE or KMP-NativeCoroutines provide automatic `Flow`→`AsyncSequence`, but add a compiler plugin and hide exactly the pattern the example wants to teach.

### 3. Structured errors instead of messages
Kotlin maps every failure to `LitellmktsException(category, httpStatus, detail)`. `category` is an enum `ErrorCategory` (`HTTP`, `NETWORK`, `INVALID_RESPONSE`, `PROVIDER`, `CONFIGURATION`, `UNKNOWN`), `httpStatus` is an `Int?` (exported to Swift as `KotlinInt?`), and `detail` is the original exception message. Mapping: `ProviderHttpException` → `HTTP` + status; `ProviderStreamException` → `PROVIDER`; `ResponseParseException` → `INVALID_RESPONSE`; `kotlinx.io.IOException` (connection, DNS, timeouts) → `NETWORK`; `IllegalArgumentException` → `CONFIGURATION`; anything else → `UNKNOWN`. `onError` receives this object; the bridge produces no user-facing text.

*Rejected alternative:* passing `e.message` as a `String` (the first draft). It is simpler, but the message is English, mixes server text with library text, and cannot be translated.

### 4. Injectable dispatcher for tests
`LitellmktsClient` has a public no-argument constructor (for Swift) that uses `Dispatchers.Main`, and an `internal` constructor that takes the `CoroutineDispatcher`. Tests use `Dispatchers.Default` and wait with `CompletableDeferred` inside `runBlocking`. `Dispatchers.Main` would deadlock, because the test blocks the main thread.

### 5. Tests in `appleTest` with a `macosArm64` target
The module also declares `macosArm64()`, without a framework, only so that `./gradlew :ExampleIos:shared:macosArm64Test` runs the tests on the Mac without a simulator. The same tests run in CI as `iosSimulatorArm64Test`. The server is simulated by overriding `HttpClientLocalEngine` (a public interface of the library) with a Ktor `MockEngine` (`ktor-client-mock`, already in the catalog), through an `internal` overload of `configureLitellmkts` that accepts extra modules. The tests use `kotlin-test`.

### 6. Xcode integration via `embedAndSignAppleFrameworkForXcode`
The app target has a *Run Script* phase before Swift compilation that runs `./gradlew :ExampleIos:shared:embedAndSignAppleFrameworkForXcode`. The task reads Xcode's `CONFIGURATION`, `SDK_NAME` and `ARCHS` and puts the framework in `ExampleIos/shared/build/xcode-frameworks/$(CONFIGURATION)/$(SDK_NAME)`, which is added to `FRAMEWORK_SEARCH_PATHS` together with `OTHER_LDFLAGS = -framework LitellmktsKit`. The framework is static (`isStatic = true`), so it does not need to be embedded or signed. `ENABLE_USER_SCRIPT_SANDBOXING = NO` is required so the script can write to `build/`.

Xcode does not inherit the shell environment, so the script looks for a JDK in this order: `JAVA_HOME`, `/usr/libexec/java_home`, Homebrew `openjdk`. If none is found, it fails with a clear message. Gradle then downloads the toolchain 21 declared in `gradle-daemon-jvm.properties`.

*Rejected alternative:* building an XCFramework and consuming it through a local SPM package (`binaryTarget`). It requires running Gradle by hand before opening Xcode and breaks the "One-step build" requirement.

### 7. Hand-written Xcode project with synchronized folders
`project.pbxproj` uses `objectVersion = 77` and a `PBXFileSystemSynchronizedRootGroup` for the source folder (Xcode 16+), so the project does not list files: adding a Swift file or a string catalog requires no `pbxproj` change. A shared scheme (`xcshareddata/xcschemes/LitellmktsExample.xcscheme`) is committed so `xcodebuild -scheme` works on a clean checkout.

*Rejected alternative:* XcodeGen/Tuist. The project would be more readable, but one more tool would be needed just to open the example.

### 8. SwiftUI app, iOS 17+
An `@Observable` view model that depends only on `ChatRepository` (§11), and a single screen: a settings bar with a segmented server-type picker (Ollama / OpenAI-compatible), the URL, a `SecureField` for the API key (only for OpenAI-compatible, not persisted) and an apply button, a model button that opens a popover (the list is reloaded on every tap, with a small `ProgressView` while it loads, because a `Picker` or `Menu` gives no hook for when it opens), a message list with auto-scroll, and a text field with a send/stop button. The assistant's reply is appended as an empty message and extended with each token. The picker shows the last path component of each model id, since llama.cpp reports ids as GGUF file paths; requests use the full id. Reasoning models stream their thinking separately (`reasoning_content`), which the library ignores, so the reply shows a progress indicator until the answer starts.

### 9. Localization with String Catalogs
- `Localizable.xcstrings` for the UI and `InfoPlist.xcstrings` for `NSLocalNetworkUsageDescription`; `developmentRegion = en`, `knownRegions = (en, Base)`, and the build settings `LOCALIZATION_PREFERS_STRING_CATALOGS = YES` and `SWIFT_EMIT_LOC_STRINGS = YES`, so Xcode extracts strings into the catalog on every build.
- Keys are semantic identifiers grouped by area (`chat.title`, `chat.message.placeholder`, `error.http.status`), each with its English value and a translator comment in the catalog. With `STRING_CATALOG_GENERATE_SYMBOLS = YES`, Xcode generates a `LocalizedStringResource` symbol per key (`.chatTitle`, `.errorHttpStatus(_:)` for format strings), so the code contains no user-facing text (`Text(.chatTitle)`, `String(localized: .errorHttpStatus(status))`) and referencing a missing key fails to compile.
- Dynamic content is shown with `Text(verbatim:)`, so it is never looked up in the catalog: chat messages, model names, the URL and the technical error detail.
- A Swift `AppError: LocalizedError` wraps `LitellmktsException` and maps each `ErrorCategory` to a localized `errorDescription`, keeping `detail` as `failureReason`; the error view shows both.
- Adding a language = adding it in the catalog editor (or importing an `.xcloc`) and translating; no code changes.

*Rejected alternative:* the English source text as key (Apple's default extraction). It needs no manual catalog entries, but the code then reads as hardcoded text and a typo silently creates a new, untranslated key.

### 10. Local HTTP access
A custom `Info.plist` (merged with the generated one, `GENERATE_INFOPLIST_FILE = YES`) with `NSAppTransportSecurity > NSAllowsLocalNetworking = YES` and `NSLocalNetworkUsageDescription` (its text localized via `InfoPlist.xcstrings`). In the simulator, `localhost` reaches the Mac's Ollama. On a device the Mac's IP must be used and Ollama started with `OLLAMA_HOST=0.0.0.0`, as the README will state.

### 11. Feature-based structure with repositories
The Swift sources are grouped by responsibility, in lowercase folders under the synchronized root (so no `pbxproj` change is needed):

- `app/`: the entry point (`LitellmktsExampleApp`), the composition root that creates each feature's repository and injects it into its view.
- `core/`: code shared by features: `AppError` and the `LitellmktsClient+Async` wrappers.
- `features/chat/`: the chat feature.
  - `ChatRepository` protocol: `configure(serverType:url:apiKey:) throws`, `models() async throws -> [String]` and `chat(model:messages:) -> AsyncThrowingStream<String, Error>`, using its own `ChatTurn` type, so the view model never imports `LitellmktsKit`.
  - `LitellmktsChatRepository`: the implementation over `configureLitellmkts` and `LitellmktsClient`.
  - `ChatViewModel`, which receives a `ChatRepository` in its initializer, and `ChatView`.

A new feature is a new `features/<name>/` folder with its own repository protocol and implementation, wired in `app/`; existing features are not touched. The Kotlin module is unchanged: repositories wrap `LitellmktsClient`.

*Rejected alternative:* a single app-wide repository. It is simpler today, but it grows with every feature and couples features that share nothing.

### 13. Adaptive layout with `NavigationSplitView`
- `NavigationSplitView(columnVisibility:preferredCompactColumn:)` with `preferredCompactColumn = .detail`: the sidebar holds the server settings (`ServerSettingsView`: type, URL, API key, Connect) and the detail holds the chat. SwiftUI collapses it into a stack on compact widths (iPhone, Slide Over), opening on the chat with a back button to the settings, and shows both columns on regular widths (iPad, Mac). The column visibility starts at `.all`, so iPads show the short settings sidebar in portrait too; users can still hide it.
- The settings column has a minimum width (280 pt) so its controls never squeeze; the server-type picker is segmented when both options fit and a menu otherwise (`ViewThatFits`), and the fields are placeholder-only with hidden labels, because a macOS `Form` would put the label beside the field.
- The model button (with its loading popover) stays in the chat toolbar on every size, so switching models never needs the sidebar.
- The message list and input bar are capped at a readable width (`frame(maxWidth: 760)`, centered) and bubbles keep a margin on the opposite side, so wide screens do not stretch lines edge to edge.
- Views avoid iOS-only APIs, or guard them with `#if os(iOS)` (`navigationBarTitleDisplayMode`, `keyboardType`, `textInputAutocapitalization`, UIKit colors; toolbar placement `.primaryAction` instead of `.topBarTrailing`), so the feature compiles for macOS as-is; adding a macOS destination is a separate change.

*Rejected alternative:* switching between two hand-written layouts by size class. It keeps today's iPhone screen, but duplicates the screen structure and gives up the navigation SwiftUI provides for free.

### 14. Native macOS destination
- The app target lists `macosx` in `SUPPORTED_PLATFORMS` (with `SDKROOT = auto`, macOS 14+), so one target builds the iOS and the macOS app from the same sources; the views already avoid iOS-only APIs (§13).
- The Kotlin module builds the static `LitellmktsKit` framework for `macosArm64` and `macosX64` as well; `embedAndSignAppleFrameworkForXcode` picks the slice from the `macosx` SDK.
- macOS needs the App Sandbox with outgoing connections (`ENABLE_APP_SANDBOX`, `ENABLE_OUTGOING_NETWORK_CONNECTIONS`), and is signed ad hoc (`CODE_SIGN_IDENTITY[sdk=macosx*] = -`) so it runs locally without a team.

*Rejected alternatives:* Mac Catalyst, because Kotlin/Native has no Catalyst target; running the iPad app on Mac ("Designed for iPad"), because it keeps iOS behaviors and needs device signing.

### 12. Response metrics
- Kotlin: `onComplete` receives `ChatStats(generatedTokens: Int?, generationMillis: Long?)` from the last chunk's `evalCount` and `evalDuration` (Ollama reports both; OpenAI-compatible servers report `usage.completion_tokens` only when they send usage, and no duration). llama.cpp's own `timings` object is not read by the library; parsing it belongs in a separate library change.
- Swift: `ChatRepository.chat` yields `ChatEvent.token(String)` and `ChatEvent.finished(ServerStats)`. `ChatViewModel` measures with `ContinuousClock` (send → first fragment → last fragment, plus fragment count), so metrics also work with the preview repository and for stopped replies. Speed = server tokens / server time when both exist; else server tokens / (last − first fragment); else fragments / (last − first fragment), flagged approximate. Fewer than two fragments → no speed.
- Display: one caption line under the assistant message, built from catalog format strings (`chat.metrics.firstToken`, `chat.metrics.total`, `chat.metrics.speed`, `chat.metrics.speedApprox`) with durations formatted by `Duration.formatted` and numbers by `FloatingPointFormatStyle`, both locale-aware.

## Risks / Trade-offs

- [The simulator runtime may not be ready when implementing] → The automatic verification is `xcodebuild build` against `generic/platform=iOS Simulator` plus the `macosArm64` tests; the manual run in the simulator is a separate task.
- [Xcode is not selected with `xcode-select`] → Verification commands use `DEVELOPER_DIR=/Applications/Xcode.app/Contents/Developer`; the README mentions `sudo xcode-select -s` as an alternative.
- [`NSAllowsLocalNetworking` might not cover literal-IP URLs on every iOS version] → Checked during the manual test; if blocked, the README documents adding a per-domain exception or `NSAllowsArbitraryLoads` for Debug only.
- [`stopKoin()` with requests in flight] → In-flight coroutines may fail on reconfiguration; the app cancels its handles before calling `configureLitellmkts`, and an error, if one arrives, is shown like any other.
- [Strings created outside SwiftUI without `String(localized:)` would silently skip the catalog] → The `exportLocalizations` check in tasks plus a pass with the double-length pseudolanguage, which makes untranslated strings stand out.
- [The first Xcode build is slow (toolchain download and Kotlin/Native compilation)] → Documented in the README; later builds use the Gradle cache.
- [Including the module in `settings.gradle.kts` adds work to CI's global tasks (`iosSimulatorArm64Test`)] → Small cost (one module with a few tests), and in exchange CI covers the example.
