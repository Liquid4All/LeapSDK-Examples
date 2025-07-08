import SwiftUI

struct MessageRow: View {
  let message: MessageBubble

  var body: some View {
    HStack {
      if message.isUser {
        Spacer(minLength: 60)

        Text(message.content)
          .padding(.horizontal, 16)
          .padding(.vertical, 10)
          .background(Color.blue)
          .foregroundColor(.white)
          .clipShape(RoundedRectangle(cornerRadius: 18))
      } else {
        Text(message.content)
          .padding(.horizontal, 16)
          .padding(.vertical, 10)
          .background(Color(.systemGray5))
          .foregroundColor(.primary)
          .clipShape(RoundedRectangle(cornerRadius: 18))

        Spacer(minLength: 60)
      }
    }
    .padding(.horizontal, 4)
  }
}
