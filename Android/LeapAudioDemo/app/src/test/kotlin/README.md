# LeapAudioDemo Unit Tests

This directory contains unit tests for the LeapAudioDemo application using Kotlin Test and Kotest.

## Test Framework

- **kotlin.test** - Core testing framework
- **Kotest Assertions** - Expressive matchers and assertions
- **Kotlinx Coroutines Test** - Testing coroutines and Flows
- **Turbine** - Testing Flow emissions
- **AndroidX Arch Core Testing** - InstantTaskExecutorRule for LiveData/StateFlow
- **Robolectric** - Unit testing with Android framework classes (no device/emulator needed)

## Test Coverage

### AudioDemoViewModelRobolectricTest (11 tests)

Tests actual ViewModel behavior using Robolectric for Android framework support:

- ViewModel initialization with proper default state
- UpdateInputText event handling and StateFlow emissions
- ToggleRecording state transitions (handles multiple state emissions)
- RecordingFailed error handling
- SendTextPrompt validation and message creation
- SendAudioPrompt with empty/valid samples
- StateFlow emission verification using Turbine

**Note:** Model loading and response generation tested separately in integration tests.

### AudioDemoViewModelTest (15 tests)

Tests state management and event handling without mocking external dependencies:

- Initial state defaults validation
- State copy and update operations
- Message list management
- Input text updates
- Loading and error states
- Recording and generating states
- Audio playback state (playingMessageId tracking)
- Event data validation

**Note:** Model loading and API interaction tests require instrumented tests with test doubles for LeapSDK.

### AudioRecorderTest (3 tests)

Tests AudioRecorder data structures:

- AudioCapture data class with samples and sample rate
- Support for various sample rates
- Empty sample handling

**Instrumented tests needed for:**
- Microphone permission handling
- AudioRecord initialization
- Recording lifecycle (start/stop)
- 60-second recording limit
- Coroutine cancellation
- Resource cleanup

### WavEncodingTest (14 tests)

Tests WAV encoding utilities:

- Little-endian int to bytes conversion
- Little-endian short to bytes conversion
- Float to 16-bit PCM conversion
- Clamping for out-of-range values

**Future improvements:**
- Extract encoding functions to separate utility class
- Add full WAV header structure tests
- Test various sample rates and buffer sizes

## Running Tests

```bash
# Run all unit tests
./gradlew test

# Run with coverage
./gradlew testDebugUnitTestCoverage

# Run specific test class
./gradlew test --tests AudioDemoViewModelTest
```

## Testing Philosophy

This project follows a pragmatic testing approach:

1. **No mocking libraries** - Tests use real implementations or simple test doubles
2. **State-based testing** - Focus on observable state changes rather than implementation details
3. **Pure function testing** - Prioritize testing logic that doesn't require Android runtime
4. **Instrumented tests for Android** - Platform-dependent code tested separately

## Future Work

### Integration Tests

Create integration tests for:
- Model download and loading flow
- Response streaming with LeapSDK
- Audio recording and playback
- Error recovery scenarios
- Resource cleanup verification

### Instrumented Tests (androidTest/)

Required for testing:
- AudioRecord/AudioTrack functionality
- Android permissions
- Media codec integration
- UI interactions with Compose
- End-to-end user flows

### Test Utilities

Consider extracting for better testability:
- AudioEncoder utility (floatArrayToWav)
- AudioValidator (sample validation)
- Fake LeapSDK implementations for integration tests
