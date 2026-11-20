# Spec Delta

## Purpose

SwiftUI example iOS app that shows how to integrate litellmkts into a Swift project: configure the server, pick a model and chat with a streamed reply, with a UI ready to be translated.

## ADDED Requirements

### Requirement: Server configuration
The app SHALL let the user choose the server type (Ollama, the default, or OpenAI-compatible), edit the server URL (default `http://localhost:11434`) and, for OpenAI-compatible servers, enter an optional API key; applying the settings SHALL reconfigure the client without restarting and reload the model list.

#### Scenario: Change the URL
- **WHEN** the user enters `http://192.168.1.10:11434` and applies it
- **THEN** the app reconfigures the client with that URL and requests the models again

#### Scenario: llama.cpp server
- **WHEN** the user selects OpenAI-compatible, enters `http://192.168.1.10:11436`, leaves the API key empty and applies it
- **THEN** the app lists the server's models and can chat with them

#### Scenario: API key field
- **WHEN** the server type is Ollama
- **THEN** the API key field is hidden, and no key is sent

### Requirement: Model selection
The app SHALL show the server's models in a picker and preselect the first one; while no model is selected, sending messages SHALL be disabled.

#### Scenario: Models loaded
- **WHEN** the server returns `llama3.1` and `qwen2.5`
- **THEN** the picker shows both and `llama3.1` is selected

#### Scenario: Model ids that are file paths
- **WHEN** the server reports a model id such as `/models/qwen/Qwen3-8B-Q4_K_M.gguf`
- **THEN** the picker shows `Qwen3-8B-Q4_K_M.gguf` and requests use the full id

#### Scenario: Reload when opening the picker
- **WHEN** the user taps the model picker
- **THEN** the app requests the model list again, shows a small loading indicator until it arrives, then lists the models with the current one marked, keeping the selection if the model is still available

#### Scenario: Model list while the server does not answer
- **WHEN** the user taps the model picker and the server is slow or unreachable
- **THEN** the list shows the loading indicator from the moment it opens and, if the request fails, the localized error instead of an empty list

#### Scenario: Server without models
- **WHEN** the server returns an empty list
- **THEN** the app states that there are no models and the send button is disabled

### Requirement: Streaming conversation
When a message is sent, the app SHALL append it to the history, send the full history to the selected model and show the assistant's reply growing as fragments arrive.

#### Scenario: Question and answer
- **WHEN** the user sends `Why is the sky blue?`
- **THEN** the message appears in the history and the assistant's reply appears below it, filling in until it finishes

#### Scenario: Conversation context
- **GIVEN** a history with one question and its answer
- **WHEN** the user sends a second question
- **THEN** the request contains the three messages in order

### Requirement: One reply at a time
While a reply is being generated, the app SHALL disable sending and SHALL offer to stop it; stopping SHALL keep the text received so far.

#### Scenario: Stop generation
- **WHEN** the user taps stop during a reply
- **THEN** no more text is added, the partial reply is kept and sending is enabled again

### Requirement: Adaptive layout
The app SHALL adapt to the available width: on narrow screens (iPhone) it SHALL open on the chat with the server settings one navigation step away; on wide screens (iPad, Mac window) it SHALL show the server settings in a sidebar next to the chat. Messages and the input SHALL keep a readable maximum width.

#### Scenario: iPhone
- **WHEN** the app opens on an iPhone
- **THEN** the chat fills the screen and a back button leads to the server settings

#### Scenario: iPad
- **WHEN** the app opens on an iPad in landscape
- **THEN** the server settings appear in a sidebar and the chat beside it

#### Scenario: Wide window
- **WHEN** the chat area is much wider than a line of text
- **THEN** messages and the input are limited to a readable width and centered

### Requirement: Response metrics
When a reply finishes or is stopped, the app SHALL show below it the time to the first token, the total response time and the generation speed in tokens per second. The speed SHALL use the server-reported tokens and time when available; otherwise it SHALL be estimated from the fragments received between the first and the last one and marked as approximate.

#### Scenario: Server with stats
- **WHEN** a reply from an Ollama server finishes and the server reports 42 tokens in 2 s
- **THEN** the reply shows its time to first token, its total time and `21.0` tokens per second, not marked as approximate

#### Scenario: Server without stats
- **WHEN** a reply from a server that reports no stats finishes after 51 fragments that arrived over 2 s
- **THEN** the reply shows `25.0` tokens per second marked as approximate

#### Scenario: Stopped reply
- **WHEN** the user stops a reply after some fragments arrived
- **THEN** the reply shows the metrics measured up to that point

#### Scenario: Localized metrics
- **WHEN** the device language has translations in the catalog
- **THEN** the metric labels and number formats follow that language

### Requirement: Visible errors
If model listing or chat fails, the app SHALL show a localized description of the error category (including the HTTP status when present), followed by the untranslated technical detail, and return to a state where the user can retry.

#### Scenario: Unreachable server
- **WHEN** the URL points to a server that does not respond
- **THEN** the app shows the localized network error with its detail and lets the user change the URL and retry

#### Scenario: HTTP error
- **WHEN** the chat request fails with HTTP 404
- **THEN** the app shows the localized HTTP error mentioning 404, followed by the server's message

### Requirement: Localizable user interface
Every user-visible string, including the system permission texts, SHALL come from String Catalogs with English as the source language. Adding a language SHALL only require adding translations to the catalogs, without code changes. Chat content, model names and technical error details SHALL NOT be translated.

#### Scenario: Translated language
- **GIVEN** the catalogs contain Spanish translations
- **WHEN** the device language is Spanish
- **THEN** every label, button, placeholder, error description and the local network permission prompt appear in Spanish

#### Scenario: Language without translations
- **WHEN** the device language has no translations in the catalogs
- **THEN** the UI appears in English

#### Scenario: No string outside the catalog
- **WHEN** localizations are exported from the project
- **THEN** the exported English catalog contains every user-visible string of the app

### Requirement: Local HTTP access
The app SHALL be able to connect over plain HTTP (no TLS) to servers on `localhost` and on the local network, which is how Ollama is usually served.

#### Scenario: Ollama on the local network
- **WHEN** the URL is `http://<local-network-ip>:11434`
- **THEN** iOS allows the connection (after the local network permission is granted on a device) and App Transport Security does not block it

### Requirement: One-step build
Opening the Xcode project and building SHALL produce the Kotlin framework and the app with no prior manual commands, as long as a JDK is available.

#### Scenario: Clean build for the simulator
- **WHEN** the app scheme is built for the iOS simulator from a clean checkout
- **THEN** the build compiles the Kotlin framework, links it and finishes without errors
