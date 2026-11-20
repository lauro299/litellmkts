import SwiftUI

/// Server type, URL and API key. Shown in the sidebar on wide screens and one step back from the chat on narrow ones.
struct ServerSettingsView: View {
    @Bindable var viewModel: ChatViewModel
    /// Called after the settings are applied, e.g. to go back to the chat on narrow screens.
    var onConnect: () -> Void = {}

    var body: some View {
        Form {
            Section {
                // Segmented when both options fit; a menu in narrow columns or with long translations.
                ViewThatFits(in: .horizontal) {
                    serverTypePicker
                        .pickerStyle(.segmented)
                        .fixedSize()
                    serverTypePicker
                        .pickerStyle(.menu)
                }
                .labelsHidden()
            }

            Section {
                // Placeholder-only fields: on macOS a Form would otherwise put the label beside the field.
                TextField(text: $viewModel.serverURL, prompt: Text(.serverUrlPlaceholder)) {
                    Text(.serverUrlPlaceholder)
                }
                .labelsHidden()
                .autocorrectionDisabled()
                #if os(iOS)
                .keyboardType(.URL)
                .textInputAutocapitalization(.never)
                #endif
                .onSubmit(connect)

                if viewModel.serverType == .openAICompatible {
                    SecureField(text: $viewModel.apiKey, prompt: Text(.serverApiKeyPlaceholder)) {
                        Text(.serverApiKeyPlaceholder)
                    }
                    .labelsHidden()
                    .onSubmit(connect)
                }
            }

            Section {
                Button(action: connect) {
                    Text(.serverConnect)
                        .frame(maxWidth: .infinity)
                }
                .buttonStyle(.borderedProminent)
            }
        }
        .formStyle(.grouped)
    }

    private var serverTypePicker: some View {
        Picker(selection: $viewModel.serverType) {
            Text(.serverTypeOllama)
                .tag(ServerType.ollama)
            Text(.serverTypeOpenAICompatible)
                .tag(ServerType.openAICompatible)
        } label: {
            Text(.serverTypeLabel)
        }
    }

    private func connect() {
        viewModel.applyServer()
        onConnect()
    }
}
