import SwiftUI

struct MessageRow: View {
    let message: ChatViewModel.Message
    let isPending: Bool

    var body: some View {
        HStack {
            if message.role == .user { Spacer(minLength: 40) }
            VStack(alignment: .leading, spacing: 4) {
                Group {
                    if message.content.isEmpty && isPending {
                        ProgressView()
                    } else {
                        Text(verbatim: message.content)
                            .textSelection(.enabled)
                    }
                }
                .padding(10)
                .background(message.role == .user ? Color.accentColor.opacity(0.2) : Color.secondary.opacity(0.12),
                            in: RoundedRectangle(cornerRadius: 12))
                if let metrics = message.metrics {
                    MetricsCaption(metrics: metrics)
                        .padding(.horizontal, 4)
                }
            }
            if message.role == .assistant { Spacer(minLength: 40) }
        }
    }
}

/// "1.3 sec to first token · 8.2 sec total · 38.2 tokens/s", with every label and number localized.
private struct MetricsCaption: View {
    let metrics: ResponseMetrics

    var body: some View {
        parts
            .font(.caption2)
            .foregroundStyle(.secondary)
            .monospacedDigit()
    }

    private var parts: Text {
        var text = Text(.chatMetricsFirstToken(format(metrics.timeToFirstToken)))
            + separator
            + Text(.chatMetricsTotal(format(metrics.totalTime)))
        if let speed = metrics.tokensPerSecond {
            let number = speed.formatted(.number.precision(.fractionLength(1)))
            text = text + separator
                + Text(metrics.isApproximate ? .chatMetricsSpeedApprox(number) : .chatMetricsSpeed(number))
        }
        return text
    }

    /// Punctuation only, the same in every language.
    private var separator: Text { Text(verbatim: " · ") }

    private func format(_ duration: Duration) -> String {
        duration.formatted(.units(allowed: [.seconds], width: .abbreviated, fractionalPart: .show(length: 1)))
    }
}
