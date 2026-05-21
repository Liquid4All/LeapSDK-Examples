import Foundation
import LeapSDKMacros

@Generatable("A cooking recipe")
struct Recipe: Codable {
  @Guide("Name of the dish")
  let name: String

  @Guide("Estimated total cooking time in minutes")
  let cookingTime: Int

  @Guide("True if the recipe contains no meat, poultry, or fish")
  let isVegetarian: Bool

  @Guide("Ingredients with quantities (e.g. \"1 lb shrimp\", \"4 cloves garlic\")")
  let ingredients: [String]

  @Guide("Ordered preparation steps")
  let directions: [String]
}
