import SwiftCompilerPlugin
import SwiftSyntaxMacros

@main
struct LeapConstraintPlugin: CompilerPlugin {
  let providingMacros: [Macro.Type] = [
    GeneratableMacro.self,
    GuideMacro.self,
  ]
}
