import AVFoundation
import Foundation
import LeapSDK
import LeapUi

private let modelName = "LFM2.5-Audio-1.5B"
private let quantizationSlug = "Q4_0"
private let systemPrompt = "Respond with interleaved text and audio."

/// Full model-integration view model for the iOS voice assistant demo.
///
/// Delegates all state management, recording, playback, and generation flow to
/// `VoiceAssistantStore` (the shared Kotlin state machine). This view model is responsible only for:
/// - Configuring the AVAudioSession
/// - Loading the model and wiring it to the store via `AppleVoiceConversation`
/// - Observing `store.state` and publishing changes for SwiftUI
@MainActor
final class DemoViewModel: ObservableObject {

    // MARK: - Published state

    let store: VoiceAssistantStore

    @Published private(set) var statusText: String = "Initializing\u{2026}"
    @Published private(set) var statusType: DemoStatusType = .loading
    @Published private(set) var statsText: String? = nil

    // MARK: - Private

    private var stateObservation: Task<Void, Never>? = nil
    private var modelLoaded = false

    // MARK: - Init

    init() {
        store = VoiceAssistantStore.makeForApple()
    }

    deinit {
        store.close()
    }

    // MARK: - Lifecycle

    func onAppear() {
        configureAudioSession()
        startObservingState()
        if !modelLoaded {
            modelLoaded = true
            Task { await loadModel() }
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
                self.statusText = state.statusText
                self.statusType = DemoStatusType(kotlinName: state.statusType.name)
                self.statsText = state.statsText
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
            let conversation = runner.createConversation(systemPrompt: systemPrompt)
            store.setConversation(conv: AppleVoiceConversation(conversation: conversation))
        } catch {
            store.setModelError(message: "\u{2717} \(error.localizedDescription)")
        }
    }

    // MARK: - AVAudioSession (iOS only)

    private func configureAudioSession() {
        let session = AVAudioSession.sharedInstance()
        try? session.setCategory(.playAndRecord, mode: .default, options: [.defaultToSpeaker])
        try? session.setActive(true)
        session.requestRecordPermission { _ in }
    }
}
