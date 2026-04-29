import AVFoundation
import Foundation
import LeapSDK
import LeapUi

private let modelName = "LFM2.5-Audio-1.5B"
private let quantizationSlug = "Q4_0"
private let systemPrompt = "Respond with interleaved text and audio."

/// Full model-integration view model for the macOS voice assistant demo.
///
/// Delegates all state management, recording, playback, and generation flow to
/// `VoiceAssistantStore` (the shared Kotlin state machine). This view model is responsible only for:
/// - Loading the model and wiring it to the store via `AppleVoiceConversation`
/// - Observing `store.state` and publishing changes for SwiftUI
/// - Exposing `sharedRunner` for `AppDelegate` to call `unload()` before process exit
@MainActor
final class DemoViewModel: ObservableObject {

    // MARK: - Published state

    let store: VoiceAssistantStore

    @Published private(set) var debugInfo: String = ""

    /// Shared runner reference used by AppDelegate to call unload() before process exit.
    static var sharedRunner: (any ModelRunner)?

    // MARK: - Private

    private var stateObservation: Task<Void, Never>? = nil
    private var modelLoaded = false

    // MARK: - Init

    init() {
        // 10-second playback timeout to prevent infinite hangs in playback callbacks on macOS.
        store = VoiceAssistantStore.makeForApple(playbackTimeoutMs: 10_000)
    }

    deinit {
        store.close()
    }

    // MARK: - Lifecycle

    func onAppear() {
        startObservingState()
        if !modelLoaded {
            modelLoaded = true
            Task {
                await AVCaptureDevice.requestAccess(for: .audio)
                await loadModel()
            }
        }
    }

    func onDisappear() {
        stateObservation?.cancel()
        stateObservation = nil
    }

    // MARK: - State observation

    private func startObservingState() {
        stateObservation = Task { [weak self] in
            guard let self else { return }
            for await state in self.store.state {
                guard !Task.isCancelled else { break }
                let status = DemoStatusType(kotlinName: state.statusType.name)
                if status == .error {
                    self.debugInfo = state.statusText
                } else if status == .ready {
                    let threads = ProcessInfo.processInfo.activeProcessorCount
                    if let statsText = state.statsText, !statsText.isEmpty {
                        self.debugInfo = "\(modelName) \(quantizationSlug)\nBackends: Metal, CPU\nThreads: \(threads)\nLast gen: \(statsText)"
                    } else {
                        self.debugInfo = "\(modelName) \(quantizationSlug)\nBackends: Metal, CPU\nThreads: \(threads)"
                    }
                }
            }
        }
    }

    // MARK: - Model loading

    private func loadModel() async {
        store.setModelProgress(fraction: 0, message: "Resolving manifest\u{2026}")
        do {
            let runner = try await Leap.shared.load(
                model: modelName,
                quantization: quantizationSlug,
                progress: { [weak self] fraction, _ in
                    Task { @MainActor [weak self] in
                        self?.store.setModelProgress(
                            fraction: Float(fraction),
                            message: "Downloading (\(Int(fraction * 100))%)"
                        )
                    }
                }
            )
            DemoViewModel.sharedRunner = runner
            let conversation = runner.createConversation(systemPrompt: systemPrompt)
            store.setConversation(conv: AppleVoiceConversation(conversation: conversation))

            let threads = ProcessInfo.processInfo.activeProcessorCount
            debugInfo = "\(modelName) \(quantizationSlug)\nBackends: Metal, CPU\nThreads: \(threads)"
        } catch {
            store.setModelError(message: "\u{2717} \(error.localizedDescription)")
        }
    }
}
