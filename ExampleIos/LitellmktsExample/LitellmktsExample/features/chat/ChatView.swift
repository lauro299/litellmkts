import SwiftUI

/// Readable width for the message column on wide screens.
private let maxContentWidth: CGFloat = 760

struct ChatView: View {
    @State private var viewModel: ChatViewModel
    /// On narrow screens the split view collapses into a stack; this keeps the chat on top.
    @State private var compactColumn = NavigationSplitViewColumn.detail
    /// The settings are short, so wide screens show them next to the chat even in portrait; users can still hide them.
    @State private var columnVisibility = NavigationSplitViewVisibility.all

    init(viewModel: ChatViewModel) {
        _viewModel = State(initialValue: viewModel)
    }

    var body: some View {
        NavigationSplitView(columnVisibility: $columnVisibility, preferredCompactColumn: $compactColumn) {
            ServerSettingsView(viewModel: viewModel) { compactColumn = .detail }
                .navigationTitle(Text(.serverTitle))
                .navigationSplitViewColumnWidth(min: 280, ideal: 320, max: 420)
        } detail: {
            VStack(spacing: 0) {
                if let error = viewModel.error {
                    ErrorBanner(error: error) { viewModel.error = nil }
                }
                messageList
                inputBar
            }
            .navigationTitle(Text(.chatTitle))
            #if os(iOS)
            .navigationBarTitleDisplayMode(.inline)
            #endif
            .toolbar {
                ToolbarItem(placement: .primaryAction) { ModelPicker(viewModel: viewModel) }
            }
        }
        .task { viewModel.applyServer() }
    }

    private var messageList: some View {
        ScrollViewReader { proxy in
            List(viewModel.messages) { message in
                MessageRow(message: message, isPending: viewModel.isGenerating && message.id == viewModel.messages.last?.id)
                    .frame(maxWidth: maxContentWidth)
                    .frame(maxWidth: .infinity)
                    .id(message.id)
                    .listRowSeparator(.hidden)
            }
            .listStyle(.plain)
            .overlay {
                if viewModel.messages.isEmpty {
                    ContentUnavailableView {
                        Label {
                            Text(.chatEmptyTitle)
                        } icon: {
                            Image(systemName: "bubble.left.and.bubble.right")
                        }
                    } description: {
                        Text(.chatEmptyMessage)
                    }
                }
            }
            .onChange(of: viewModel.messages.last?.content) {
                if let id = viewModel.messages.last?.id {
                    proxy.scrollTo(id, anchor: .bottom)
                }
            }
        }
    }

    private var inputBar: some View {
        HStack(alignment: .bottom) {
            MessageEditor(text: $viewModel.draft)

            if viewModel.isGenerating {
                Button {
                    viewModel.stopGenerating()
                } label: {
                    Label {
                        Text(.chatStop)
                    } icon: {
                        Image(systemName: "stop.circle.fill")
                    }
                    .labelStyle(.iconOnly)
                    .font(.title)
                }
            } else {
                Button {
                    viewModel.send()
                } label: {
                    Label {
                        Text(.chatSend)
                    } icon: {
                        Image(systemName: "arrow.up.circle.fill")
                    }
                    .labelStyle(.iconOnly)
                    .font(.title)
                }
                .disabled(!viewModel.canSend)
            }
        }
        .frame(maxWidth: maxContentWidth)
        .padding()
        .frame(maxWidth: .infinity)
    }
}

/// Multi-line message field. Unlike a vertical `TextField`, Return always inserts a line break; sending is done with
/// the send button.
private struct MessageEditor: View {
    @Binding var text: String

    var body: some View {
        TextEditor(text: $text)
            .scrollContentBackground(.hidden)
            .frame(minHeight: 36, maxHeight: 120)
            .fixedSize(horizontal: false, vertical: true)
            .padding(.horizontal, 4)
            .background(alignment: .topLeading) {
                if text.isEmpty {
                    Text(.chatMessagePlaceholder)
                        .foregroundStyle(.tertiary)
                        .padding(.horizontal, 9)
                        .padding(.vertical, 8)
                        .allowsHitTesting(false)
                }
            }
            .overlay(RoundedRectangle(cornerRadius: 6).stroke(.secondary.opacity(0.4)))
    }
}

private struct ErrorBanner: View {
    let error: AppError
    let onDismiss: () -> Void

    var body: some View {
        HStack(alignment: .top) {
            Image(systemName: "exclamationmark.triangle.fill")
                .foregroundStyle(.orange)
            VStack(alignment: .leading, spacing: 4) {
                Text(verbatim: error.errorDescription ?? "")
                    .font(.subheadline.bold())
                if let reason = error.failureReason {
                    Text(verbatim: reason)
                        .font(.caption)
                        .foregroundStyle(.secondary)
                        .lineLimit(3)
                        .textSelection(.enabled)
                }
            }
            Spacer()
            Button(action: onDismiss) {
                Label {
                    Text(.errorDismiss)
                } icon: {
                    Image(systemName: "xmark")
                }
                .labelStyle(.iconOnly)
            }
            .buttonStyle(.plain)
        }
        .padding()
        .background(.orange.opacity(0.12))
    }
}

#if DEBUG
#Preview {
    ChatView(viewModel: ChatViewModel(repository: PreviewChatRepository()))
}
#endif
