import SwiftCompilerPlugin
import SwiftSyntax
import SwiftSyntaxMacros

public struct GuideMacro: PeerMacro {
  public static func expansion(
    of node: AttributeSyntax,
    providingPeersOf declaration: some DeclSyntaxProtocol,
    in context: some MacroExpansionContext
  ) throws -> [DeclSyntax] {
    // @Guide is a marker macro - it doesn't generate code itself
    // Its information is consumed by @Generatable during schema generation
    return []
  }
}
