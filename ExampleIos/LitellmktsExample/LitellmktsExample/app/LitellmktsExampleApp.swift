import SwiftUI

/// Composition root: creates each feature's repository and injects it into its view.
@main
struct LitellmktsExampleApp: App {
    var body: some Scene {
        WindowGroup {
            ChatView(viewModel: ChatViewModel(repository: LitellmktsChatRepository()))
        }
    }
}
