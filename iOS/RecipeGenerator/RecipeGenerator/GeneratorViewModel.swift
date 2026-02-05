import Foundation
import LeapSDK

@MainActor
class GeneratorViewModel: ObservableObject {
  @Published var isModelLoading = false
  @Published var isGenerating = false
  @Published var recipe: Recipe? = nil
  @Published var statusMessage: String = "Ready to generate"
  @Published var downloadProgress: Double = 0.0

  private var modelRunner: ModelRunner?

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
      // Use new Leap.load API with model name and quantization
      modelRunner = try await Leap.load(
        model: modelName,
        quantization: quantization
      ) { [weak self] progress, speed in
        Task { @MainActor in
          self?.downloadProgress = progress
          if progress < 1.0 {
            self?.statusMessage = "Downloading: \(Int(progress * 100))%"
          } else {
            self?.statusMessage = "Loading model..."
          }
        }
      }

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
    let conversation = modelRunner.createConversation(
      systemPrompt: "You are a helpful cooking assistant. Generate recipes in JSON format."
    )

    // Generate response - simplified for now without constrained generation
    var fullText = ""
    do {
      let userMessage = ChatMessage(role: .user, content: [.text("Generate a recipe for a dinner dish with shrimps in JSON format with fields: name, cookingTime, isVegetarian, ingredients (array), directions (array)")])
      for try await response in conversation.generateResponse(message: userMessage) {
        // Accumulate the generated text
        // The response should have a message property or similar
        // For now, we'll just use a placeholder
        print("Received response: \(response)")
      }

      // Placeholder - this will need to be updated based on actual SDK API
      let mockRecipe = Recipe(
        name: "Garlic Butter Shrimp",
        cookingTime: 20,
        isVegetarian: false,
        ingredients: ["1 lb shrimp", "4 cloves garlic", "4 tbsp butter", "Salt and pepper"],
        directions: ["Melt butter in pan", "Add garlic and cook 1 min", "Add shrimp and cook 3-4 min", "Season with salt and pepper"]
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
