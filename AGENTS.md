# LeapSDK Examples - Agent Instructions

This repository contains example applications demonstrating the use of the LeapSDK across iOS and Android platforms.

## Project Preferences

### Code Style
- Do not use star imports (wildcard imports)
  - ❌ Bad: `import androidx.compose.material.icons.*`
  - ✅ Good: `import androidx.compose.material.icons.Icons`
  - Applies to all languages: Kotlin, Swift, etc.

### Commit Messages
- Focus on describing the technical changes made

## Project Structure

### iOS Examples
- Located in `iOS/` directory
- Use Xcode project generation via `project.yml` files
- LeapSDK integration via Swift Package Manager

### Android Examples
- Located in `Android/` directory
- Built with Gradle and Kotlin
- LeapSDK integration via Kotlin Multiplatform

## SDK Locations

- **iOS SDK**: `~/development/leap-ios-sdk`
- **Android/KMP SDK**: `~/development/leap-android-sdk`

## Key APIs

### Android SDK
- Package: `ai.liquid.leap` (not `ai.liquid.sdk`)
- Main classes:
  - `LeapClient`
  - `ModelRunner`
  - `Conversation`
  - `ChatMessage` (with `Role` enum: USER, ASSISTANT, SYSTEM, TOOL)
  - `ChatMessageContent` (sealed class: Text, Audio, Image)
  - `MessageResponse` (sealed interface: Chunk, AudioSample, Complete, etc.)
  - `LeapModelDownloader` for automatic model downloading

### iOS SDK
- Main classes follow similar patterns

### Patterns and Best Practices

**Android Architecture:**
- Use MVI (Model-View-Intent) pattern for UI state management
- Sealed interfaces for events with descriptive names (e.g., `AudioDemoEvent`)
- Single `onEvent` handler routing events to private functions
- State updates using `.update { }` extension on MutableStateFlow
- Composables receive state and event handler, not ViewModel directly

**Coroutines:**
- Use `Dispatchers.IO` for I/O operations (file, network, audio)
- Prefer coroutines over threads for concurrent operations
- Use `suspend` functions instead of blocking calls
- Bounded `Channel` for producer-consumer patterns (prevent OOM)

**Resource Management:**
- Implement cleanup in ViewModel `onCleared()` with try-catch
- Use `runBlocking(Dispatchers.IO)` for cleanup if viewModelScope is cancelled
- Always release audio resources (AudioRecord, AudioTrack)
- Set maximum limits for memory buffers (e.g., recording duration)

**Error Handling:**
- Return Boolean or Result types to indicate success/failure
- Provide user-friendly error messages with recovery actions
- Show Snackbar or Dialog for permission denials
- Log errors with appropriate tags for debugging
- Add retry mechanisms for network operations

**Permissions:**
- Use Composable permission handling with `rememberLauncherForActivityResult`
- Request permissions when needed (not on app launch)
- Show clear explanations when permissions are denied
- Guide users to Settings if permission is critical

### Preferences
- All files must end with a newline character
- Prefer MVI pattern for all UI components
- Use explicit imports (no star imports)
- Use string resources instead of hardcoded strings in Android
- In tests, do not use mocks
