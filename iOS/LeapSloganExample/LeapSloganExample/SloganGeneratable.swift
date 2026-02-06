import Foundation
import LeapSDK

// SloganResponse structure for constrained generation using Swift macros
@Generatable("Generate creative marketing slogans for a business")
struct SloganResponse: Codable {
  @Guide("The name or type of the business")
  let businessName: String

  @Guide("A short, catchy slogan for the business (max 10 words)")
  let shortSlogan: String

  @Guide("A longer, descriptive tagline (max 20 words)")
  let longTagline: String

  @Guide("Key selling points or benefits (3-5 items)")
  let keyBenefits: [String]

  @Guide("Target audience for this slogan")
  let targetAudience: String

  @Guide("Emotional tone of the slogan (e.g., professional, playful, inspiring)")
  let tone: String
}
