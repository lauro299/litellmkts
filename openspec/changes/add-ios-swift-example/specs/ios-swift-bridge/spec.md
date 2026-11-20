# Spec Delta

## Purpose

Lets Swift code use litellmkts (list models and stream chats with Ollama or OpenAI) without dealing with Flow, suspend functions or Koin generics.

## ADDED Requirements

### Requirement: Swift initialization
The framework SHALL expose an initialization function that takes the server base URL, an optional API key and the provider (`ollama` or `openai`), and sets up the library's dependency injection. Swift SHALL be able to catch its failures with `try`.

#### Scenario: Initialization with Ollama
- **WHEN** Swift initializes with `http://localhost:11434` and provider `ollama`
- **THEN** later model listing and chat calls are sent to that server

#### Scenario: Unknown provider
- **WHEN** Swift initializes with a provider other than `ollama` or `openai`
- **THEN** the call throws an error of category `configuration` whose detail names the unsupported provider

### Requirement: Optional API key
The API key SHALL be optional for every provider, so OpenAI-compatible servers that need no key (such as llama.cpp) work; a server that requires a key and does not get a valid one answers with an HTTP error.

#### Scenario: OpenAI-compatible server without key
- **WHEN** Swift initializes with provider `openai`, no API key and a llama.cpp server URL
- **THEN** model listing and chat requests reach `/v1/models` and `/v1/chat/completions` and succeed

#### Scenario: Server that requires a key
- **WHEN** the server rejects the request with HTTP 401
- **THEN** the error callback receives category `http` with status 401

### Requirement: Reinitialization on server change
Initializing again SHALL replace the previous configuration without restarting the app.

#### Scenario: URL change
- **GIVEN** the framework initialized with server A
- **WHEN** Swift initializes again with server B
- **THEN** subsequent calls go to server B and none reach server A

### Requirement: Model listing
The framework SHALL provide an operation that delivers, through a success callback, the list of model names on the server in the provider's order.

#### Scenario: Server with models
- **WHEN** the server responds with the models `llama3.1` and `qwen2.5`
- **THEN** the success callback receives `["llama3.1", "qwen2.5"]` exactly once

### Requirement: Streaming chat
The framework SHALL provide a chat operation that takes the model and the history (role and content per message), delivers each non-empty reply fragment in order and, when done, calls the completion callback once with the generation stats reported by the server: generated tokens and generation time, each absent when the server does not report it.

#### Scenario: Reply in several fragments
- **WHEN** the server streams the fragments `Hello`, ` world` and an empty final one
- **THEN** the token callback receives `Hello` and then ` world`, followed by a single completion call

#### Scenario: Server that reports stats
- **WHEN** an Ollama server ends the stream reporting 42 generated tokens in 2,000,000,000 ns
- **THEN** the completion callback receives 42 tokens and 2,000 ms

#### Scenario: Server without stats
- **WHEN** the server ends the stream without reporting token counts or durations
- **THEN** the completion callback receives absent tokens and absent time

### Requirement: Structured errors
On any failure an operation SHALL call the error callback once and SHALL NOT call the success or completion callbacks. The error SHALL carry a category (`http`, `network`, `invalidResponse`, `provider`, `configuration`, `unknown`), the HTTP status when there is one, and an untranslated technical detail; it SHALL NOT carry user-facing text.

#### Scenario: Provider HTTP error
- **WHEN** the server answers 404 to a chat request
- **THEN** the error callback receives category `http` with status 404, and completion is not called

#### Scenario: Unreachable server
- **WHEN** the connection to the server fails
- **THEN** the error callback receives category `network` with no HTTP status

### Requirement: Callbacks on the main thread
All callbacks SHALL run on the main thread, so Swift can update the UI directly.

#### Scenario: UI update
- **WHEN** a chat fragment arrives
- **THEN** its callback runs on the main thread

### Requirement: Cancellation
Every operation SHALL return a cancellable handle; once it is cancelled, no further callbacks of that operation SHALL be called.

#### Scenario: Cancel a running chat
- **GIVEN** a chat that has already delivered some fragments
- **WHEN** Swift cancels its handle
- **THEN** no more fragments are delivered and neither completion nor error is called
