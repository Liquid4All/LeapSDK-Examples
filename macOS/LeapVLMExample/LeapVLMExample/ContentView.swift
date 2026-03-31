import SwiftUI

struct ContentView: View {
  @State private var store = VLMStore()

  var body: some View {
    ScrollView {
      VStack(spacing: 20) {
        Image("pug")
          .resizable()
          .aspectRatio(contentMode: .fit)
          .frame(maxWidth: .infinity)
          .clipShape(RoundedRectangle(cornerRadius: 12))

        Button {
          Task { await store.describeImage() }
        } label: {
          Text("Describe")
            .font(.headline)
            .foregroundColor(.white)
            .frame(maxWidth: .infinity)
            .padding()
            .background(store.isModelReady && !store.isGenerating ? Color.blue : Color.gray)
            .clipShape(RoundedRectangle(cornerRadius: 10))
        }
        .buttonStyle(.plain)
        .disabled(!store.isModelReady || store.isGenerating)

        Text(store.status)
          .font(.subheadline)
          .foregroundColor(.gray)

        if !store.generatedText.isEmpty {
          Text(store.generatedText)
            .foregroundColor(.white)
            .frame(maxWidth: .infinity, alignment: .leading)
            .textSelection(.enabled)
        }
      }
      .padding()
    }
    .background(.black)
    .task { await store.setupModel() }
  }
}
