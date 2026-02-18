import Foundation
import LeapSDKMacros

@Generatable("Generate a recipe for a dinner dish")
struct Recipe: Codable {
  @Guide("Name of the dish")
  var name: String

  @Guide("Cooking time in minutes")
  var cookingTime: Int

  @Guide("Whether the dish is vegetarian")
  var isVegetarian: Bool

  @Guide("List of ingredients needed")
  var ingredients: [String]

  @Guide("Step-by-step cooking directions")
  var directions: [String]
}
