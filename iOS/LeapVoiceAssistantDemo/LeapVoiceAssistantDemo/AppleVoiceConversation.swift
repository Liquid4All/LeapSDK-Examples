import Foundation
import LeapSDK
import LeapUi

/// Swift implementation of `VoiceConversation` that wraps a LeapSDK `Conversation`.
///
/// Lives in demo code (not the framework) — same pattern as Android's `LeapVoiceConversation`
/// living in the Android demo app.
///
/// Both LeapSDK and LeapUi XCFrameworks export their own Kotlin stdlib wrappers
/// (KotlinFloatArray, KotlinInt, etc.), so types must be module-qualified to avoid ambiguity.
/// The two frameworks have **separate Kotlin runtime class registries**, so `unsafeBitCast`
/// does NOT work — LeapSDK's Kotlin runtime cannot unwrap objects created by LeapUI's runtime.
/// Instead, NSData roundtrips bridge between the two runtimes (Foundation types are shared).
///
/// Usage:
/// ```swift
/// let runner = try await Leap.shared.load(model: "LFM2.5-Audio-1.5B", quantization: "Q4_0")
/// let conv = runner.createConversation(systemPrompt: "Respond with interleaved text and audio.")
/// store.setConversation(conv: AppleVoiceConversation(conversation: conv))
/// ```
final class AppleVoiceConversation: VoiceConversation {
    private let conversation: Conversation

    init(conversation: Conversation) {
        self.conversation = conversation
    }

    func __generateResponse(
        audioSamples: LeapUi.KotlinFloatArray,
        sampleRate: Int32,
        onAudioChunk: @escaping (LeapUi.KotlinFloatArray, LeapUi.KotlinInt) -> Void,
        completionHandler: @escaping @Sendable (LeapUi.Leap_sdkGenerationStats?, (any Error)?) -> Void
    ) {
        Task {
            do {
                let result = try await generateResponseImpl(
                    audioSamples: audioSamples,
                    sampleRate: sampleRate,
                    onAudioChunk: onAudioChunk
                )
                completionHandler(result, nil)
            } catch {
                completionHandler(nil, error)
            }
        }
    }

    /// VoiceAssistantStore does not call reset() — multi-turn context is kept alive.
    /// This wraps the same Conversation instance (no context clearing) for protocol conformance.
    func reset() -> any VoiceConversation {
        return AppleVoiceConversation(conversation: conversation)
    }

    // MARK: - Private

    private func generateResponseImpl(
        audioSamples: LeapUi.KotlinFloatArray,
        sampleRate: Int32,
        onAudioChunk: @escaping (LeapUi.KotlinFloatArray, LeapUi.KotlinInt) -> Void
    ) async throws -> LeapUi.Leap_sdkGenerationStats? {
        // Convert LeapUi.KotlinFloatArray → [Float] via NSData bulk memcpy.
        // Use LeapUi's ArrayConversionsKt because the array belongs to LeapUi's Kotlin runtime.
        let nsData = LeapUi.ArrayConversionsKt.floatArrayToNSData(array: audioSamples)
        let count = nsData.count / MemoryLayout<Float>.size
        let samples: [Float] = count > 0 ? nsData.withUnsafeBytes { ptr in
            Array(ptr.bindMemory(to: Float.self))
        } : []

        let audioContent = ChatMessageContent.fromFloatSamples(
            samples,
            sampleRate: Int(sampleRate)
        )
        let message = ChatMessage(
            role: .user,
            content: [audioContent as ChatMessageContent],
            reasoningContent: nil,
            functionCalls: nil
        )

        var resultStats: LeapUi.Leap_sdkGenerationStats? = nil

        let flow = conversation.generateResponse(
            message: message,
            generationOptions: GenerationOptions()
        )
        for try await response in flow {
            try Task.checkCancellation()
            if let audio = response as? MessageResponseAudioSample {
                // Convert LeapSDK.KotlinFloatArray → LeapUi.KotlinFloatArray via NSData roundtrip.
                let data = LeapSDK.ArrayConversionsKt.floatArrayToNSData(array: audio.samples)
                let uiSamples = LeapUi.ArrayConversionsKt.nsDataToFloatArray(data: data)
                let uiRate = LeapUi.KotlinInt(value: audio.sampleRate)
                onAudioChunk(uiSamples, uiRate)
            } else if let complete = response as? MessageResponseComplete {
                if let stats = complete.stats {
                    resultStats = LeapUi.Leap_sdkGenerationStats(
                        promptTokens: stats.promptTokens,
                        completionTokens: stats.completionTokens,
                        totalTokens: stats.totalTokens,
                        tokenPerSecond: stats.tokenPerSecond
                    )
                }
            }
        }

        return resultStats
    }
}
