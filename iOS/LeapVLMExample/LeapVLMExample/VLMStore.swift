import LeapSDK
import Observation
import UIKit

@Observable
final class VLMStore {
  var status = "Initializing..."
  var isModelReady = false
  var isGenerating = false
  var generatedText = ""

  private static let modelName = "LFM2-VL-450M"
  private static let quantization = "Q8_0"

  private var modelRunner: (any ModelRunner)?

  @MainActor
  func setupModel() async {
    guard modelRunner == nil else { return }

    do {
      status = "Downloading \(Self.modelName) model..."

      let runner = try await Leap.shared.load(
        model: Self.modelName,
        quantization: Self.quantization,
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

    isGenerating = true
    generatedText = ""
    status = "Generating..."

    do {
      let imageContent = ChatMessageContent.Image.fromJPEGData(jpegData)
      let message = ChatMessage(
        role: .user,
        content: [imageContent as ChatMessageContent, ChatMessageContent.text("Describe this image.")],
        reasoningContent: nil,
        functionCalls: nil
      )

      let conversation = runner.createConversation(systemPrompt: nil)

      for try await resp in conversation.generateResponse(message: message) {
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
    } catch {
      generatedText = "Error: \(error.localizedDescription)"
      isGenerating = false
      status = "Model ready"
    }
  }

  private func resizedJPEGData(forAsset name: String, maxSize: CGSize) -> Data? {
    guard let image = UIImage(named: name) else { return nil }

    let ratio = min(maxSize.width / image.size.width, maxSize.height / image.size.height)
    let newSize = CGSize(width: image.size.width * ratio, height: image.size.height * ratio)

    let renderer = UIGraphicsImageRenderer(size: newSize)
    let resizedImage = renderer.image { _ in image.draw(in: CGRect(origin: .zero, size: newSize)) }
    return resizedImage.jpegData(compressionQuality: 0.9)
  }
}
