# Proposal

## Why

The library declares iOS targets (`iosArm64`, `iosX64`, `iosSimulatorArm64`), but the only example (`Example/`) is a JVM console app. There is no sample showing how to consume it from Swift, which is exactly where KMP has friction: `Flow`, `suspend` functions, Koin generics and Kotlin types are not natural to use from Swift.

## What Changes

- New Gradle module `:ExampleIos:shared` (Kotlin Multiplatform, Apple targets only) that produces a static `LitellmktsKit` framework for Swift.
- Koin initialization callable from Swift: starts `getLitellmktModule()` with the server URL (and optional API key) and can be restarted when the server changes.
- Swift-friendly facade over the library: model listing and streaming chat exposed as callbacks delivered on the main thread, with a cancellable handle, and errors reported as structured categories so the app can localize them.
- New SwiftUI iOS app (`ExampleIos/LitellmktsExample`, Xcode project) that configures the server URL, lists the available models, lets the user pick one and chat while the reply is shown token by token.
- The Swift app is organized by feature (`app/`, `core/`, `features/chat/`), and each feature's view model talks to the bridge only through its own repository protocol, so new features can be added as new folders without touching existing ones.
- The app is ready for additional languages: every user-visible string lives in String Catalogs with English as the source language, so adding a language only requires adding translations.
- The same app target also runs natively on macOS (Apple Silicon and Intel), sharing all Swift code; the Kotlin module builds `LitellmktsKit` for macOS too. Mac Catalyst is not used because Kotlin/Native has no Catalyst target.
- The Xcode project builds the Kotlin framework automatically on every build (Gradle script phase), with no manual steps beforehand.
- Documentation: `ExampleIos/README.md` and an "iOS (Swift)" section in the root `README.md`.
- Out of scope: the `litellmkts-onnx` module (it has no iOS target), publishing the framework (SPM/CocoaPods), shipping translations other than English, and changes to the public API of `litellmkts`.

## Capabilities

### New Capabilities
- `ios-swift-bridge`: API that the `LitellmktsKit` framework exposes to Swift (initialization, model listing, streaming chat, cancellation and structured errors) and its threading contract.
- `ios-example-app`: behavior of the SwiftUI example app (server configuration, model selection, streaming conversation, error handling, localization) and how it is built.

### Modified Capabilities
<!-- None: there are no existing specs and the litellmkts API does not change. -->

## Impact

- `settings.gradle.kts`: includes `:ExampleIos:shared`. Since CI runs `./gradlew iosSimulatorArm64Test` across all projects, the new module's tests run there automatically.
- New files under `ExampleIos/` (Kotlin, Swift, Xcode project, Info.plist, String Catalogs, README).
- Dependencies: none new; reuses `koin-core`, `kotlinx-coroutines-core`, `kotlin-test` and `ktor-client-mock` (tests) from the version catalog.
- `litellmkts` and `litellmkts-onnx` are unchanged.
