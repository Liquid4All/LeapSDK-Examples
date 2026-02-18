import AVFoundation
import Foundation
import LeapSDK
import Observation

struct AudioDemoMessage: Identifiable, Equatable {
  let id = UUID()
  let role: ChatMessage.Role
  let text: String
  let audioData: Data?

  var isUser: Bool { role == .user }

  static func == (lhs: AudioDemoMessage, rhs: AudioDemoMessage) -> Bool {
    lhs.id == rhs.id && lhs.role == rhs.role && lhs.text == rhs.text
  }
}

@Observable
@MainActor
final class AudioDemoStore {
  private static let modelName = "LFM2.5-Audio-1.5B"
  private static let quantization = "Q4_0"

  var inputText: String = ""
  var messages: [AudioDemoMessage] = []
  var status: String?
  var streamingText: String = ""
  var isModelLoading = false
  var isGenerating = false
  var isRecording = false

  private let playbackManager = AudioPlaybackManager()
  private let recorder = AudioRecorder()
  private var conversation: (any Conversation)?
  private var modelRunner: (any ModelRunner)?
  private var streamingTask: Task<Void, Never>?

  init() {
    playbackManager.prepareSession()
  }

  func setupModel() async {
    guard modelRunner == nil else { return }
    isModelLoading = true
    status = "Loading model..."

    do {
      status = "Downloading \(Self.modelName) model..."

      // Use manifest downloading for LFM2.5-Audio-1.5B (speech + text input/output)
      let runner = try await Leap.shared.load(
        model: Self.modelName,
        quantization: Self.quantization,
        options: LiquidInferenceEngineManifestOptions(contextSize: 1024, nGpuLayers: 0),
        progress: { [weak self] progress, speed in
          Task { @MainActor in
            if progress < 1.0 {
              self?.status = "Downloading: \(Int(progress * 100))%"
            } else {
              self?.status = "Loading model into memory..."
            }
          }
        }
      )

      modelRunner = runner
      conversation = Conversation(
        modelRunner: runner,
        history: [
          ChatMessage(role: .system, content: .text("Respond with interleaved text and audio."))
        ])
      messages.append(
        AudioDemoMessage(
          role: .assistant,
          text: "Model loaded: \(Self.modelName) (\(Self.quantization))",
          audioData: nil
        )
      )
      status = "Ready"
    } catch {
      status = "Failed to load model: \(error.localizedDescription)"
    }

    isModelLoading = false
  }

  func sendTextPrompt() {
    let trimmed = inputText.trimmingCharacters(in: .whitespacesAndNewlines)
    guard !trimmed.isEmpty else { return }
    inputText = ""
    let message = ChatMessage(role: .user, content: .text(trimmed))
    appendUserMessage(text: trimmed, audioData: nil)
    streamResponse(for: message)
  }

  func toggleRecording() {
    if isRecording {
      recorder.stop()
      isRecording = false
      guard let capture = recorder.capture() else {
        status = "No audio captured."
        return
      }
      sendAudioPrompt(samples: capture.samples, sampleRate: capture.sampleRate)
    } else {
      do {
        try recorder.start()
        isRecording = true
        status = "Recording..."
      } catch {
        status = "Recording failed: \(error.localizedDescription)"
      }
    }
  }

  func cancelRecording() {
    recorder.cancel()
    isRecording = false
    status = "Recording cancelled."
  }

  func playAudio(_ data: Data) {
    playbackManager.play(wavData: data)
  }

  private func sendAudioPrompt(samples: [Float], sampleRate: Int) {
    guard !samples.isEmpty else {
      status = "Audio capture was empty."
      return
    }

    let audioContent = ChatMessageContent.fromFloatSamples(
      samples, sampleRate: sampleRate)
    let chatMessage = ChatMessage(role: .user, content: audioContent)

    var display = "Audio prompt (\(samples.count) samples @ \(sampleRate) Hz)"
    if samples.count < sampleRate / 4 {
      display = "Audio prompt (~\(samples.count) samples)"
    }

    let audioData = audioContent.data.toData()

    appendUserMessage(text: display, audioData: audioData)
    streamResponse(for: chatMessage)
  }

  private func appendUserMessage(text: String, audioData: Data?) {
    messages.append(AudioDemoMessage(role: .user, text: text, audioData: audioData))
  }

  private func streamResponse(for message: ChatMessage) {
    guard let conversation else {
      status = "Model not ready yet."
      return
    }

    playbackManager.reset()
    streamingTask?.cancel()
    streamingText = ""
    status = "Awaiting response..."
    isGenerating = true

    let stream = conversation.generateResponse(message: message)

    streamingTask = Task { [weak self] in
      guard let self else { return }
      do {
        for try await event in stream {
          if Task.isCancelled { break }
          await MainActor.run {
            self.handle(event)
          }
        }
      } catch {
        await MainActor.run {
          self.handleGenerationError(error)
        }
      }
      await MainActor.run {
        self.streamingTask = nil
      }
    }
  }

  private func handle(_ event: any MessageResponse) {
    switch onEnum(of: event) {
    case .chunk(let chunk):
      streamingText.append(chunk.text)
    case .reasoningChunk:
      status = "Thinking..."
    case .audioSample(let sample):
      let floats = sample.samples.toFloatArray()
      playbackManager.enqueue(samples: floats, sampleRate: Int(sample.sampleRate))
      status = "Streaming audio..."
    case .functionCalls(let calls):
      status = "Received function call: \(calls.functionCalls.count)"
    case .complete(let completion):
      finish(with: completion)
    }
  }

  private func finish(with completion: MessageResponseComplete) {
    let text = completion.fullMessage.content.compactMap { content -> String? in
      switch onEnum(of: content) {
      case .text(let textContent):
        return textContent.text
      case .audio, .image:
        return nil
      }
    }.joined()

    let audioData = completion.fullMessage.content.firstAudioData
    messages.append(
      AudioDemoMessage(
        role: .assistant,
        text: text.isEmpty ? "(audio response)" : text,
        audioData: audioData
      )
    )
    streamingText = ""
    isGenerating = false
    status =
      audioData != nil
      ? "Response complete with audio."
      : finishReasonDescription(completion.finishReason)

    if let audioData {
      playbackManager.play(wavData: audioData)
    }
  }

  private func handleGenerationError(_ error: Error) {
    isGenerating = false
    streamingText = ""
    status = "Generation failed: \(error.localizedDescription)"
  }

  private func finishReasonDescription(_ reason: GenerationFinishReason) -> String {
    switch reason {
    case .stop:
      return "Response complete."
    case .exceedContext:
      return "Context window exceeded."
    case .interrupted:
      return "Generation interrupted."
    case .constraint:
      return "Constraint satisfied."
    case .error:
      return "Generation error."
    }
  }

  private func findModelURL() -> URL? {
    let bundle = Bundle.main
    let candidates = [
      "LFM2-Audio-1.5B-Q8_0"
    ]
    for name in candidates {
      if let url = bundle.url(forResource: name, withExtension: "gguf") {
        return url
      }
    }
    return nil
  }
}

extension Array where Element: ChatMessageContent {
  fileprivate var firstAudioData: Data? {
    for content in self {
      switch onEnum(of: content) {
      case .audio(let audioContent):
        return audioContent.data.toData()
      case .text, .image:
        continue
      }
    }
    return nil
  }
}
