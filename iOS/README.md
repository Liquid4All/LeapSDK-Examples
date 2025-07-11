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
- Basic text generation
- SwiftUI interface
- Demonstrates LeapSDK initialization and conversation management

**To run:**
```bash
cd LeapSloganExample
make setup
make open  # Opens in Xcode
```

### 2. LeapChatExample
A more comprehensive chat application showcasing advanced LeapSDK features.

**Features:**
- Real-time chat interface
- Message history management
- Token streaming
- Typing indicators
- Message bubbles UI

**To run:**
```bash
cd LeapChatExample
make setup
make open  # Opens in Xcode
```

## Getting Started

All examples use:
- **Swift Package Manager** for dependency management
- **XcodeGen** for project generation
- **LeapSDK** from the public GitHub repository

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

All examples use the public LeapSDK Swift package:

```swift
import LeapSDK

// Load a model
let modelRunner = try await Leap.load(
    modelPath: "path/to/model.bundle"
)

// Create a conversation
let conversation = Conversation()
conversation.addMessage(ChatMessage(
    role: .user,
    content: .text("Hello, AI!")
))

// Generate response
for try await chunk in modelRunner.generateResponse(for: conversation) {
    // Handle streaming response
    print(chunk.delta)
}
```

## Model Requirements

Examples require AI model bundles in the `Resources/` directory. These are not included in the repository due to size constraints.

For testing purposes, you can:
1. Use your own compatible model bundles
2. Contact Liquid AI for access to sample models
3. Refer to the LeapSDK documentation for model format requirements

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

## Contributing

When adding new examples:
1. Follow the existing project structure
2. Use XcodeGen for project configuration
3. Include appropriate Makefile targets
4. Add documentation in README.md
5. Ensure examples work with the public LeapSDK package