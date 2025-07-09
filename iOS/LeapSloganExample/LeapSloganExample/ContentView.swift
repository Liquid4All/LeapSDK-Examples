import SwiftUI

struct ContentView: View {
  @State private var store = SloganStore()

  var body: some View {
    VStack(spacing: 0) {
      // Logo/Title
      Text("Slogan Generator")
        .font(.system(.title3, design: .monospaced))
        .padding(.top, 50)
        .padding(.bottom, 20)

      // Business description input
      VStack(alignment: .leading, spacing: 8) {
        TextField("Describe your product..", text: $store.businessDescription, axis: .vertical)
          .textFieldStyle(.roundedBorder)
          .lineLimit(1...3)
          .padding(.horizontal)

        // Generate button
        Button(action: {
          Task {
            await store.generateSlogans()
          }
        }) {
          HStack {
            Text(store.isGenerating ? "Stop 🛑" : "Generate ✨")
              .font(.system(size: 16, weight: .medium))
          }
          .frame(maxWidth: .infinity)
          .padding(.vertical, 12)
          .background(Color.accentColor)
          .foregroundColor(.white)
          .cornerRadius(8)
        }
        .padding(.horizontal)
        .disabled(store.isModelLoading)

        // Model status
        if !store.modelStatus.isEmpty {
          Text(store.modelStatus)
            .font(.caption)
            .foregroundColor(store.modelStatusColor)
            .padding(.horizontal)
            .padding(.top, 4)
        }
      }
      .padding(.vertical)

      // Generated slogans output
      ScrollViewReader { proxy in
        ScrollView {
          VStack(alignment: .leading, spacing: 12) {
            // Thinking indicator
            if store.isThinking {
              HStack(spacing: 8) {
                ProgressView()
                  .progressViewStyle(CircularProgressViewStyle())
                  .scaleEffect(0.8)
                Text("Thinking...")
                  .font(.system(size: 14, weight: .medium))
                  .foregroundColor(.secondary)
              }
              .padding(.horizontal)
              .padding(.top, 12)
            }

            Text(store.generatedText)
              .font(.system(size: 14))
              .lineSpacing(4)
              .frame(maxWidth: .infinity, alignment: .leading)
              .padding(.horizontal)
              .padding(.bottom)
              .id("generatedText")
              .onChange(of: store.generatedText) { _, _ in
                // Auto-scroll to bottom when text changes
                withAnimation {
                  proxy.scrollTo("generatedText", anchor: .bottom)
                }
              }
          }
        }
        .background(Color(UIColor.systemGray6))
        .cornerRadius(8)
        .padding(.horizontal)
      }

      Spacer(minLength: 20)
    }
    .background(Color(UIColor.systemBackground))
    .onAppear {
      // Optionally preload the model
      Task {
        // await store.loadModel()
      }
    }
  }
}
