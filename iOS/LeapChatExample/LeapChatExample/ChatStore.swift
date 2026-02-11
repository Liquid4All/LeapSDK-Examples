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
      messages.append(
        MessageBubble(
          content: "🔍 Initializing LeapSDK...",
          isUser: false))

      messages.append(
        MessageBubble(
          content: "📦 Downloading LFM2-VL-450M model (compact vision support)...",
          isUser: false))

      // Use manifest downloading for LFM2-VL-450M with Q8_0 (smaller model, faster download)
      let modelRunner = try await Leap.load(
        model: "LFM2-VL-450M",
        quantization: "Q8_0",
        options: LiquidInferenceEngineManifestOptions(
          contextSize: 4096  // Reduced from default for mobile memory constraints
        )
      ) { [weak self] progress, speed in
        Task { @MainActor in
          if progress < 1.0 {
            let progressPercent = Int(progress * 100)
            self?.messages.append(
              MessageBubble(
                content: "⏳ Downloading: \(progressPercent)%",
                isUser: false))
          } else {
            self?.messages.append(
              MessageBubble(
                content: "🧠 Loading model into memory...",
                isUser: false))
          }
        }
      }

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
      messageContent.append(ChatMessageContent.text(trimmed))
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

    let stream = conversation!.generateResponse(message: userMessage, generationOptions: nil)
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
