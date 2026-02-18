import Foundation
import LeapSDK
import LeapSDKMacros

private let SYSTEM_PROMPT =
  "You are a helpful cooking assistant. Generate recipes based on user requests."

@MainActor
class GeneratorViewModel: ObservableObject {
  @Published var isModelLoading = false
  @Published var isGenerating = false
  @Published var recipe: Recipe? = nil
  @Published var statusMessage: String = "Ready to generate"
  @Published var downloadProgress: Double = 0.0

  private var modelRunner: (any ModelRunner)?

  private let modelName = "LFM2-350M"
  private let quantization = "Q4_0"

  func setupModel() async {
    guard modelRunner == nil else { return }

    isModelLoading = true
    statusMessage = "Downloading and loading model..."

    do {
      modelRunner = try await Leap.shared.load(
        model: modelName,
        quantization: quantization,
        progress: { [weak self] progress, speed in
          Task { @MainActor in
            self?.downloadProgress = progress
            if progress < 1.0 {
              self?.statusMessage = "Downloading: \(Int(progress * 100))%"
            } else {
              self?.statusMessage = "Loading model..."
            }
          }
        }
      )

      statusMessage = "Model loaded and ready"
      isModelLoading = false
    } catch {
      print("Failed to load model: \(error)")
      statusMessage = "Error loading model: \(error.localizedDescription)"
      isModelLoading = false
      modelRunner = nil
    }
  }

  func generateRecipe() async throws {
    guard let modelRunner = modelRunner else {
      print("Model not yet loaded")
      return
    }

    isGenerating = true
    statusMessage = "Generating recipe..."

    let systemMessage = ChatMessage(role: .system, content: .text(SYSTEM_PROMPT))
    let conversation = Conversation(
      modelRunner: modelRunner,
      history: [systemMessage]
    )

    let options = GenerationOptions()
    options.setResponseFormat(type: Recipe.self)

    let userMessage = ChatMessage(
      role: .user,
      content: .text("Generate a recipe for a dinner dish with shrimps"))
    let stream = conversation.generateResponse(
      message: userMessage, generationOptions: options)

    var jsonResponse = ""
    do {
      for try await response in stream {
        switch onEnum(of: response) {
        case .chunk(let chunk):
          jsonResponse.append(chunk.text)
        case .audioSample, .complete, .reasoningChunk, .functionCalls:
          break
        }
      }

      let jsonData = jsonResponse.data(using: .utf8)!
      recipe = try JSONDecoder().decode(Recipe.self, from: jsonData)
      statusMessage = "Recipe generated!"
    } catch {
      statusMessage = "Error generating recipe: \(error.localizedDescription)"
      throw error
    }

    isGenerating = false
  }
}
