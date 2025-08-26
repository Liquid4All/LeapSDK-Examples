import PhotosUI
import SwiftUI

struct ChatInputView: View {
  @Bindable var store: ChatStore
  @State private var showingImagePicker = false
  @State private var selectedImage: PhotosPickerItem?

  var body: some View {
    VStack(spacing: 12) {
      Divider()

      // Show attached image preview if present
      if let attachedImage = store.attachedImage {
        HStack {
          Image(uiImage: attachedImage)
            .resizable()
            .aspectRatio(contentMode: .fit)
            .frame(maxWidth: 150, maxHeight: 100)
            .clipShape(RoundedRectangle(cornerRadius: 8))

          Spacer()

          Button(action: { store.removeAttachedImage() }) {
            Image(systemName: "xmark.circle.fill")
              .foregroundColor(.gray)
              .font(.title2)
          }
        }
        .padding(.horizontal)
        .padding(.top, 8)
      }

      HStack(spacing: 12) {
        // Image picker button
        PhotosPicker(selection: $selectedImage, matching: .images) {
          Image(systemName: "photo")
            .foregroundColor(.blue)
            .frame(width: 32, height: 32)
        }
        .disabled(store.isModelLoading || store.isLoading)
        .onChange(of: selectedImage) { _, newItem in
          Task {
            if let newItem = newItem {
              await store.loadImageFrom(item: newItem)
            }
          }
        }

        TextField("Message", text: $store.input, axis: .vertical)
          .textFieldStyle(.roundedBorder)
          .disabled(store.isModelLoading)

        Button(action: { Task { await store.send() } }) {
          Image(systemName: "paperplane.fill")
            .foregroundColor(.white)
            .frame(width: 32, height: 32)
            .background(
              (store.input.trimmingCharacters(in: .whitespacesAndNewlines).isEmpty
                && store.attachedImage == nil)
                || store.isModelLoading || store.isLoading
                ? Color.gray : Color.blue
            )
            .clipShape(Circle())
        }
        .disabled(
          store.isModelLoading || store.isLoading
            || (store.input.trimmingCharacters(in: .whitespacesAndNewlines).isEmpty
              && store.attachedImage == nil)
        )
      }
      .padding(.horizontal)
    }
    .background(.ultraThinMaterial)
  }
}
