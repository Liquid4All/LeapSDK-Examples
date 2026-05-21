import Foundation
import LeapModelDownloader
import Observation

@MainActor
@Observable
class GeneratorViewModel {
  var isModelLoading = false
  var isGenerating = false
  var recipe: Recipe? = nil
  var statusMessage: String = "Ready to generate"
  var downloadProgress: Double = 0.0

  private var modelRunner: (any ModelRunner)?

  private let modelName = "LFM2.5-350M"  // Smaller model for faster testing
  private let quantization = "Q4_0"

  func setupModel() async {
    guard modelRunner == nil else {
      // Model already loaded
      return
    }

    isModelLoading = true
    statusMessage = "Downloading and loading model..."

    do {
      let cachePath = FileManager.default.urls(for: .cachesDirectory, in: .userDomainMask).first!
        .appendingPathComponent("leap-cache").path
      try? FileManager.default.createDirectory(atPath: cachePath, withIntermediateDirectories: true)
      modelRunner = try await Leap.shared.load(
        model: modelName,
        quantization: quantization,
        options: LiquidInferenceEngineManifestOptions(
          cacheOptions: .enabled(path: cachePath)
        ),
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

    let systemMessage = ChatMessage(
      role: .system,
      textContent: "You are a helpful cooking assistant."
    )
    let conversation = Conversation(modelRunner: modelRunner, history: [systemMessage])

    let userMessage = ChatMessage(
      role: .user,
      textContent: "Generate a recipe for a dinner dish with shrimps."
    )

    // Constrain output to the Recipe schema so the model emits valid JSON we can decode directly.
    let options = GenerationOptions()
    options.jsonSchemaConstraint = Recipe.jsonSchema()

    do {
      // generateResponse(message:generationOptions:) returns a SkieSwiftFlow directly,
      // suitable for async iteration.
      let stream = conversation.generateResponse(message: userMessage, generationOptions: options)

      var jsonResponse = ""
      var streamError: String?
      for await event in stream {
        // SKIE bridges the flow as non-throwing; surface in-band errors via runtime cast
        // (the .Error case is excluded from SKIE's sealed-enum codegen — name collides with
        // Swift's `Error` protocol).
        if let err = event as? MessageResponseError {
          streamError = err.message
          continue
        }
        switch onEnum(of: event) {
        case .chunk(let chunk):
          jsonResponse.append(chunk.text)
        case .complete, .audioSample, .reasoningChunk, .functionCalls:
          break
        }
      }
      if let streamError {
        throw NSError(
          domain: "RecipeGenerator",
          code: 0,
          userInfo: [NSLocalizedDescriptionKey: streamError]
        )
      }

      let data = Data(jsonResponse.utf8)
      recipe = try JSONDecoder().decode(Recipe.self, from: data)
      statusMessage = "Recipe generated!"
      isGenerating = false
    } catch {
      statusMessage = "Error generating recipe: \(error.localizedDescription)"
      isGenerating = false
      throw error
    }
  }
}
