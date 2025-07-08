import Foundation

struct MessageBubble {
  let id = UUID()
  let content: String
  let isUser: Bool
  let timestamp: Date = Date()
}
