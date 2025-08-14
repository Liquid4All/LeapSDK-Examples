import LeapSDK
import SwiftUI

@Observable
class ChatStore {
  var input: String = ""
  var messages: [MessageBubble] = []
  var isLoading = false
  var isModelLoading = true
  var currentAssistantMessage = ""

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
          messageType: .assistant))

      guard
        let modelURL = Bundle.main.url(
          forResource: "LFM2-1.2B-8da4w_output_8da8w-seq_4096", withExtension: "bundle")
      else {
        messages.append(
          MessageBubble(
            content: "❗️ Could not find LFM2-1.2B-8da4w_output_8da8w-seq_4096.bundle in the bundle.",
            messageType: .assistant))
        isModelLoading = false
        return
      }

      print("Model URL: \(modelURL)")
      messages.append(
        MessageBubble(
          content: "📁 Found model bundle at: \(modelURL.lastPathComponent)",
          messageType: .assistant))

      let modelRunner = try await Leap.load(url: modelURL)
      self.modelRunner = modelRunner
      let conversation = Conversation(modelRunner: modelRunner, history: [])
      
      // Register the compute_sum function
      conversation.registerFunction(
        LeapFunction(
          name: "compute_sum",
          description: "Compute sum of a series of numbers",
          parameters: [
            LeapFunctionParameter(
              name: "values",
              type: LeapFunctionParameterType.array(ArrayType(
                itemType: LeapFunctionParameterType.string(StringType())
              )),
              description: "Numbers to compute sum. Values should be represented as strings."
            )
          ]
        )
      )
      
      self.conversation = conversation
      messages.append(
        MessageBubble(
          content: "✅ Model loaded successfully! You can start chatting.",
          messageType: .assistant))
    } catch {
      print("Error loading model: \(error)")
      let errorMessage = "🚨 Failed to load model: \(error.localizedDescription)"
      messages.append(MessageBubble(content: errorMessage, messageType: .assistant))

      // Check if it's a LeapError
      if let leapError = error as? LeapError {
        print("LeapError details: \(leapError)")
        messages.append(
          MessageBubble(content: "📋 Error type: \(String(describing: leapError))", messageType: .assistant))
      }
    }

    isModelLoading = false
  }

  @MainActor
  func send() async {
    guard conversation != nil else { return }

    let trimmed = input.trimmingCharacters(in: .whitespacesAndNewlines)
    guard !trimmed.isEmpty else { return }

    let userMessage = ChatMessage(role: .user, content: [.text(trimmed)])
    messages.append(MessageBubble(content: trimmed, messageType: .user))
    input = ""
    isLoading = true
    currentAssistantMessage = ""

    let stream = conversation!.generateResponse(message: userMessage)
    var functionCallsToProcess: [LeapFunctionCall] = []
    
    do {
      for try await resp in stream {
        print(resp)
        switch resp {
        case .chunk(let str):
          currentAssistantMessage.append(str)
        case .complete(_, _):
          if !currentAssistantMessage.isEmpty {
            messages.append(MessageBubble(content: currentAssistantMessage, messageType: .assistant))
          }
          currentAssistantMessage = ""
          isLoading = false
        case .functionCall(let calls):
          functionCallsToProcess.append(contentsOf: calls)
        default:
          break  // Handle any other case
        }
      }
      
      // Process function calls after the generation is complete
      if !functionCallsToProcess.isEmpty {
        await processFunctionCalls(functionCallsToProcess)
      }
    } catch {
      currentAssistantMessage = "Error: \(error.localizedDescription)"
      messages.append(MessageBubble(content: currentAssistantMessage, messageType: .assistant))
      currentAssistantMessage = ""
      isLoading = false
    }
  }
  
  @MainActor
  private func processFunctionCalls(_ functionCalls: [LeapFunctionCall]) async {
    for call in functionCalls {
      switch call.name {
      case "compute_sum":
        // Extract values from arguments
        if let valuesArray = call.arguments["values"] as? [String] {
          var sum = 0.0
          for value in valuesArray {
            sum += Double(value) ?? 0.0
          }
          let result = "Sum = \(sum)"
          
          // Add tool message to display
          messages.append(MessageBubble(content: result, messageType: .tool))
          
          // Send tool response back to conversation
          let toolMessage = ChatMessage(role: .tool, content: [.text(result)])
          await sendToolResponse(toolMessage)
        } else {
          let errorResult = "Error: Could not process values for compute_sum"
          messages.append(MessageBubble(content: errorResult, messageType: .tool))
          
          let toolMessage = ChatMessage(role: .tool, content: [.text(errorResult)])
          await sendToolResponse(toolMessage)
        }
      default:
        let unknownResult = "Tool: \(call.name) is not available"
        messages.append(MessageBubble(content: unknownResult, messageType: .tool))
        
        let toolMessage = ChatMessage(role: .tool, content: [.text(unknownResult)])
        await sendToolResponse(toolMessage)
      }
    }
  }
  
  @MainActor
  private func sendToolResponse(_ toolMessage: ChatMessage) async {
    guard let conversation = conversation else { return }
    
    isLoading = true
    currentAssistantMessage = ""
    
    let stream = conversation.generateResponse(message: toolMessage)
    var functionCallsToProcess: [LeapFunctionCall] = []
    
    do {
      for try await resp in stream {
        print(resp)
        switch resp {
        case .chunk(let str):
          currentAssistantMessage.append(str)
        case .complete(_, _):
          if !currentAssistantMessage.isEmpty {
            messages.append(MessageBubble(content: currentAssistantMessage, messageType: .assistant))
          }
          currentAssistantMessage = ""
          isLoading = false
        case .functionCall(let calls):
          functionCallsToProcess.append(contentsOf: calls)
        default:
          break
        }
      }
      
      // Process any additional function calls
      if !functionCallsToProcess.isEmpty {
        await processFunctionCalls(functionCallsToProcess)
      }
    } catch {
      currentAssistantMessage = "Error: \(error.localizedDescription)"
      messages.append(MessageBubble(content: currentAssistantMessage, messageType: .assistant))
      currentAssistantMessage = ""
      isLoading = false
    }
  }
}
