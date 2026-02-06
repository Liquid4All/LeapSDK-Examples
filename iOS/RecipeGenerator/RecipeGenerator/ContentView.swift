import SwiftUI

struct ContentView: View {
  @StateObject var viewModel = GeneratorViewModel()
  @State private var errorMessage: String?

  var body: some View {
    VStack(spacing: 20) {
      // Status message
      Text(viewModel.statusMessage)
        .font(.headline)
        .multilineTextAlignment(.center)
        .padding()

      if viewModel.isModelLoading {
        VStack(spacing: 12) {
          ProgressView(value: viewModel.downloadProgress) {
            Text("Loading model...")
          }
          .progressViewStyle(.linear)
          .frame(maxWidth: 200)

          if viewModel.downloadProgress > 0 && viewModel.downloadProgress < 1.0 {
            Text("\(Int(viewModel.downloadProgress * 100))%")
              .font(.caption)
              .foregroundColor(.secondary)
          }
        }
      } else if viewModel.isGenerating {
        ProgressView("Generating recipe...")
      } else {
        Button("Generate a new recipe") {
          Task {
            await viewModel.setupModel()
            do {
              try await viewModel.generateRecipe()
              errorMessage = nil
            } catch {
              errorMessage = "Error: \(error.localizedDescription)"
              print("Error: \(error)")
            }
          }
        }
        .buttonStyle(.borderedProminent)
      }

      if let errorMessage = errorMessage {
        Text(errorMessage)
          .foregroundColor(.red)
          .font(.caption)
      }

      if let recipe = viewModel.recipe {
        ScrollView {
          VStack(alignment: .leading, spacing: 12) {
            Text(recipe.name)
              .font(.title)
              .bold()

            Text("⏱️ \(recipe.cookingTime) minutes")
              .font(.subheadline)

            if recipe.isVegetarian {
              Text("🌱 Vegetarian")
                .font(.subheadline)
            }

            Divider()

            Text("Ingredients:")
              .font(.headline)
            ForEach(recipe.ingredients, id: \.self) { ingredient in
              Text("• \(ingredient)")
            }

            Divider()

            Text("Directions:")
              .font(.headline)
            ForEach(Array(recipe.directions.enumerated()), id: \.offset) { index, step in
              Text("\(index + 1). \(step)")
                .padding(.bottom, 4)
            }
          }
          .frame(maxWidth: .infinity, alignment: .leading)
        }
      }
    }
    .padding()
  }
}

#Preview {
  ContentView()
}
