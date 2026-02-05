# LeapSDK iOS Examples

This directory contains iOS example applications demonstrating how to use the LeapSDK for on-device AI inference.

## Prerequisites

- Xcode 15.0+
- iOS 17.0+ deployment target
- Swift 5.9+
- XcodeGen: `brew install xcodegen`

## Examples

### 1. LeapSloganExample
A simple SwiftUI app that generates creative slogans using the LeapSDK.

**Features:**
- Automatic model downloading with manifest resolution
- Constrained generation with structured JSON output
- SwiftUI interface with download progress tracking
- Model: **LFM2.5-1.2B-Instruct** (Q4_0) - Optimized for instruction following

**To run:**
```bash
cd LeapSloganExample
make setup
make open  # Opens in Xcode
```

### 2. LeapChatExample
A multimodal chat application with vision support.

**Features:**
- Real-time chat interface with vision support
- Image attachment and analysis
- Message history management
- Token streaming with typing indicators
- Message bubbles UI
- Model: **LFM2.5-VL-1.6B** (Q4_0) - Vision-enabled multimodal model

**To run:**
```bash
cd LeapChatExample
make setup
make open  # Opens in Xcode
```

### 3. RecipeGenerator
Demonstrates constrained generation for structured JSON output.

**Features:**
- Automatic model downloading
- Constrained generation with JSON schema
- Structured recipe output
- Model: **LFM2-350M** (Q4_0) - Small, fast model for testing

**To run:**
```bash
cd RecipeGenerator
xcodegen generate
open RecipeGenerator.xcodeproj
```

## Getting Started

