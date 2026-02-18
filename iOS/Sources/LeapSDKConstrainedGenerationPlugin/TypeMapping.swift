import SwiftSyntax

// ADD a new return-type that carries the name of a custom type
indirect enum JSONKind {
  case primitive(String)  // "string", "number", …
  case array(JSONKind)  // element kind
  case custom(String)  // DayPlan, Activity, …
}

func mapSwiftType(_ type: TypeSyntax) -> (JSONKind, isOptional: Bool) {
  let raw = type.description.trimmingCharacters(in: .whitespaces)

  // optionals ────────────────────────────────────────────────
  if raw.hasSuffix("?") {
    let inner = String(raw.dropLast())
    let (kind, _) = mapSwiftType(TypeSyntax(stringLiteral: inner))
    return (kind, true)
  }
  if raw.hasPrefix("Optional<"), raw.hasSuffix(">") {
    let inner = String(raw.dropFirst("Optional<".count).dropLast())
    let (kind, _) = mapSwiftType(TypeSyntax(stringLiteral: inner))
    return (kind, true)
  }

  // arrays ───────────────────────────────────────────────────
  if raw.hasPrefix("["), raw.hasSuffix("]") {
    let inner = String(raw.dropFirst().dropLast())
    let (elem, _) = mapSwiftType(TypeSyntax(stringLiteral: inner))
    return (.array(elem), false)
  }
  if raw.hasPrefix("Array<"), raw.hasSuffix(">") {
    let inner = String(raw.dropFirst("Array<".count).dropLast())
    let (elem, _) = mapSwiftType(TypeSyntax(stringLiteral: inner))
    return (.array(elem), false)
  }

  // dictionaries (still treat as plain object literals)
  if raw.hasPrefix("[") && raw.contains(":"), raw.hasSuffix("]") {
    return (.primitive("object"), false)
  }
  if raw.hasPrefix("Dictionary<"), raw.hasSuffix(">") {
    return (.primitive("object"), false)
  }

  // primitives ───────────────────────────────────────────────
  switch raw {
  case "String": return (.primitive("string"), false)
  case "Int", "Int8", "Int16",
    "Int32", "Int64",
    "UInt", "UInt8", "UInt16",
    "UInt32", "UInt64":
    return (.primitive("integer"), false)
  case "Float", "Double", "CGFloat": return (.primitive("number"), false)
  case "Bool": return (.primitive("boolean"), false)
  case "Date", "Data", "URL", "UUID": return (.primitive("string"), false)

  default:
    // *** NEW: everything else is assumed to be a nested @Generatable ***
    return (.custom(raw), false)
  }
}

// Keep the old function for backward compatibility if needed
func mapSwiftTypeToJSONType(_ type: TypeSyntax) -> (String, Bool) {
  let typeDescription = type.description.trimmingCharacters(in: .whitespaces)

  // Handle optionals
  if typeDescription.hasSuffix("?") {
    let baseType = String(typeDescription.dropLast())
    let (jsonType, _) = mapSwiftTypeToJSONType(TypeSyntax(stringLiteral: baseType))
    return (jsonType, true)
  }

  // Handle Optional<T>
  if typeDescription.hasPrefix("Optional<") && typeDescription.hasSuffix(">") {
    let innerType = String(typeDescription.dropFirst(9).dropLast())
    let (jsonType, _) = mapSwiftTypeToJSONType(TypeSyntax(stringLiteral: innerType))
    return (jsonType, true)
  }

  // Handle arrays
  if typeDescription.hasPrefix("[") && typeDescription.hasSuffix("]") {
    return ("array", false)
  }

  // Handle Array<T>
  if typeDescription.hasPrefix("Array<") && typeDescription.hasSuffix(">") {
    return ("array", false)
  }

  // Handle dictionaries
  if typeDescription.hasPrefix("[") && typeDescription.contains(":")
    && typeDescription.hasSuffix("]")
  {
    return ("object", false)
  }

  // Handle Dictionary<K, V>
  if typeDescription.hasPrefix("Dictionary<") && typeDescription.hasSuffix(">") {
    return ("object", false)
  }

  // Basic types
  switch typeDescription {
  case "String":
    return ("string", false)
  case "Int", "Int8", "Int16", "Int32", "Int64", "UInt", "UInt8", "UInt16", "UInt32", "UInt64":
    return ("integer", false)
  case "Float", "Double", "CGFloat":
    return ("number", false)
  case "Bool":
    return ("boolean", false)
  case "Date":
    return ("string", false)  // JSON doesn't have a date type, typically serialized as string
  case "Data":
    return ("string", false)  // Base64 encoded string
  case "URL":
    return ("string", false)  // URL as string
  case "UUID":
    return ("string", false)  // UUID as string
  default:
    // For custom types, assume they're objects
    return ("object", false)
  }
}
