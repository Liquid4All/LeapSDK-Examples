import SwiftUI

struct MessagesListView: View {
  @Bindable var store: ChatStore

  var body: some View {
    ScrollViewReader { proxy in
      ScrollView {
        LazyVStack(spacing: 8) {
          if store.isModelLoading {
            VStack(spacing: 8) {
              ProgressView()
              Text("Loading model...")
                .foregroundColor(.secondary)
            }
            .padding()
          } else {
            ForEach(store.messages, id: \.id) { message in
              MessageRow(message: message)
            }

            if store.isLoading && !store.currentAssistantMessage.isEmpty {
              MessageRow(
                message: MessageBubble(content: store.currentAssistantMessage, messageType: .assistant)
              )
              .id("streaming")
            } else if store.isLoading {
              HStack {
                TypingIndicator()
                Spacer()
              }
              .padding(.horizontal)
              .id("typing")
            }
          }
        }
        .padding(.horizontal)
        .padding(.top, 8)
      }
      .onChange(of: store.messages.count) { _ in
        if let lastMessage = store.messages.last {
          withAnimation(.easeOut(duration: 0.3)) {
            proxy.scrollTo(lastMessage.id, anchor: .bottom)
          }
        }
      }
      .onChange(of: store.currentAssistantMessage) { _ in
        withAnimation(.easeOut(duration: 0.1)) {
          if store.isLoading && !store.currentAssistantMessage.isEmpty {
            proxy.scrollTo("streaming", anchor: .bottom)
          } else {
            proxy.scrollTo("typing", anchor: .bottom)
          }
        }
      }
    }
  }
}