All examples use:
- **Swift Package Manager** for dependency management
- **XcodeGen** for project generation
- **LeapSDK v0.9.2** directly from the official [leap-ios](https://github.com/Liquid4All/leap-ios/releases/tag/v0.9.2) GitHub release

### Quick Start

1. Clone the repository
2. Navigate to any example directory
3. Run `make setup` to generate the Xcode project
4. Run `make open` to open in Xcode
5. Build and run on simulator or device

### Development Workflow

Each example includes a Makefile with common tasks:

```bash
make setup     # Generate Xcode project
make build     # Build the project
make run       # Build and run on simulator
make clean     # Clean generated files
make open      # Open project in Xcode
```

## Project Structure

Each example follows this structure:
```
ExampleApp/
├── project.yml          # XcodeGen configuration
├── Makefile            # Build automation
├── README.md           # Example-specific documentation
├── ExampleApp/         # Source code
│   ├── *.swift        # Swift source files
│   ├── Assets.xcassets # App assets
│   └── Resources/     # Model bundles and resources
└── Resources/          # Shared resources (for LeapChatExample)
    └── Info.plist     # App configuration
```

## Using LeapSDK

All examples use LeapSDK v0.9.2 with support for automatic model downloading and manifest-based configuration:

```swift
import LeapSDK

// Load a model by name and quantization (easiest method)
let modelRunner = try await Leap.load(
    model: "LFM2-350M",
    quantization: "Q4_0"
) { progress, speed in
    print("Download progress: \(progress * 100)%")
}

// Or load from a manifest URL
let modelRunner = try await Leap.load(
    manifestURL: manifestURL
) { progress, speed in
    print("Download: \(progress), Speed: \(speed) bytes/sec")
}

// Or load from local bundle with explicit options
let modelRunner = try Leap.load(
    options: LiquidInferenceEngineOptions(bundlePath: "path/to/model")
)

// Create a conversation
let conversation = modelRunner.createConversation(
    systemPrompt: "You are a helpful assistant."
)

// Generate response with streaming
for try await response in conversation.generateResponse(
    userTextMessage: "Hello, AI!"
) {
    if let chunk = response as? MessageResponseChunk {
        print(chunk.text)
    }
}
```

## Constrained Generation (Structured Output)

LeapSDK v0.9.2 supports constrained generation to ensure JSON-formatted responses. There are two ways to define schemas:

### Option 1: Manual `GeneratableType` Conformance (Used in Examples)

This approach works with the binary v0.9.2 release:

```swift
import LeapSDK

struct ProductRecommendation: Codable, GeneratableType {
  let name: String
  let category: String
  let price: Double
  let reason: String
  let features: [String]

  static var typeDescription: String {
    "Simple product recommendation"
  }

  static func jsonSchema() throws -> String {
    """
    {
      "type": "object",
      "description": "Simple product recommendation",
      "properties": {
        "name": { "type": "string", "description": "Product name" },
        "category": { "type": "string", "description": "Category" },
        "price": { "type": "number", "description": "Price in USD" },
        "reason": { "type": "string", "description": "Short recommendation reason" },
        "features": {
          "type": "array",
          "description": "List of key features",
          "items": { "type": "string" }
        }
      },
      "required": ["name", "category", "price", "reason", "features"]
    }
    """
  }
}

// Usage
var options = GenerationOptions()
try options.setResponseFormat(type: ProductRecommendation.self)
let stream = conversation.generateResponse(message: userMessage, generationOptions: options)
```

### Option 2: Using `@Generatable` and `@Guide` Macros (Source Builds Only)

For a cleaner, more readable syntax when building from source:

```swift
import LeapSDK

@Generatable("Simple product recommendation")
struct ProductRecommendation: Codable {
  @Guide("Product name")
  let name: String

  @Guide("Category")
  let category: String

  @Guide("Price in USD")
  let price: Double

  @Guide("Short recommendation reason")
  let reason: String

  @Guide("List of key features")
  let features: [String]
}

// Usage is identical
var options = GenerationOptions()
try options.setResponseFormat(type: ProductRecommendation.self)
```

**Note:** The `@Generatable` and `@Guide` macros are available when building LeapSDK from source ([leap-ios-sdk](https://github.com/Liquid4All/leap-ios-sdk)), but are not included in the binary v0.9.2 XCFramework release. Swift macro plugins must be compiled as executables and cannot be distributed in binary frameworks. These examples use manual `GeneratableType` conformance for compatibility with the published binary release.

### 4. LeapAudioDemo
Real-time audio conversation with speech input/output.

**Features:**
- Speech-to-speech conversation
- Audio recording and playback
- Interleaved text and audio responses
- Model: **LFM2.5-Audio-1.5B** (Q4_0) - Speech + text input/output

## Model Requirements

**All examples use automatic model downloading** - no manual model setup required!

Models are automatically downloaded and cached on first run using manifest-based resolution:
- **LeapSloganExample**: LFM2.5-1.2B-Instruct (Q4_0) - ~700 MB
- **LeapChatExample**: LFM2.5-VL-1.6B (Q4_0) - ~1.0 GB
- **RecipeGenerator**: LFM2-350M (Q4_0) - ~209 MB
- **LeapAudioDemo**: LFM2.5-Audio-1.5B (Q4_0) - ~900 MB

Models are downloaded from the [Liquid AI model registry](https://leap.liquid.ai/api/models) and cached in the app's Documents directory for reuse.

## Troubleshooting

### XcodeGen not found
```bash
brew install xcodegen
```

### Build errors
```bash
make clean
make setup
```

### Model loading errors
Ensure model bundles are correctly placed in the `Resources/` directory and properly referenced in the app configuration.

### Code signing for real devices
To run on a physical iOS device, you need to configure code signing:

1. Open the generated `.xcodeproj` in Xcode
2. Select the project in the navigator
3. Select the app target
4. Go to "Signing & Capabilities"
5. Select your development team
6. Xcode will automatically manage provisioning profiles

**Note**: The simulator has GPU memory limitations and cannot run inference models. Always test model inference on real devices.

Alternatively, add to your local `project.yml`:
```yaml
targets:
  YourTarget:
    settings:
      base:
        DEVELOPMENT_TEAM: YOUR_TEAM_ID
        CODE_SIGN_STYLE: Automatic
```
Then run `xcodegen generate` to regenerate the project.

## LeapSDK Package

All examples reference the official **LeapSDK v0.9.2** release directly from GitHub:
- **Repository**: https://github.com/Liquid4All/leap-ios
- **Release**: https://github.com/Liquid4All/leap-ios/releases/tag/v0.9.2
- **Package Configuration**: Each example's `project.yml` specifies the GitHub URL and version

The SDK is automatically downloaded by Swift Package Manager when you run `xcodegen generate`.

## Contributing

When adding new examples:
1. Follow the existing project structure
2. Use XcodeGen for project configuration
3. Include appropriate Makefile targets
4. Add documentation in README.md
5. Reference LeapSDK from GitHub in `project.yml`:
   ```yaml
   packages:
     LeapSDK:
       url: https://github.com/Liquid4All/leap-ios
       from: 0.9.2
   ```
