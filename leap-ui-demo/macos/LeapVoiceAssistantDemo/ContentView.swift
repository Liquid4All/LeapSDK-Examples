import LeapUi
import SwiftUI

/// Full-screen voice assistant demo on a black background.
///
/// Hosts `VoiceAssistantNSViewController` (from LeapUi) via `NSViewControllerRepresentable`.
/// `DemoViewModel` manages model loading and delegates all state management to `VoiceAssistantStore`.
/// A debug info overlay is shown at the top-left, matching the macOS demo style.
struct ContentView: View {
    @StateObject private var viewModel = DemoViewModel()

    var body: some View {
        ZStack {
            VoiceWidgetRepresentable(store: viewModel.store)
                .frame(maxWidth: .infinity, maxHeight: .infinity)
                .background(Color.black)
                .ignoresSafeArea()

            VStack {
                HStack {
                    if !viewModel.debugInfo.isEmpty {
                        Text(viewModel.debugInfo)
                            .font(.system(size: 11, design: .monospaced))
                            .foregroundColor(Color.white.opacity(0.7))
                            .padding(.horizontal, 8)
                            .padding(.vertical, 6)
                            .background(Color.black.opacity(0.55))
                            .padding(12)
                    }
                    Spacer()
                }
                Spacer()
            }
        }
        .onAppear { viewModel.onAppear() }
        .onDisappear { viewModel.onDisappear() }
    }
}

// MARK: - NSViewControllerRepresentable bridge

/// Wraps `VoiceAssistantNSViewController` (from LeapUi) for use in SwiftUI.
///
/// Intents are dispatched directly to the `VoiceAssistantStore` via `processIntent`.
/// Kotlin default parameters are not visible in ObjC interop, so `labels`, `colors`, and
/// `showPoweredBy` are passed explicitly using their Kotlin default values.
private struct VoiceWidgetRepresentable: NSViewControllerRepresentable {
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

    func makeNSViewController(context: Context) -> NSViewController {
        VoiceAssistantNSViewControllerKt.VoiceAssistantNSViewController(
            state: store.widgetStateHolder,
            onIntent: { intent in store.processIntent(intent: intent) },
            labels: Self.defaultLabels,
            colors: Self.defaultColors,
            showPoweredBy: true
        )
    }

    func updateNSViewController(_ nsViewController: NSViewController, context: Context) {
        // State changes drive recomposition inside Compose — nothing to do here.
    }
}
