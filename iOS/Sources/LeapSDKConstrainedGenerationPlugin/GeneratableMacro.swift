import SwiftCompilerPlugin
import SwiftSyntax
import SwiftSyntaxBuilder
import SwiftSyntaxMacros

enum MacroError: Error, CustomStringConvertible {
  case notApplicableToType
  case missingDescription
  case invalidSyntax(String)

  var description: String {
    switch self {
    case .notApplicableToType:
      return "@Generatable can only be applied to structs or classes"
    case .missingDescription:
      return "@Generatable requires a description argument"
    case .invalidSyntax(let message):
      return "Invalid syntax: \(message)"
    }
  }
}

public struct GeneratableMacro: ExtensionMacro {
  public static func expansion(
    of node: AttributeSyntax,
    attachedTo declaration: some DeclGroupSyntax,
    providingExtensionsOf type: some TypeSyntaxProtocol,
    conformingTo protocols: [TypeSyntax],
    in context: some MacroExpansionContext
  ) throws -> [ExtensionDeclSyntax] {

    // Extract description from macro argument
    let description = try extractDescription(from: node)

    // Ensure applied to struct or class
    guard
      let _ = declaration.as(StructDeclSyntax.self)
        ?? declaration.as(ClassDeclSyntax.self)?.asProtocol(NamedDeclSyntax.self),
      let namedDecl = declaration.asProtocol(NamedDeclSyntax.self)
    else {
      throw MacroError.notApplicableToType
    }

    let typeName = namedDecl.name.text

    // Generate JSON schema by analyzing the type's members
    let schema = try generateJSONSchemaLazy(
      for: declaration,
      typeName: typeName,
      description: description
    )

    // Generate extension conforming to GeneratableType
    let extensionDecl = try ExtensionDeclSyntax(
      "extension \(type.trimmed): LeapSDKMacros.GeneratableType"
    ) {
      DeclSyntax("static var typeDescription: String { \(literal: description) }")
      DeclSyntax(
        """
        static func jsonSchema() -> String {
          \(raw: schema)
        }
        """)
    }

    return [extensionDecl]
  }
}

// Helper to extract description from macro argument
func extractDescription(from node: AttributeSyntax) throws -> String {
  guard let argument = node.arguments?.as(LabeledExprListSyntax.self),
    let expr = argument.first?.expression,
    let stringLiteral = expr.as(StringLiteralExprSyntax.self),
    let segment = stringLiteral.segments.first?.as(StringSegmentSyntax.self)
  else {
    throw MacroError.missingDescription
  }

  return segment.content.text
}
