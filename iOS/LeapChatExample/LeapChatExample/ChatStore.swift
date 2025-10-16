import LeapSDK
import PhotosUI
import SwiftUI

@Observable
class ChatStore {
  var input: String = ""
  var messages: [MessageBubble] = []
  var isLoading = false
  var isModelLoading = true
  var currentAssistantMessage = ""
  var attachedImage: UIImage?

  var conversation: Conversation?
  var modelRunner: ModelRunner?

  @MainActor
  func setupModel() async {
    guard modelRunner == nil else { return }
    isModelLoading = true

    do {
      // First, verify the SDK is accessible
      print("LeapSDK version check...")
      messages.append(
        MessageBubble(
          content: "🔍 Checking LeapSDK integration...",
          isUser: false))

      guard
        let modelURL = Bundle.main.url(
          forResource: "LFM2-8B-A1B-Q4_0", withExtension: "gguf")
      else {
        messages.append(
          MessageBubble(
            content: "❗️ Could not find LFM2-VL-1.6B-Q8_0.gguf in the bundle.",
            isUser: false))
        isModelLoading = false
        return
      }

      print("Model URL: \(modelURL)")
      messages.append(
        MessageBubble(
          content: "📁 Found model at: \(modelURL.lastPathComponent)",
          isUser: false))

      // The SDK will automatically look for mmproj-*.gguf files
      messages.append(
        MessageBubble(
          content: "🔍 Checking for vision support files...",
          isUser: false))

      let modelRunner = try await Leap.load(
        url: modelURL,
        options: LiquidInferenceEngineOptions(
          bundlePath: modelURL.path(),
          contextSize: 1024,
        ))
      self.modelRunner = modelRunner
      conversation = Conversation(modelRunner: modelRunner, history: [])
      messages.append(
        MessageBubble(
          content: "✅ Model loaded successfully! You can start chatting.",
          isUser: false))
    } catch {
      print("Error loading model: \(error)")
      let errorMessage = "🚨 Failed to load model: \(error.localizedDescription)"
      messages.append(MessageBubble(content: errorMessage, isUser: false))

      // Check if it's a LeapError
      if let leapError = error as? LeapError {
        print("LeapError details: \(leapError)")
        messages.append(
          MessageBubble(content: "📋 Error type: \(String(describing: leapError))", isUser: false))
      }
    }

    isModelLoading = false
  }

  @MainActor
  func send() async {
    guard conversation != nil else { return }

    let trimmed = input.trimmingCharacters(in: .whitespacesAndNewlines)
    guard !trimmed.isEmpty || attachedImage != nil else { return }

    // Create message content array
    var messageContent: [ChatMessageContent] = []

    // Add image if present
    if let image = attachedImage {
      do {
        let imageContent = try ChatMessageContent.fromUIImage(image)
        messageContent.append(imageContent)
      } catch {
        print("Error converting image: \(error)")
        return
      }
    }

    // Add text if present
    if !trimmed.isEmpty {
      messageContent.append(.text(trimmed))
    }

    let userMessage = ChatMessage(role: .user, content: messageContent)

    // Create display content for the message bubble
    var displayContent = trimmed
    if attachedImage != nil {
      displayContent = displayContent.isEmpty ? "[Image]" : "[Image] \(displayContent)"
    }

    messages.append(MessageBubble(content: displayContent, isUser: true, image: attachedImage))
    input = ""
    attachedImage = nil
    isLoading = true
    currentAssistantMessage = ""

    let stream = conversation!.generateResponse(message: userMessage)
    do {
      for try await resp in stream {
        print(resp)
        switch resp {
        case .reasoningChunk(let str): break
        case .chunk(let str):
          currentAssistantMessage.append(str)
        case .audioSample:
          break
        case .complete(let completion):
          let finalText = completion.message.content.compactMap { content -> String? in
            if case .text(let text) = content {
              return text
            }
            return nil
          }.joined()
          if !finalText.isEmpty {
            currentAssistantMessage = finalText
          }
          if !currentAssistantMessage.isEmpty {
            messages.append(MessageBubble(content: currentAssistantMessage, isUser: false))
          }
          currentAssistantMessage = ""
          isLoading = false
        case .functionCall(_):
          break  // Function calls not used in this example
        }
      }
    } catch {
      currentAssistantMessage = "Error: \(error.localizedDescription)"
      messages.append(MessageBubble(content: currentAssistantMessage, isUser: false))
      currentAssistantMessage = ""
      isLoading = false
    }
  }

  @MainActor
  func loadImageFrom(item: PhotosPickerItem) async {
    guard let data = try? await item.loadTransferable(type: Data.self),
      let image = UIImage(data: data)
    else {
      print("Failed to load image from PhotosPickerItem")
      return
    }
    attachedImage = image
  }

  func removeAttachedImage() {
    attachedImage = nil
  }
}
