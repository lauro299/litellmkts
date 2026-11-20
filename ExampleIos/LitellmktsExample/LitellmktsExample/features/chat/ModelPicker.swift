import SwiftUI

/// Toolbar button with the selected model. Tapping it reloads the list and shows it in a popover.
struct ModelPicker: View {
    @Bindable var viewModel: ChatViewModel
    @State private var isPresented = false

    var body: some View {
        Button {
            isPresented = true
            viewModel.loadModels()
        } label: {
            HStack(spacing: 4) {
                if let selected = viewModel.selectedModel {
                    Text(verbatim: Self.displayName(of: selected))
                        .lineLimit(1)
                        .truncationMode(.middle)
                } else {
                    Text(.modelsNone)
                }
                Image(systemName: "chevron.down")
                    .font(.caption)
            }
        }
        .accessibilityLabel(Text(.modelsLabel))
        .popover(isPresented: $isPresented) {
            // A fixed size from the first frame: sizing the popover from content that is still loading left it blank.
            list
                .frame(width: 280)
                .frame(minHeight: 120)
                .presentationCompactAdaptation(.popover)
        }
    }

    @ViewBuilder
    private var list: some View {
        if viewModel.isLoadingModels {
            HStack(spacing: 8) {
                ProgressView()
                    .controlSize(.small)
                Text(.modelsLoading)
                    .foregroundStyle(.secondary)
            }
            .padding()
        } else if let error = viewModel.modelsError {
            Label {
                Text(verbatim: error.errorDescription ?? "")
            } icon: {
                Image(systemName: "exclamationmark.triangle.fill")
                    .foregroundStyle(.orange)
            }
            .font(.subheadline)
            .padding()
        } else if viewModel.models.isEmpty {
            Text(.modelsEmpty)
                .foregroundStyle(.secondary)
                .padding()
        } else {
            ScrollView {
                VStack(alignment: .leading, spacing: 0) {
                    ForEach(viewModel.models, id: \.self) { model in
                        Button {
                            viewModel.selectedModel = model
                            isPresented = false
                        } label: {
                            HStack {
                                Text(verbatim: Self.displayName(of: model))
                                    .lineLimit(1)
                                    .truncationMode(.middle)
                                Spacer(minLength: 16)
                                if model == viewModel.selectedModel {
                                    Image(systemName: "checkmark")
                                }
                            }
                            .padding(.horizontal)
                            .padding(.vertical, 10)
                            .contentShape(Rectangle())
                        }
                        .buttonStyle(.plain)
                    }
                }
            }
            .frame(maxHeight: 320)
            .padding(.vertical, 8)
        }
    }

    /// llama.cpp reports models by file path; the last component is enough to tell them apart.
    static func displayName(of model: String) -> String {
        model.split(separator: "/").last.map(String.init) ?? model
    }
}
