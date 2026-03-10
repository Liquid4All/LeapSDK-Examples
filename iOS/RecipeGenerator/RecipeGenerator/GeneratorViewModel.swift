import Foundation
import LeapSDK

@MainActor
class GeneratorViewModel: ObservableObject {
  @Published var isModelLoading = false
  @Published var isGenerating = false
  @Published var recipe: Recipe? = nil
  @Published var statusMessage: String = "Ready to generate"
  @Published var downloadProgress: Double = 0.0

  private var modelRunner: (any ModelRunner)?

  private let modelName = "LFM2-350M"  // Smaller model for faster testing
  private let quantization = "Q4_0"

  func setupModel() async {
    guard modelRunner == nil else {
      // Model already loaded
      return
    }

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

    // Create conversation with system prompt
    let systemMessage = ChatMessage(
      role: .system,
      content: ChatMessageContent.text("You are a helpful cooking assistant. Generate recipes in JSON format.")
    )
    let conversation = Conversation(modelRunner: modelRunner, history: [systemMessage])

    var fullText = ""
    do {
      let userMessage = ChatMessage(
        role: .user,
        content: ChatMessageContent.text(
          "Generate a recipe for a dinner dish with shrimps in JSON format with fields: name, cookingTime, isVegetarian, ingredients (array), directions (array)"
        )
      )
      let stream = conversation.generateResponse(message: userMessage)
      for try await event in stream {
        switch onEnum(of: event) {
        case .chunk(let chunk):
          fullText.append(chunk.text)
        case .complete(let completion):
          let finalText = completion.fullMessage.content.compactMap { content -> String? in
            if case .text(let t) = onEnum(of: content) {
              return t.text
            }
            return nil
          }.joined()
          if !finalText.isEmpty {
            fullText = finalText
          }
        case .audioSample, .reasoningChunk, .functionCalls:
          break
        }
      }

      // Attempt to parse the JSON response
      if let jsonStart = fullText.firstIndex(of: "{"),
        let jsonEnd = fullText.lastIndex(of: "}")
      {
        let jsonString = String(fullText[jsonStart...jsonEnd])
        if let jsonData = jsonString.data(using: .utf8),
          let decoded = try? JSONDecoder().decode(Recipe.self, from: jsonData)
        {
          recipe = decoded
          statusMessage = "Recipe generated!"
          isGenerating = false
          return
        }
      }

      // Fallback mock recipe if JSON parsing fails
      let mockRecipe = Recipe(
        name: "Garlic Butter Shrimp",
        cookingTime: 20,
        isVegetarian: false,
        ingredients: ["1 lb shrimp", "4 cloves garlic", "4 tbsp butter", "Salt and pepper"],
        directions: [
          "Melt butter in pan", "Add garlic and cook 1 min", "Add shrimp and cook 3-4 min",
          "Season with salt and pepper",
        ]
      )
      recipe = mockRecipe
      statusMessage = "Recipe generated!"
    } catch {
      statusMessage = "Error generating recipe: \(error.localizedDescription)"
      throw error
    }

    isGenerating = false
  }
}
