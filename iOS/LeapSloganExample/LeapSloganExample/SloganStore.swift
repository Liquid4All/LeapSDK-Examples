import LeapSDK
import SwiftUI

// System prompt and user prompt constants
private let SYSTEM_PROMPT =
  "You are a marketing expert. Suggest engaging and creative slogans based on a business description provided by the user."
private let USER_PROMPT_TEMPLATE = "Suggest slogans for this business: \"%@\""

@Observable
class SloganStore {
  var businessDescription: String = ""
  var generatedText: String = ""
  var isGenerating = false
  var isThinking = false
  var isModelLoading = false
  var modelLoaded = false
  var modelStatus: String = ""
  var modelStatusColor: Color = .red

  private var modelRunner: ModelRunner?
  private var conversation: Conversation?
  private var generationTask: Task<Void, Never>?

  @MainActor
  func loadModel() async {
    guard !modelLoaded else { return }
    isModelLoading = true
    generatedText = "Initiating AI model ...."
    modelStatus = "● Model: Loading..."
    modelStatusColor = .orange

    do {
      guard
        let modelURL = Bundle.main.url(
          forResource: "qwen3-1_7b_8da4w",
          withExtension: "bundle"
        )
      else {
        throw LeapError.modelLoadingFailure("Could not find model bundle in the app bundle", nil)
      }

      modelRunner = try await Leap.load(url: modelURL)
      let systemMessage = ChatMessage(role: .system, content: [.text(SYSTEM_PROMPT)])
      conversation = Conversation(
        modelRunner: modelRunner!,
        history: [systemMessage]
      )

      modelLoaded = true
      modelStatus = "● Model: Loaded ✓"
      modelStatusColor = .green
      generatedText = "Slogan generator ready! Time to create your brand's tagline! 🎯\n\n"
    } catch {
      generatedText =
        "Error loading slogan generator: \(error.localizedDescription)\n\nOops! An error happened."
      modelStatus = "● Model: Error"
      modelStatusColor = .red
    }

    isModelLoading = false
  }

  @MainActor
  func generateSlogans() async {
    // If already generating, stop the current generation
    if isGenerating {
      generationTask?.cancel()
      generationTask = nil
      isGenerating = false
      isThinking = false

      let currentText = generatedText
      if currentText.hasPrefix("Generating...")
        && currentText.trimmingCharacters(in: .whitespacesAndNewlines) == "Generating..."
      {
        generatedText =
          "Slogan generation stopped. Try describing your business differently for new ideas."
      } else {
        generatedText.append("\n\n[Slogan generation stopped...]")
      }
      return
    }

    let topic = businessDescription.trimmingCharacters(in: .whitespacesAndNewlines)
    guard !topic.isEmpty else {
      generatedText = "Describe your company and I'll create slogan suggestions."
      return
    }

    // Load model if not loaded
    if !modelLoaded {
      await loadModel()
      guard modelLoaded else { return }
    }

    isGenerating = true
    isThinking = false
    generatedText = "Generating...\n\n"

    generationTask = Task { @MainActor in
      // Reset conversation for each generation to avoid context length issues
      let systemMessage = ChatMessage(role: .system, content: [.text(SYSTEM_PROMPT)])
      conversation = Conversation(
        modelRunner: modelRunner!,
        history: [systemMessage]
      )

      let prompt = String(format: USER_PROMPT_TEMPLATE, topic)
      let userMessage = ChatMessage(role: .user, content: [.text(prompt)])

      let stream = conversation!.generateResponse(message: userMessage)

      do {
        for try await response in stream {
          if Task.isCancelled { break }

          switch response {
          case .chunk(let text):
            isThinking = false
            if generatedText.hasPrefix("Generating...") {
              generatedText = text
            } else {
              generatedText.append(text)
            }
          case .complete(_, _):
            isGenerating = false
            isThinking = false
            generationTask = nil
          case .reasoningChunk(_):
            // Set thinking state when we receive reasoning chunks
            if !isThinking {
              isThinking = true
            }
          @unknown default:
            break  // Handle any future cases
          }
        }
      } catch {
        isGenerating = false
        isThinking = false
        generationTask = nil
        generatedText = "Error generating slogans: \(error.localizedDescription)"
      }
    }
  }

  deinit {
    generationTask?.cancel()
  }
}
