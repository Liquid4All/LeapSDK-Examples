# LeapAudioDemo - Android

An Android example app demonstrating audio input and output capabilities with the Leap SDK using the LFM2.5-Audio-1.5B model.

## Features

- **Audio Input**: Record audio prompts using the device microphone (max 60 seconds)
- **Text Input**: Send text prompts to the model
- **Multimodal Output**: Receive responses with both text and audio
- **Single-turn Conversations**: Each message is independent (audio model limitation)
- **Model Downloading**: Automatic model download with progress tracking and retry on failure
- **Streaming**: Real-time streaming of text and audio responses
- **Error Handling**: Clear error messages with actionable recovery steps
- **MVI Architecture**: Clean separation of concerns with event-driven pattern

## Model

- **Model**: LFM2.5-Audio-1.5B
- **Quantization**: Q4_0
- **Context Size**: 1024 tokens

The model supports both speech and text input/output, enabling natural conversational interactions with audio.

## Requirements

- **Minimum SDK**: Android 12 (API 31) or higher
- **Storage**: ~2-3 GB free space for model download
- **Permissions**:
  - `RECORD_AUDIO` - Required for audio recording
  - `POST_NOTIFICATIONS` - For download progress notifications (Android 13+)
- **Network**: Internet connection for initial model download
- **Authentication**: GitHub Packages access for Leap SDK dependencies

## Setup

1. **Configure GitHub Packages Authentication**:

   Set the following environment variables:
   ```bash
   export GITHUB_PACKAGES_USERNAME="your-github-username"
   export GITHUB_PACKAGES_TOKEN="your-github-personal-access-token"
   ```

2. **Build and Run**:
   ```bash
   ./gradlew installDebug
   ```

## Usage

1. **Load Model**:
   - Tap "Load Model" to download and initialize the model
   - Grant microphone permission when prompted
   - Wait for download to complete (progress shown in status bar)
   - If download fails, tap "Retry" button

2. **Text Input**:
   - Type a message in the text field
   - Tap the send button to submit

3. **Audio Input**:
   - Tap the microphone floating action button (FAB) to start recording
   - Speak your message (max 60 seconds)
   - Tap the stop icon to finish recording and send automatically

4. **Play Audio**:
   - Audio responses play automatically during generation
   - Tap "Play Audio" on any message to replay it

5. **Conversation**:
   - Each message is handled independently
   - Audio model does not maintain conversation context

## Architecture

The app follows the **Model-View-Intent (MVI)** pattern for clean, unidirectional data flow:

- **MainActivity**: Entry point with composable permission handling
- **AudioDemoViewModel**:
  - State management with `StateFlow`
  - Event handling through sealed `AudioDemoEvent` interface
  - Model loading, conversation management, and audio processing
- **AudioDemoScreen**:
  - Pure composable function receiving state and event handler
  - No direct ViewModel dependency for better testability
- **AudioRecorder**: Coroutine-based audio capture with 60-second limit
- **AudioPlayer**: Streaming audio playback with channel-based buffering

## Implementation Details

**Technologies:**
- **Leap SDK**: Model inference and conversation management
- **LeapModelDownloader**: Automatic downloading with progress notifications
- **Jetpack Compose**: Modern, declarative UI with Material 3
- **Kotlin Coroutines**: Asynchronous operations on `Dispatchers.IO`
- **StateFlow**: Reactive state management with `.update` extension
- **Channel**: Bounded buffering for smooth audio streaming
- **AudioRecord/AudioTrack**: Low-level audio I/O

**Key Patterns:**
- MVI architecture with sealed events
- Single state source of truth
- Coroutine-based audio processing
- Error handling with user-friendly recovery
- Resource cleanup in ViewModel `onCleared()`

## Code Structure

```
app/src/main/kotlin/ai/liquid/leapaudiodemo/
├── MainActivity.kt           # Main activity with permissions
├── AudioDemoViewModel.kt     # Business logic and state management
├── AudioDemoScreen.kt        # Compose UI components
├── AudioRecorder.kt          # Audio recording functionality
└── AudioPlayer.kt            # Audio playback functionality
```

## Configuration

You can customize the following in `AudioDemoViewModel.kt`:

```kotlin
private const val MODEL_NAME = "LFM2.5-Audio-1.5B"
private const val QUANTIZATION = "Q4_0"
private const val ENABLE_MULTI_TURN = false  // Audio model doesn't support multi-turn
```

In `AudioRecorder.kt`:
```kotlin
private const val MAX_RECORDING_SECONDS = 60  // Maximum recording duration
```

## Troubleshooting

**"Failed to start recording"**
- Check that microphone permission is granted
- Go to Settings → Apps → LeapAudioDemo → Permissions
- Restart the app after granting permission

**"Not enough storage space"**
- Model requires ~2-3 GB of free space
- Free up space and tap "Retry"
- Check available storage in Settings

**"Network error"**
- Ensure internet connection is active
- Check firewall/proxy settings
- Tap "Retry" when connection is restored

**Audio not playing**
- Check device volume
- Ensure no other apps are using audio
- Try recording a new message

**App crashes on first launch**
- Verify GitHub Packages authentication is configured
- Check that Leap SDK dependency is accessible
- Review logcat for detailed error messages

## Notes

- Audio recording limited to 60 seconds to prevent memory issues
- Model is downloaded on first use (~2-3 GB) and cached locally
- Audio recorded at 16kHz, played back at model's sample rate (24kHz)
- Permissions requested when loading model (better UX than immediate request)
- Single-turn conversations (audio model does not support conversation history)
