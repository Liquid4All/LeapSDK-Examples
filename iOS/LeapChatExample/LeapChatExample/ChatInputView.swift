import SwiftUI

struct ChatInputView: View {
  @Bindable var store: ChatStore

  var body: some View {
    VStack(spacing: 12) {
      Divider()

      HStack(spacing: 12) {
        TextField("Message", text: $store.input, axis: .vertical)
          .textFieldStyle(.roundedBorder)
          .disabled(store.isModelLoading)

        Button(action: { Task { await store.send() } }) {
          Image(systemName: "paperplane.fill")
            .foregroundColor(.white)
            .frame(width: 32, height: 32)
            .background(
              store.input.trimmingCharacters(in: .whitespacesAndNewlines).isEmpty
                || store.isModelLoading || store.isLoading
                ? Color.gray : Color.blue
            )
            .clipShape(Circle())
        }
        .disabled(
          store.isModelLoading || store.isLoading
            || store.input.trimmingCharacters(in: .whitespacesAndNewlines).isEmpty)
      }
      .padding(.horizontal)
    }
    .background(.ultraThinMaterial)
  }
}
