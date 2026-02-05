import Foundation
import LeapSDK

struct Recipe: Codable {
  var name: String
  var cookingTime: Int
  var isVegetarian: Bool
  var ingredients: [String]
  var directions: [String]
}
