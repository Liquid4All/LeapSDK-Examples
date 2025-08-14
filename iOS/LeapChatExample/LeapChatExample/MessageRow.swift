import SwiftUI

struct MessageRow: View {
  let message: MessageBubble

  var body: some View {
    if message.isTool {
      // Tool message display
      HStack(alignment: .top, spacing: 8) {
        Image(systemName: "wrench.and.screwdriver")
          .foregroundColor(.secondary)
          .frame(width: 24, height: 24)
          .background(
            RoundedRectangle(cornerRadius: 6)
              .stroke(Color.secondary, lineWidth: 1)
          )
        
        VStack(alignment: .leading, spacing: 4) {
          Text("Tool")
            .font(.caption)
            .foregroundColor(.secondary)
            .fontWeight(.medium)
          
          Text(message.content)
            .padding(.horizontal, 12)
            .padding(.vertical, 8)
            .background(Color(.systemGray6))
            .foregroundColor(.primary)
            .clipShape(RoundedRectangle(cornerRadius: 12))
        }
        
        Spacer(minLength: 60)
      }
      .padding(.horizontal, 4)
    } else {
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
}
