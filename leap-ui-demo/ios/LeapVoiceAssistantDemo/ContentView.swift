import LeapUi
import SwiftUI

/// Full-screen voice assistant demo on a black background.
///
/// Hosts `VoiceAssistantViewController` (from LeapUi) via `UIViewControllerRepresentable`.
/// `DemoViewModel` manages model loading and delegates all state management to `VoiceAssistantStore`.
/// A status text overlay and post-generation stats are shown at the bottom, matching the Android
/// and web demos.
struct ContentView: View {
    @StateObject private var viewModel = DemoViewModel()

    var body: some View {
        ZStack {
            VoiceWidgetRepresentable(store: viewModel.store)
                .frame(maxWidth: .infinity, maxHeight: .infinity)
                .background(Color.black)
                .ignoresSafeArea()

            VStack {
                Spacer()
                VStack(spacing: 4) {
                    if let stats = viewModel.statsText {
                        Text(stats)
                            .font(.system(size: 11))
                            .foregroundColor(Color(white: 0.55))
                    }
                    if !viewModel.statusText.isEmpty {
                        Text(viewModel.statusText)
                            .font(.system(size: 12))
                            .foregroundColor(statusColor(for: viewModel.statusType))
                    }
                }
                .padding(.bottom, 16)
            }
        }
        .onAppear { viewModel.onAppear() }
        .onDisappear { viewModel.onDisappear() }
    }

    private func statusColor(for type: DemoStatusType) -> Color {
        switch type {
        case .loading: return Color(white: 0.67)
        case .ready:   return Color(red: 0.4, green: 1.0, blue: 0.4)
        case .error:   return Color(red: 1.0, green: 0.4, blue: 0.4)
        }
    }
}

// MARK: - UIViewControllerRepresentable bridge

/// Wraps `VoiceAssistantViewController` (from LeapUi) for use in SwiftUI.
///
/// Intents are dispatched directly to the `VoiceAssistantStore` via `processIntent`.
/// Kotlin default parameters are not visible in ObjC interop, so `labels`, `colors`, and
/// `showPoweredBy` are passed explicitly using their Kotlin default values.
private struct VoiceWidgetRepresentable: UIViewControllerRepresentable {
    let store: VoiceAssistantStore

    private static let defaultLabels = VoiceWidgetLabels(
        idle: "Tap and hold to speak",
        listening: "Listening",
        responding: "Generating",
        micStartDescription: "Start recording",
        micStopDescription: "Stop recording",
        micCancelDescription: "Cancel recording"
    )
    private static let defaultColors = VoiceWidgetColors.companion.Default

    func makeUIViewController(context: Context) -> UIViewController {
        VoiceAssistantViewControllerKt.VoiceAssistantViewController(
            state: store.widgetStateHolder,
            onIntent: { intent in store.processIntent(intent: intent) },
            labels: Self.defaultLabels,
            colors: Self.defaultColors,
            showPoweredBy: true
        )
    }

    func updateUIViewController(_ uiViewController: UIViewController, context: Context) {
        // State changes drive recomposition inside Compose — nothing to do here.
    }
}
