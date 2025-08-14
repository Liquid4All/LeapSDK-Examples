import Foundation

enum MessageType {
  case user
  case assistant
  case tool
}

struct MessageBubble {
  let id = UUID()
  let content: String
  let messageType: MessageType
  let timestamp: Date = Date()
  
  var isUser: Bool {
    return messageType == .user
  }
  
  var isTool: Bool {
    return messageType == .tool
  }
}
