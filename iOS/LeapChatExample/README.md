# LeapChatExample

A comprehensive chat application demonstrating advanced LeapSDK features including real-time streaming, conversation management, and modern UI components.

## Features

- **Real-time Chat Interface**: Modern chat UI with message bubbles
- **Token Streaming**: Live response generation with typing indicators  
- **Message History**: Persistent conversation management
- **Rich UI Components**: Custom message rows, input views, and animations
- **Error Handling**: Robust error management and user feedback
- **Function Calling**: Tool support with compute_sum example function

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
2. Type a message in the input field
3. Tap send or press return
4. Watch the AI respond in real-time with streaming text
5. Continue the conversation with context awareness

## Project Structure

```
LeapChatExample/
├── project.yml                # XcodeGen configuration
├── Makefile                  # Build automation
├── README.md                 # This file
├── Resources/
│   └── Info.plist           # App configuration
└── LeapChatExample/         # Source code
    ├── LeapChatExampleApp.swift   # App entry point
    ├── ContentView.swift          # Main chat interface
    ├── ChatStore.swift           # Core business logic
    ├── MessagesListView.swift    # Message list component
    ├── MessageRow.swift          # Individual message display
    ├── MessageBubble.swift       # Message bubble data model
    ├── ToolMessageRow.swift      # Tool message display component
    ├── ChatInputView.swift       # Input field component
    ├── TypingIndicator.swift     # Typing animation
    ├── Assets.xcassets          # App assets
    └── Resources/               # Model bundles
        └── LFM2-1.2B-8da4w_output_8da8w-seq_4096.bundle
```

## Code Overview

### Key Components

**ChatStore**: Core business logic and state management
```swift
@MainActor
class ChatStore: ObservableObject {
    @Published var messages: [ChatMessage] = []
    @Published var isGenerating = false
    
    func sendMessage(_ text: String) async {
        // Handle message sending and AI response
    }
}
```

**ContentView**: Main chat interface
```swift
struct ContentView: View {
    @StateObject private var chatStore = ChatStore()
    
    var body: some View {
        VStack {
            MessagesListView(messages: chatStore.messages)
            ChatInputView(onSend: chatStore.sendMessage)
        }
    }
}
```

**MessageBubble**: Custom message UI component
```swift
struct MessageBubble: View {
    let message: ChatMessage
    
    var body: some View {
        // Custom bubble design with different styles for user/assistant
    }
}
```

## Advanced Features

### Real-time Streaming
The app demonstrates token-by-token streaming:
```swift
for try await chunk in modelRunner.generateResponse(for: conversation) {
    if let delta = chunk.delta {
        // Update UI with each token
        lastMessage.content.appendText(delta)
    }
}
```

### Function Calling
The app includes tool support with a `compute_sum` function that can add numbers:
```swift
// Function registration
conversation.registerFunction(
    LeapFunction(
        name: "compute_sum",
        description: "Compute sum of a series of numbers",
        parameters: [
            LeapFunctionParameter(
                name: "values",
                type: .array(.string),
                description: "Numbers to compute sum. Values should be represented as strings."
            )
        ]
    )
)

// Function call handling
case .functionCall(let calls):
    for call in calls {
        if call.name == "compute_sum" {
            let result = computeSum(call.arguments["values"])
            // Display tool result and continue conversation
        }
    }
```

Try asking the model: "Can you compute the sum of 5, 10, and 15?"

### Conversation Management
Maintains chat context across messages:
```swift
conversation.addMessage(ChatMessage(
    role: .user,
    content: .text(messageText)
))
```

### UI Animations
Smooth animations for message appearance and typing indicators.

## Customization

### Styling
- Modify bubble colors in `MessageBubble.swift`
- Adjust animations in `TypingIndicator.swift`
- Update spacing and layout in `MessagesListView.swift`

### Functionality
- Add message persistence in `ChatStore.swift`
- Implement message editing/deletion
- Add support for different message types (images, files)

### Model Configuration
Update the model path in `ChatStore.swift`:
```swift
let modelRunner = try await Leap.load(
    modelPath: Bundle.main.bundlePath + "/LFM2-1.2B-8da4w_output_8da8w-seq_4096.bundle"
)
```

## Testing

The project includes test targets:
- **LeapChatExampleTests**: Unit tests for business logic
- **LeapChatExampleUITests**: UI automation tests

Run tests with:
```bash
make build  # Includes running tests
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
Ensure the model bundle exists in `LeapChatExample/Resources/`

### UI Issues
- Check iOS deployment target is 17.0+
- Verify SwiftUI preview compatibility

## Performance Tips

- The app uses `@MainActor` for UI updates
- Streaming responses minimize perceived latency
- Efficient list rendering with SwiftUI's `List`

## Next Steps

- Explore the simpler [LeapSloganExample](../LeapSloganExample/)
- Read the [LeapSDK documentation](https://leap.liquid.ai/docs/ios-quick-start-guide)
- Implement additional features like message search or export