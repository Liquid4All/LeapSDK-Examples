/// Marks a type as generatable, allowing it to be used for constrained generation.
/// The type must conform to Codable.
@attached(
  extension, conformances: GeneratableType, names: named(typeDescription), named(jsonSchema))
public macro Generatable(_ description: String) =
  #externalMacro(module: "LeapSDKConstrainedGenerationPlugin", type: "GeneratableMacro")

/// Adds a description to a property for inclusion in the generated JSON schema.
@attached(peer)
public macro Guide(_ description: String) =
  #externalMacro(module: "LeapSDKConstrainedGenerationPlugin", type: "GuideMacro")
