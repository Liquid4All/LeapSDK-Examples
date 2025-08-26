import Foundation
import UIKit

struct MessageBubble {
  let id = UUID()
  let content: String
  let isUser: Bool
  let timestamp: Date = Date()
  let image: UIImage?

  init(content: String, isUser: Bool, image: UIImage? = nil) {
    self.content = content
    self.isUser = isUser
    self.image = image
  }
}
