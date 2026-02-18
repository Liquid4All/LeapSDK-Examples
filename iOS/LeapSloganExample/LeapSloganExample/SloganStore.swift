import LeapSDK
import LeapSDKMacros
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

  private var modelRunner: (any ModelRunner)?
  private var conversation: (any Conversation)?
  private var generationTask: Task<Void, Never>?

  @MainActor
  func loadModel() async {
    guard !modelLoaded else { return }
    isModelLoading = true
    generatedText = "Initiating AI model ...."
    modelStatus = "● Model: Loading..."
    modelStatusColor = .orange

    do {
      // Use manifest downloading for LFM2.5-1.2B-Instruct (best for instruction following)
      modelRunner = try await Leap.shared.load(
        model: "LFM2.5-1.2B-Instruct",
        quantization: "Q4_0",
        progress: { [weak self] progress, speed in
          Task { @MainActor in
            if progress < 1.0 {
              self?.generatedText = "Downloading model: \(Int(progress * 100))%"
              self?.modelStatus = "● Model: Downloading \(Int(progress * 100))%"
            } else {
              self?.generatedText = "Loading model into memory..."
              self?.modelStatus = "● Model: Loading..."
            }
          }
        }
      )

      // Initialize conversation for regular generation
      let systemMessage = ChatMessage(role: .system, content: .text(SYSTEM_PROMPT))
      conversation = Conversation(
        modelRunner: modelRunner!,
        history: [systemMessage]
      )

      modelLoaded = true
      modelStatus = "● Model: Loaded ✓"
      modelStatusColor = .green
      generatedText = "Slogan generator ready! Time to create your brand's tagline! 🎯\n\n"
    } catch {
      print(error)
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
      do {
        // Use constrained generation for structured output
        generatedText = "Generating structured slogans...\n\n"

        // Create conversation with constrained generation options
        let systemMessage = ChatMessage(role: .system, content: .text(SYSTEM_PROMPT))
        let constrainedConversation = Conversation(
          modelRunner: modelRunner!,
          history: [systemMessage]
        )

        // Set up generation options with structured output
        let options = GenerationOptions()
        options.setResponseFormat(type: SloganResponse.self)

        let userMessage = ChatMessage(
          role: .user, content: .text(String(format: USER_PROMPT_TEMPLATE, topic)))
        let stream = constrainedConversation.generateResponse(
          message: userMessage, generationOptions: options)

        var jsonResponse = ""
        for try await response in stream {
          if Task.isCancelled { break }
          switch onEnum(of: response) {
          case .chunk(let chunk):
            jsonResponse.append(chunk.text)
          case .audioSample, .complete, .reasoningChunk, .functionCalls:
            break
          }
        }

        // Parse the JSON response
        let jsonData = jsonResponse.data(using: .utf8)!
        let sloganResponse = try JSONDecoder().decode(SloganResponse.self, from: jsonData)

        // Format the structured output nicely
        var formattedOutput = ""
        formattedOutput += "🎯 **Short Slogan:**\n\(sloganResponse.shortSlogan)\n\n"
        formattedOutput += "📝 **Long Tagline:**\n\(sloganResponse.longTagline)\n\n"
        formattedOutput += "🎭 **Tone:** \(sloganResponse.tone)\n\n"
        formattedOutput += "👥 **Target Audience:** \(sloganResponse.targetAudience)\n\n"
        formattedOutput += "✨ **Key Benefits:**\n"
        for benefit in sloganResponse.keyBenefits {
          formattedOutput += "  • \(benefit)\n"
        }

        generatedText = formattedOutput
        isGenerating = false
        isThinking = false
        generationTask = nil

      } catch {
        // Fallback to regular conversation if constrained generation fails
        print("Constrained generation failed: \(error)")
        print("Falling back to regular conversation...")

        // Reset conversation for each generation to avoid context length issues
        let systemMessage = ChatMessage(role: .system, content: .text(SYSTEM_PROMPT))
        conversation = Conversation(
          modelRunner: modelRunner!,
          history: [systemMessage]
        )

        let prompt = String(format: USER_PROMPT_TEMPLATE, topic)
        let userMessage = ChatMessage(role: .user, content: .text(prompt))

        let stream = conversation!.generateResponse(message: userMessage)

        generatedText = "Generating (fallback mode)...\n\n"

        do {
          for try await response in stream {
            if Task.isCancelled { break }

            switch onEnum(of: response) {
            case .chunk(let chunk):
              isThinking = false
              if generatedText.hasPrefix("Generating (fallback mode)...") {
                generatedText = chunk.text
              } else {
                generatedText.append(chunk.text)
              }
            case .audioSample:
              break
            case .complete:
              isGenerating = false
              isThinking = false
              generationTask = nil
            case .reasoningChunk:
              if !isThinking {
                isThinking = true
              }
            case .functionCalls:
              break
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
  }

  deinit {
    generationTask?.cancel()
  }
}
