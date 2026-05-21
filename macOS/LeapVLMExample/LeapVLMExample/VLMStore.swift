import AppKit
import LeapModelDownloader
import Observation

@Observable
final class VLMStore {
  var status = "Initializing..."
  var isModelReady = false
  var isGenerating = false
  var generatedText = ""

  private static let modelName = "LFM2.5-VL-1.6B"
  private static let quantization = "Q4_0"

  private var modelRunner: (any ModelRunner)?
  private var generationTask: Task<Void, Never>?

  @MainActor
  func setupModel() async {
    guard modelRunner == nil else { return }

    do {
      status = "Downloading \(Self.modelName) model..."

      let cachePath = FileManager.default.urls(for: .cachesDirectory, in: .userDomainMask).first!
        .appendingPathComponent("leap-cache").path
      try? FileManager.default.createDirectory(atPath: cachePath, withIntermediateDirectories: true)
      let runner = try await Leap.shared.load(
        model: Self.modelName,
        quantization: Self.quantization,
        options: LiquidInferenceEngineManifestOptions(
          cacheOptions: .enabled(path: cachePath)
        ),
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
      isModelReady = true
      status = "Model ready"
    } catch {
      status = "Failed to load model: \(error.localizedDescription)"
    }
  }

  @MainActor
  func describeImage() async {
    guard let runner = modelRunner else { return }

    guard let jpegData = resizedJPEGData(forAsset: "pug", maxSize: CGSize(width: 512, height: 512)) else {
      status = "Failed to load image from assets"
      return
    }

    // Cancel any in-flight generation before starting a new one.
    generationTask?.cancel()

    isGenerating = true
    generatedText = ""
    status = "Generating..."

    generationTask = Task { @MainActor in
      let imageContent = ChatMessageContent.Image.fromJPEGData(jpegData)
      let message = ChatMessage(
        role: .user,
        content: [imageContent as ChatMessageContent, ChatMessageContent.text("Describe this image.")],
        reasoningContent: nil,
        functionCalls: nil
      )

      let conversation = runner.createConversation(systemPrompt: nil)

      for await resp in conversation.generateResponse(message: message) {
        if Task.isCancelled { break }
        // SKIE bridges the flow as non-throwing; surface in-band errors via runtime cast
        // (the .Error case is excluded from SKIE's sealed-enum codegen — name collides with
        // Swift's `Error` protocol).
        if let err = resp as? MessageResponseError {
          isGenerating = false
          status = "Generation failed: \(err.message)"
          continue
        }
        switch onEnum(of: resp) {
        case .chunk(let chunk):
          generatedText.append(chunk.text)
        case .complete:
          isGenerating = false
          status = "Model ready"
        default:
          break
        }
      }
      generationTask = nil
    }
  }

  func stop() {
    generationTask?.cancel()
    generationTask = nil
  }

  deinit {
    generationTask?.cancel()
  }

  private func resizedJPEGData(forAsset name: String, maxSize: CGSize) -> Data? {
    guard let nsImage = NSImage(named: name) else { return nil }

    let originalSize = nsImage.size
    let ratio = min(maxSize.width / originalSize.width, maxSize.height / originalSize.height)
    let newSize = CGSize(width: originalSize.width * ratio, height: originalSize.height * ratio)

    let resizedImage = NSImage(size: newSize)
    resizedImage.lockFocus()
    nsImage.draw(in: CGRect(origin: .zero, size: newSize),
                 from: CGRect(origin: .zero, size: originalSize),
                 operation: .copy, fraction: 1.0)
    resizedImage.unlockFocus()

    guard let tiffData = resizedImage.tiffRepresentation,
          let bitmap = NSBitmapImageRep(data: tiffData),
          let jpegData = bitmap.representation(using: .jpeg, properties: [.compressionFactor: 0.9])
    else { return nil }

    return jpegData
  }
}
