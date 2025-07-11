import SwiftUI

struct TypingIndicator: View {
  @State private var animating = false

  var body: some View {
    HStack(spacing: 4) {
      ForEach(0..<3) { index in
        Circle()
          .fill(Color.gray)
          .frame(width: 8, height: 8)
          .scaleEffect(animating ? 1.0 : 0.5)
          .animation(
            Animation.easeInOut(duration: 0.6)
              .repeatForever()
              .delay(Double(index) * 0.2),
            value: animating
          )
      }
    }
    .padding(.horizontal, 16)
    .padding(.vertical, 10)
    .background(Color(.systemGray5))
    .clipShape(RoundedRectangle(cornerRadius: 18))
    .onAppear {
      animating = true
    }
    .id("typing")
  }
}
