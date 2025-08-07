import Foundation
import LeapSDK

@Generatable("A recipe for cooking great dishes")
struct Recipe: Codable {
  @Guide("Name of the dish")
  var name: String
  @Guide("Time to cook the dish in minutes")
  var cookingTime: Int
  @Guide("Whether the dish is vegetarian or not")
  var isVegetarian: Bool
  @Guide("Ingredients needed")
  var ingredients: [String]
  @Guide("Directions of cooking")
  var directions: [String]
}
