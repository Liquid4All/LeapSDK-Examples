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
- Constrained generation with structured JSON output using `@Generatable` macros
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
- Model: **LFM2-VL-450M** (Q8_0) - Vision-enabled multimodal model

**To run:**
```bash
cd LeapChatExample
make setup
make open  # Opens in Xcode
```

### 3. RecipeGenerator
Demonstrates structured JSON recipe generation.

**Features:**
- Automatic model downloading
- JSON recipe output
- Model: **LFM2-350M** (Q4_0) - Small, fast model for testing

**To run:**
```bash
cd RecipeGenerator
xcodegen generate
open RecipeGenerator.xcodeproj
```

### 4. LeapAudioDemo
Real-time audio conversation with speech input/output.

**Features:**
- Speech-to-speech conversation
- Audio recording and playback
- Interleaved text and audio responses
- Model: **LFM2.5-Audio-1.5B** (Q4_0) - Speech + text input/output

**To run:**
```bash
cd LeapAudioDemo
make setup
make open  # Opens in Xcode
```

## Getting Started

All examples use:
- **Swift Package Manager** for dependency management
- **XcodeGen** for project generation
- **LeapSDK v0.10.0-SNAPSHOT** directly from the official [leap-sdk](https://github.com/Liquid4All/leap-sdk/releases/tag/v0.10.0-SNAPSHOT) GitHub release

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

All examples use LeapSDK v0.10.0-SNAPSHOT with the KMP-based SDK and SKIE Swift interop. Import `LeapModelDownloader` for manifest-based model downloading:

```swift
import LeapSDK

// Load a model by name and quantization
let modelRunner = try await Leap.shared.load(
    model: "LFM2-350M",
    quantization: "Q4_0",
    progress: { progress, speed in
        print("Download progress: \(Int(progress * 100))%")
    }
)

// Create a conversation
let conversation = Conversation(modelRunner: modelRunner, history: [])

// Generate response with streaming
let stream = conversation.generateResponse(message:
    ChatMessage(role: .user, content: ChatMessageContent.text("Hello, AI!"))
)
for try await response in stream {
    switch onEnum(of: response) {
    case .chunk(let chunk):
        print(chunk.text)
    case .complete(let completion):
        print("Done: \(completion.fullMessage)")
    default:
        break
    }
}
```

## Constrained Generation (Structured Output)

LeapSDK v0.10.0-SNAPSHOT supports constrained generation using the `@Generatable` and `@Guide` macros from the `LeapSDKMacros` product:

```swift
import LeapSDK
import LeapSDKMacros

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

// Set up constrained generation options
let options = GenerationOptions()
options.jsonSchemaConstraint = ProductRecommendation.jsonSchema()

// generateResponse(message:generationOptions:) returns a raw Kotlin flow;
// bridge it to SkieSwiftFlow for async iteration
let rawFlow = conversation.generateResponse(message: userMessage, generationOptions: options)
let stream = rawFlow as! SkieSwiftFlow<any MessageResponse>

var jsonResponse = ""
for try await response in stream {
    switch onEnum(of: response) {
    case .chunk(let chunk):
        jsonResponse.append(chunk.text)
    default:
        break
    }
}

// Decode the JSON response
let result = try JSONDecoder().decode(ProductRecommendation.self, from: Data(jsonResponse.utf8))
```

### Package Configuration

Reference the SDK in each example's `project.yml`:

```yaml
packages:
  LeapSDK:
    url: https://github.com/Liquid4All/leap-sdk
    exactVersion: 0.10.0-SNAPSHOT

targets:
  YourApp:
    dependencies:
      - package: LeapSDK
        product: LeapModelDownloader   # Includes LeapSDK; adds manifest downloading
      # Add LeapSDKMacros if using @Generatable/@Guide macros:
      - package: LeapSDK
        product: LeapSDKMacros
    settings:
      base:
        # Required to link the inference engine dynamic libraries
        OTHER_LDFLAGS: "$(inherited) -L$(BUILT_PRODUCTS_DIR)/LeapSDK.framework/Frameworks -linference_engine -linference_engine_llamacpp_backend"
        LD_RUNPATH_SEARCH_PATHS: "$(inherited) @executable_path/Frameworks @executable_path/Frameworks/LeapSDK.framework/Frameworks"
```

## Model Requirements

**All examples use automatic model downloading** - no manual model setup required!

Models are automatically downloaded and cached on first run using manifest-based resolution:
- **LeapSloganExample**: LFM2.5-1.2B-Instruct (Q4_0) - ~700 MB
- **LeapChatExample**: LFM2-VL-450M (Q8_0) - ~500 MB
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

### Simulator build — `x86_64` architecture errors

The LeapSDK v0.10.0-SNAPSHOT xcframeworks only include `arm64` slices for simulator (no `x86_64`). When building for a generic iOS Simulator destination, add `EXCLUDED_ARCHS=x86_64`:

```bash
xcodebuild -scheme YourScheme \
  -destination 'generic/platform=iOS Simulator' \
  build EXCLUDED_ARCHS=x86_64
```

Or in Xcode: set **Excluded Architectures** → *Any iOS Simulator SDK* → `x86_64` in Build Settings.

### Macro trust prompt

When building LeapSloganExample, Xcode may prompt to trust the `LeapSDKConstrainedGenerationPlugin` macro. Click **Trust & Enable** to proceed. For command-line builds, run:

```bash
defaults write com.apple.dt.Xcode IDESkipMacroFingerprintValidation -bool YES
```

### Code signing for real devices
To run on a physical iOS device, you need to configure code signing:

1. Open the generated `.xcodeproj` in Xcode
2. Select the project in the navigator
3. Select the app target
4. Go to "Signing & Capabilities"
5. Select your development team
6. Xcode will automatically manage provisioning profiles

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

All examples reference the official **LeapSDK v0.10.0-SNAPSHOT** release directly from GitHub:
- **Repository**: https://github.com/Liquid4All/leap-sdk
- **Release**: https://github.com/Liquid4All/leap-sdk/releases/tag/v0.10.0-SNAPSHOT
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
       url: https://github.com/Liquid4All/leap-sdk
       exactVersion: 0.10.0-SNAPSHOT
   ```
