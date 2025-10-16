import SwiftUI

struct AudioDemoView: View {
  @State private var store = AudioDemoStore()

  var body: some View {
    NavigationStack {
      VStack(spacing: 12) {
        if store.isModelLoading {
          ProgressView("Loading model...")
            .padding()
        }

        List {
          ForEach(store.messages) { message in
            VStack(alignment: message.isUser ? .trailing : .leading, spacing: 8) {
              if !message.text.isEmpty {
                Text(message.text)
                  .font(.body)
                  .padding(12)
                  .background(message.isUser ? Color.blue.opacity(0.15) : Color.gray.opacity(0.15))
                  .cornerRadius(14)
                  .frame(maxWidth: .infinity, alignment: message.isUser ? .trailing : .leading)
              }

              if let audioData = message.audioData {
                Button {
                  store.playAudio(audioData)
                } label: {
                  Label("Play audio", systemImage: "play.circle")
                }
                .frame(maxWidth: .infinity, alignment: message.isUser ? .trailing : .leading)
              }
            }
            .listRowSeparator(.hidden)
            .listRowInsets(EdgeInsets(top: 4, leading: 8, bottom: 4, trailing: 8))
          }

          if !store.streamingText.isEmpty {
            VStack(alignment: .leading, spacing: 8) {
              Text(store.streamingText)
                .font(.body)
                .padding(12)
                .background(Color.gray.opacity(0.15))
                .cornerRadius(14)
                .frame(maxWidth: .infinity, alignment: .leading)
            }
            .listRowSeparator(.hidden)
            .listRowInsets(EdgeInsets(top: 4, leading: 8, bottom: 4, trailing: 8))
          }
        }
        .listStyle(.plain)

        if let status = store.status {
          Text(status)
            .font(.footnote)
            .foregroundStyle(.secondary)
            .frame(maxWidth: .infinity, alignment: .leading)
        }

        VStack(spacing: 8) {
          HStack {
            TextField("Enter text prompt", text: $store.inputText)
              .textFieldStyle(.roundedBorder)

            Button("Send") {
              store.sendTextPrompt()
            }
            .disabled(
              store.inputText.trimmingCharacters(in: .whitespacesAndNewlines).isEmpty
                || store.isGenerating)
          }

          HStack {
            Button {
              store.toggleRecording()
            } label: {
              Label(
                store.isRecording ? "Stop Recording" : "Record",
                systemImage: store.isRecording ? "stop.circle.fill" : "mic.circle"
              )
            }
            .buttonStyle(.borderedProminent)

            if store.isRecording {
              Button("Cancel") {
                store.cancelRecording()
              }
              .buttonStyle(.bordered)
            }

            Spacer()

            if store.isGenerating {
              ProgressView()
                .progressViewStyle(.circular)
            }
          }
        }
      }
      .padding()
      .navigationTitle("Audio Demo")
    }
    .task {
      await store.setupModel()
    }
  }
}

#Preview {
  AudioDemoView()
}
