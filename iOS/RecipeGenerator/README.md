# RecipeGenerator

A SwiftUI demo showcasing **constrained JSON generation** with LeapSDK. The `Recipe` struct is annotated with `@Generatable` / `@Guide`, and the generated JSON schema is passed to the SDK so the model emits output that is guaranteed to decode into the struct — no prompt-engineered JSON, no substring extraction, no fallback.

## Features

- Structured-output generation via `LeapSDKMacros`' `@Generatable` and `@Guide` macros
- Schema-constrained sampling (`GenerationOptions.jsonSchemaConstraint`) so the model can only emit JSON matching the `Recipe` shape
- Direct `JSONDecoder` round-trip — no `{` ... `}` substring extraction or string mangling
- On-device text generation via `LeapModelDownloader`
- Automatic model fetch + on-disk caching on first launch
- Streamed token consumption with `onEnum(of:)`
- Live download/generation progress in the UI

## Model

- **LFM2.5-350M** (Q4_0) — small and fast, chosen for quick cold-start during testing.
- Downloaded automatically on first launch via `Leap.shared.load(model:quantization:options:progress:)`. The `progress` callback drives the on-screen `ProgressView`.
- Cached under the app's Caches directory at `leap-cache/` (`FileManager.default.urls(for: .cachesDirectory, ...)`). Subsequent launches load from disk and run offline.

## Prerequisites

- Xcode 15+
- iOS 18.0+ deployment target (set in `project.yml`)
- [XcodeGen](https://github.com/yonaskolb/XcodeGen): `brew install xcodegen`

## Running

```bash
cd iOS/RecipeGenerator
make setup    # xcodegen generate
make open     # opens RecipeGenerator.xcodeproj in Xcode
# or: make build / make run
```

Then select an iOS 18+ simulator or device and run. On a generic iOS Simulator destination, exclude `x86_64` (the v0.10.8 xcframeworks ship arm64-only simulator slices) — see `iOS/README.md` for the full incantation.

When Xcode first builds the project it may prompt to trust the `LeapSDKConstrainedGenerationPlugin` macro — click **Trust & Enable**. For headless builds, set `defaults write com.apple.dt.Xcode IDESkipMacroFingerprintValidation -bool YES`.

## Project Structure

```
RecipeGenerator/
├── project.yml                          # XcodeGen configuration (SDK pin, macros dep, linker flags, codesign script)
└── RecipeGenerator/
    ├── RecipeGeneratorApp.swift         # @main App entry
    ├── ContentView.swift                # Button, progress bar, decoded-recipe view
    ├── GeneratorViewModel.swift         # LeapSDK integration: load model, set jsonSchemaConstraint, stream, decode
    ├── Models/
    │   └── Recipe.swift                 # @Generatable Codable struct — drives both decoding and the schema
    ├── Info.plist
    └── Assets.xcassets/
```

`project.yml` pins LeapSDK at `revision: v0.10.8` and depends on both `LeapModelDownloader` (manifest-based downloading + re-exports of the SDK runtime types) and `LeapSDKMacros` (the `@Generatable` / `@Guide` macro plugin). The post-build script re-signs the nested inference-engine dylibs inside `LeapModelDownloader.framework/Frameworks`.

## Key SDK Patterns

### Annotate the data model with `@Generatable` and `@Guide`

```swift
import LeapSDKMacros

@Generatable("A cooking recipe")
struct Recipe: Codable {
  @Guide("Name of the dish")
  let name: String

  @Guide("Estimated total cooking time in minutes")
  let cookingTime: Int

  @Guide("True if the recipe contains no meat, poultry, or fish")
  let isVegetarian: Bool

  @Guide("Ingredients with quantities (e.g. \"1 lb shrimp\", \"4 cloves garlic\")")
  let ingredients: [String]

  @Guide("Ordered preparation steps")
  let directions: [String]
}
```

The `@Generatable` macro synthesizes a `static func jsonSchema()` on the type. `@Guide` descriptions are embedded in the schema and seen by the model, so write them like prompts — they describe what each field is for.

### Load the model with cached manifest downloading

```swift
let cachePath = FileManager.default
  .urls(for: .cachesDirectory, in: .userDomainMask).first!
  .appendingPathComponent("leap-cache").path
try? FileManager.default.createDirectory(atPath: cachePath, withIntermediateDirectories: true)

modelRunner = try await Leap.shared.load(
  model: "LFM2.5-350M",
  quantization: "Q4_0",
  options: LiquidInferenceEngineManifestOptions(
    cacheOptions: .enabled(path: cachePath)
  ),
  progress: { progress, _ in
    // progress is 0.0 ... 1.0; speed is bytes/sec
  }
)
```

### Constrain generation to the schema and decode the streamed JSON

```swift
let systemMessage = ChatMessage(
  role: .system,
  textContent: "You are a helpful cooking assistant."
)
let conversation = Conversation(modelRunner: modelRunner, history: [systemMessage])

let userMessage = ChatMessage(
  role: .user,
  textContent: "Generate a recipe for a dinner dish with shrimps."
)

let options = GenerationOptions()
options.jsonSchemaConstraint = Recipe.jsonSchema()

// generateResponse(message:generationOptions:) returns a SkieSwiftFlow directly,
// suitable for async iteration.
let stream = conversation.generateResponse(message: userMessage, generationOptions: options)

var jsonResponse = ""
var streamError: String?
for await event in stream {
  switch onEnum(of: event) {
  case .chunk(let chunk):
    jsonResponse.append(chunk.text)
  case .complete, .audioSample, .reasoningChunk, .functionCalls:
    break
  case .error(let err):
    // SKIE bridges the flow as non-throwing (Failure = Never); surface in-band errors
    // through the .error case rather than relying on a thrown close.
    streamError = err.message
  }
}
if let streamError {
  throw NSError(
    domain: "RecipeGenerator",
    code: 0,
    userInfo: [NSLocalizedDescriptionKey: streamError]
  )
}

let recipe = try JSONDecoder().decode(Recipe.self, from: Data(jsonResponse.utf8))
```

Because `jsonSchemaConstraint` constrains sampling to tokens that produce schema-valid JSON, the system prompt no longer needs "Generate recipes in JSON format..." instructions, and decoding can be unconditional — no substring extraction, no fallback recipe.

## Notes / Troubleshooting

- The user prompt is hard-coded ("a dinner dish with shrimps") in `GeneratorViewModel.generateRecipe()`. Edit it — or wire it up to UI — to vary the output.
- iOS Simulator builds may need `EXCLUDED_ARCHS=x86_64` — see the root `iOS/README.md` troubleshooting section.
- If you change `Recipe`'s fields, no separate schema update is needed; `jsonSchema()` is regenerated by the macro on every build.
