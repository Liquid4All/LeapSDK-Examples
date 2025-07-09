# LeapSloganExample

A simple SwiftUI app demonstrating basic LeapSDK integration for text generation.

## Features

- Generate creative slogans using AI
- Simple, clean SwiftUI interface
- Demonstrates LeapSDK initialization
- Example of conversation management
- Local on-device AI inference

## Requirements

- iOS 17.0+
- Xcode 15.0+
- XcodeGen: `brew install xcodegen`

## Getting Started

1. **Setup the project:**
   ```bash
   make setup
   ```

2. **Open in Xcode:**
   ```bash
   make open
   ```

3. **Build and run:**
   - Select a simulator or device
   - Press Cmd+R to build and run

## Usage

1. Launch the app
2. Enter a topic or product name
3. Tap "Generate Slogan"
4. Watch as the AI generates a creative slogan

## Project Structure

```
LeapSloganExample/
├── project.yml                 # XcodeGen configuration
├── Makefile                   # Build automation
├── README.md                  # This file
└── LeapSloganExample/         # Source code
    ├── LeapSloganExampleApp.swift  # App entry point
    ├── ContentView.swift           # Main UI
    ├── SloganStore.swift          # Business logic
    ├── Assets.xcassets           # App assets
    ├── Preview Content/          # SwiftUI previews
    └── Resources/               # Model bundles
        └── qwen3-1_7b_8da4w.bundle
```

## Code Overview

### Key Components

**SloganStore**: Manages LeapSDK interaction
```swift
@MainActor
class SloganStore: ObservableObject {
    @Published var slogan = ""
    @Published var isGenerating = false
    
    func generateSlogan(for topic: String) async {
        // LeapSDK integration
    }
}
```

**ContentView**: SwiftUI interface
```swift
struct ContentView: View {
    @StateObject private var store = SloganStore()
    @State private var topic = ""
    
    var body: some View {
        // UI implementation
    }
}
```

## How it Works

The app uses the LeapSDK to:

1. Load a local AI model (qwen3-1_7b_8da4w)
2. Create a conversation with a marketing expert system prompt
3. Generate creative slogans based on the business description
4. Stream the response in real-time

## Customization

### Adding New Features
- Modify `SloganStore.swift` for business logic changes
- Update `ContentView.swift` for UI modifications
- Add model bundles to `Resources/` directory

### Model Configuration
Update the model path in `SloganStore.swift`:
```swift
let modelRunner = try await Leap.load(
    modelPath: Bundle.main.bundlePath + "/qwen3-1_7b_8da4w.bundle"
)
```

## Troubleshooting

### Build Issues
```bash
make clean
make setup
```

### XcodeGen not found
```bash
brew install xcodegen
```

### Model Loading Errors
Ensure the model bundle exists in `LeapSloganExample/Resources/`

## Next Steps

- Try the more advanced [LeapChatExample](../LeapChatExample/)
- Explore the [LeapSDK documentation](https://leap.liquid.ai/docs/ios-quick-start-guide)
- Experiment with different model bundles
