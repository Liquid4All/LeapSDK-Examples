import Foundation
import LeapModelDownloader

struct Recipe: Codable {
  var name: String
  var cookingTime: Int
  var isVegetarian: Bool
  var ingredients: [String]
  var directions: [String]
}
