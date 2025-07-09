import SwiftUI

struct ContentView: View {
  @State private var store = ChatStore()

  var body: some View {
    NavigationStack {
      VStack(spacing: 0) {
        MessagesListView(store: store)
        ChatInputView(store: store)
      }
      .navigationTitle("Leap Demo")
      .navigationBarTitleDisplayMode(.inline)
      .task { await store.setupModel() }
    }
  }
}

#Preview {
  ContentView()
}
