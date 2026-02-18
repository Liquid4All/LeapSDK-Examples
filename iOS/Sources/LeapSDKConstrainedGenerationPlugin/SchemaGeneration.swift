import Foundation
import SwiftSyntax
import SwiftSyntaxMacros

// Replace the whole file contents with the *lazy* generator
func generateJSONSchemaLazy(
  for declaration: some DeclGroupSyntax,
  typeName: String,
  description: String
) throws -> String {

  var propertySnippets: [String] = []
  var required: [String] = []

  for member in declaration.memberBlock.members {
    guard
      let varDecl = member.decl.as(VariableDeclSyntax.self),
      let binding = varDecl.bindings.first,
      binding.accessorBlock == nil,  // stored only
      !varDecl.modifiers.contains(where: { $0.name.text == "static" }),
      let ident = binding.pattern.as(IdentifierPatternSyntax.self)?.identifier,
      let type = binding.typeAnnotation?.type
    else { continue }

    let name = ident.text
    let (kind, isOpt) = mapSwiftType(type)

    // -------- format one property snippet ------------
    let snippet: String

    switch kind {

    case .primitive(let json):
      snippet = """
        "\(name)": { "type": "\(json)"\(guide(varDecl)) }
        """

    case .array(let elem):
      switch elem {
      case .primitive(let json):
        snippet = """
          "\(name)": {
            "type": "array",
            "items": { "type": "\(json)" }
            \(guide(varDecl, leadingComma: true))
          }
          """

      case .custom(let elemName):
        snippet = """
          "\(name)": {
            "type": "array",
            "items": \\(\(elemName).jsonSchema())
            \(guide(varDecl, leadingComma: true))
          }
          """

      case .array:  // nested-array edge-cases
        snippet = """
          "\(name)": { "type": "array" }
          """
      }

    case .custom(let other):
      snippet = """
        "\(name)": \\(\(other).jsonSchema())\(guide(varDecl, leadingComma: true))
        """
    }

    propertySnippets.append(snippet)
    if !isOpt { required.append("\"\(name)\"") }
  }

  // join everything
  let propertiesBlock = propertySnippets.joined(separator: ",\n")
  let requiredBlock = required.joined(separator: ", ")

  return #"""
    """
    {
      "type": "object",
      "title": "\#(typeName)",
      "description": "\#(description)",
      "properties": {
        \#(propertiesBlock)
      },
      "required": [\#(requiredBlock)]
    }
    """
    """#
}

// helper for optional @Guide text
private func guide(
  _ decl: VariableDeclSyntax,
  leadingComma: Bool = false
) -> String {
  guard let text = extractGuideDescription(from: decl) else { return "" }
  return "\(leadingComma ? ", " : ", ")\"description\": \"\(text)\""
}

// Keep the old function for backward compatibility
func generateJSONSchema(
  for declaration: some DeclGroupSyntax,
  typeName: String,
  description: String,
  in context: some MacroExpansionContext
) throws -> String {

  var properties: [String: [String: Any]] = [:]
  var required: [String] = []

  // Analyze stored properties
  let memberList = declaration.memberBlock.members

  for member in memberList {
    guard let varDecl = member.decl.as(VariableDeclSyntax.self),
      let binding = varDecl.bindings.first,
      let identifier = binding.pattern.as(IdentifierPatternSyntax.self)?.identifier,
      let type = binding.typeAnnotation?.type
    else {
      continue
    }

    // Skip computed properties (those with accessors)
    if binding.accessorBlock != nil {
      continue
    }

    // Skip static properties
    if varDecl.modifiers.contains(where: { $0.name.text == "static" }) {
      continue
    }

    let propertyName = identifier.text
    let (jsonType, isOptional) = mapSwiftTypeToJSONType(type)

    var propertySchema: [String: Any] = ["type": jsonType]

    // Handle array types - add items schema
    if jsonType == "array" {
      if let arrayType = extractArrayElementType(from: type) {
        let (elementType, _) = mapSwiftTypeToJSONType(arrayType)
        propertySchema["items"] = ["type": elementType]
      }
    }

    // Extract @Guide description if present
    if let guideDescription = extractGuideDescription(from: varDecl) {
      propertySchema["description"] = guideDescription
    }

    properties[propertyName] = propertySchema

    if !isOptional {
      required.append(propertyName)
    }
  }

  let schemaDict: [String: Any] = [
    "type": "object",
    "title": typeName,
    "description": description,
    "properties": properties,
    "required": required,
  ]

  // Convert to JSON string with pretty printing
  let jsonData = try JSONSerialization.data(
    withJSONObject: schemaDict, options: [.prettyPrinted, .sortedKeys])
  return String(data: jsonData, encoding: .utf8) ?? "{}"
}

// Helper to extract array element type
func extractArrayElementType(from type: TypeSyntax) -> TypeSyntax? {
  let typeDescription = type.description.trimmingCharacters(in: .whitespaces)

  // Handle [Type] syntax
  if typeDescription.hasPrefix("[") && typeDescription.hasSuffix("]") {
    let innerType = String(typeDescription.dropFirst().dropLast())
    return TypeSyntax(stringLiteral: innerType)
  }

  // Handle Array<Type> syntax
  if typeDescription.hasPrefix("Array<") && typeDescription.hasSuffix(">") {
    let innerType = String(typeDescription.dropFirst(6).dropLast())
    return TypeSyntax(stringLiteral: innerType)
  }

  return nil
}

// Helper to extract Guide description from variable declaration
func extractGuideDescription(from varDecl: VariableDeclSyntax) -> String? {
  for attribute in varDecl.attributes {
    guard let attrSyntax = attribute.as(AttributeSyntax.self),
      attrSyntax.attributeName.as(IdentifierTypeSyntax.self)?.name.text == "Guide"
    else {
      continue
    }

    if let argument = attrSyntax.arguments?.as(LabeledExprListSyntax.self),
      let expr = argument.first?.expression,
      let stringLiteral = expr.as(StringLiteralExprSyntax.self),
      let segment = stringLiteral.segments.first?.as(StringSegmentSyntax.self)
    {
      return segment.content.text
    }
  }

  return nil
}
