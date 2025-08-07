import SwiftUI

struct ContentView: View {
  @StateObject var viewModel = GeneratorViewModel()
  var body: some View {
    VStack {
      if viewModel.isModelLoading {
        Text("Loading model")
      } else if viewModel.isGenerating {
        Text("Generating recipe...")
      } else {
        Button("Generate a new recipe") {
          Task {
            await viewModel.setupModel()
            do {
              try await viewModel.generateRecipe()
            } catch {
              print("Error: \(error)")
            }
          }
        }
      }
      if let recipe = viewModel.recipe {
        Text("Title: \(recipe.name)")
        Text("Cooking time: \(recipe.cookingTime) minutes")
        Text("Directions: \(recipe.directions.joined(separator: "\n"))")

      }
    }
    .padding()
  }
}

#Preview {
  ContentView()
}
