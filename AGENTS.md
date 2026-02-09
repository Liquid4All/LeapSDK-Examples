# LeapSDK Examples - Agent Instructions

This repository contains example applications demonstrating the use of the LeapSDK across iOS and Android platforms.

## Table of Contents
- [Code Style & Formatting](#code-style--formatting)
- [Commit Guidelines](#commit-guidelines)
- [Project Structure](#project-structure)
- [Architecture Patterns](#architecture-patterns)
- [Concurrency & Threading](#concurrency--threading)
- [Resource Management](#resource-management)
- [Audio Handling](#audio-handling)
- [Testing](#testing)
- [Common Pitfalls](#common-pitfalls)

---

## Code Style & Formatting

### Import Rules
- **Never use star imports** (wildcard imports)
  - ❌ Bad: `import androidx.compose.material.icons.*`
  - ✅ Good: `import androidx.compose.material.icons.Icons`
  - Applies to: Kotlin, Swift, and all other languages

### Android Formatting
- Use **ktfmt with Google style** for Kotlin code
  - Add `com.ncorti.ktfmt.gradle` plugin to Android modules
  - Configure: `ktfmt { googleStyle() }`
  - Format before committing: `./gradlew ktfmtFormat`

### General Rules
- All files must end with a newline character
- **CRITICAL: Always use string resources instead of hardcoded strings in Android**
  - ❌ Bad: `Text("Recording stopped")`
  - ✅ Good: `Text(stringResource(R.string.recording_stopped))`
  - Add new entries to `res/values/strings.xml` for all user-facing text
  - This includes: UI text, error messages, accessibility descriptions, announcements
  - Exception: Debug/log messages can use string literals
- Prefer explicit types when clarity matters
- Avoid `!!` (force unwrap) - use safe calls or proper null handling

---

## Commit Guidelines

### Commit Message Format
Follow [Conventional Commits](https://www.conventionalcommits.org/):
```
<type>: <description>

[optional body explaining the change and why]

[optional breaking changes section]
```

**Types:**
- `feat:` - New feature
- `fix:` - Bug fix
- `refactor:` - Code restructuring without behavior change
- `test:` - Adding or updating tests
- `docs:` - Documentation changes
- `perf:` - Performance improvements
- `chore:` - Maintenance tasks

**Examples:**
```
feat: add graceful streaming completion for audio playback

fix: resolve race condition in audio streaming initialization

refactor: extract string provider interface for testability
```

### Commit Organization
- Create **logical commits** that group related changes
- Each commit should represent a single cohesive change
- Avoid mixing unrelated changes (e.g., bug fixes + features)
- Write detailed commit messages explaining **why**, not just **what**

---

## Project Structure

### SDK Locations
- **iOS SDK**: `~/development/leap-ios-sdk`
- **Android/KMP SDK**: `~/development/leap-android-sdk`

### iOS Examples
- Located in `iOS/` directory
- Xcode project generation via `project.yml` files
- LeapSDK integration via Swift Package Manager

### Android Examples
- Located in `Android/` directory
- Built with Gradle and Kotlin
- LeapSDK integration via Kotlin Multiplatform

### Key SDK APIs (Android)
- **Package**: `ai.liquid.leap` (not `ai.liquid.sdk`)
- **Main Classes**:
  - `LeapClient` - SDK entry point
  - `ModelRunner` - Model lifecycle management
  - `Conversation` - Chat conversation management
  - `ChatMessage` - Message representation with `Role` enum (USER, ASSISTANT, SYSTEM, TOOL)
  - `ChatMessageContent` - Sealed class: Text, Audio, Image
  - `MessageResponse` - Sealed interface: Chunk, AudioSample, Complete, etc.
  - `LeapModelDownloader` - Automatic model downloading with progress

---

## Architecture Patterns

### MVI (Model-View-Intent) Pattern
Use MVI for all Android UI state management:

**State Definition:**
```kotlin
data class MyState(
  val items: List<Item> = emptyList(),
  val isLoading: Boolean = false,
  val error: String? = null,
)
```

**Event Definition:**
```kotlin
sealed interface MyEvent {
  data object LoadData : MyEvent
  data class UpdateItem(val id: String) : MyEvent
}
```

**ViewModel Structure:**
```kotlin
class MyViewModel : ViewModel() {
  private val _state = MutableStateFlow(MyState())
  val state: StateFlow<MyState> = _state.asStateFlow()

  fun onEvent(event: MyEvent) {
    when (event) {
      is MyEvent.LoadData -> loadData()
      is MyEvent.UpdateItem -> updateItem(event.id)
    }
  }

  private fun loadData() {
    _state.update { it.copy(isLoading = true) }
    // ... implementation
  }
}
```

**Composable Usage:**
```kotlin
@Composable
fun MyScreen(state: MyState, onEvent: (MyEvent) -> Unit) {
  // UI receives state and event handler, not ViewModel
  Button(onClick = { onEvent(MyEvent.LoadData) }) {
    Text("Load")
  }
}
```

### Sealed Classes for Type Safety
- Use sealed interfaces for events, states, and responses
- Provides exhaustive `when` expressions
- Makes invalid states unrepresentable
- Example: `GenerationState.Idle`, `GenerationState.Generating`, etc.

---

## Concurrency & Threading

### Coroutine Best Practices
- **Use proper dispatchers:**
  - `Dispatchers.IO` - File I/O, network, audio operations
  - `Dispatchers.Default` - CPU-intensive work
  - `Dispatchers.Main` - UI updates (automatically in ViewModel)

- **Prefer coroutines over threads:**
  ```kotlin
  // ✅ Good
  viewModelScope.launch(Dispatchers.IO) {
    val result = loadData()
  }

  // ❌ Bad
  Thread {
    val result = loadData()
  }.start()
  ```

- **Use structured concurrency:**
  ```kotlin
  viewModelScope.launch {
    supervisorScope {
      launch { task1() }
      launch { task2() }
    }
  }
  ```

### Channel vs Flow
- **Channel** - For hot streams with backpressure (producer-consumer)
  ```kotlin
  val channel = Channel<AudioData>(capacity = 100) // Bounded
  ```

- **Flow** - For cold streams, reactive data
  ```kotlin
  val dataFlow: Flow<Data> = flow {
    emit(loadData())
  }
  ```

### Race Condition Prevention
- **Always use synchronous cleanup** before creating resources:
  ```kotlin
  // ✅ Good - synchronous cleanup
  fun startStreaming() {
    audioTrack?.stop()
    audioTrack?.release()
    audioTrack = null

    audioTrack = AudioTrack.Builder()...build()
  }

  // ❌ Bad - async cleanup causes race
  fun startStreaming() {
    scope.launch { reset() } // Returns immediately!
    audioTrack = AudioTrack.Builder()...build() // Can be nulled by reset()
  }
  ```

- **Use Mutex for shared mutable state:**
  ```kotlin
  private val mutex = Mutex()

  suspend fun updateSharedState() {
    mutex.withLock {
      sharedState = newValue
    }
  }
  ```

### Avoid `runBlocking` in Production Code
- ❌ Don't use `runBlocking` on main thread
- ✅ Use `suspend` functions and proper coroutine scopes
- Exception: OK in `onCleared()` for cleanup when `viewModelScope` is cancelled

---

## Resource Management

### ViewModel Cleanup
Always clean up resources in `onCleared()`:

```kotlin
override fun onCleared() {
  super.onCleared()

  // Cancel jobs first
  generationJob?.cancel()

  // Clean up resources with error handling
  audioRecorder.release()

  // Model cleanup - fire-and-forget is acceptable
  // (process death will clean up anyway)
  CoroutineScope(Dispatchers.IO).launch {
    try {
      modelRunner?.unload()
    } catch (e: Exception) {
      Log.e(TAG, "Error unloading model", e)
    }
  }
}
```

### Audio Resource Management
- **Always release audio resources:**
  ```kotlin
  audioTrack?.apply {
    stop()
    release()
  }
  audioTrack = null
  ```

- **Abandon audio focus when done:**
  ```kotlin
  audioManager.abandonAudioFocusRequest(focusRequest)
  ```

- **Set buffer limits to prevent OOM:**
  ```kotlin
  // Bounded channel
  val audioQueue = Channel<FloatArray>(capacity = 100)

  // Pre-allocated buffer
  val audioBuffer = ArrayList<Float>(720_000) // 30 sec @ 24kHz
  ```

### Memory Management
- Pre-allocate collections when size is known
- Use bounded channels for streaming data
- Set maximum recording durations
- Clear large buffers after use
- Document buffer size calculations:
  ```kotlin
  // Pre-allocate for ~30 seconds of audio at 24kHz sample rate
  // (24,000 samples/sec × 30 sec = 720,000 samples ≈ 2.88 MB)
  val audioBuffer = ArrayList<Float>(720_000)
  ```

---

## Audio Handling

### Audio Focus Management
Request and manage audio focus properly:

```kotlin
private fun requestAudioFocus(): Boolean {
  val request = AudioFocusRequest.Builder(AudioManager.AUDIOFOCUS_GAIN)
    .setAudioAttributes(
      AudioAttributes.Builder()
        .setUsage(AudioAttributes.USAGE_MEDIA)
        .setContentType(AudioAttributes.CONTENT_TYPE_SPEECH)
        .build()
    )
    .setOnAudioFocusChangeListener { focusChange ->
      when (focusChange) {
        AudioManager.AUDIOFOCUS_LOSS -> {
          stop()
          abandonAudioFocus()
        }
      }
    }
    .build()

  return audioManager.requestAudioFocus(request) ==
    AudioManager.AUDIOFOCUS_REQUEST_GRANTED
}
```

### Streaming Audio Patterns

**Graceful completion:**
```kotlin
// Allow buffered audio to play out after generation completes
fun finishStreaming() {
  audioQueue?.close() // Close channel but don't cancel playback
}

// Immediate stop
fun stopStreaming() {
  streamingJob?.cancel()
  audioQueue?.close()
  audioTrack?.stop()
}
```

**Completion callbacks:**
```kotlin
class AudioPlayer(
  private val onPlaybackCompleted: (() -> Unit)? = null,
  private val onStreamingCompleted: (() -> Unit)? = null,
) {
  // Invoke callback when buffer is fully drained
  for (samples in queue) {
    track.write(samples, ...)
  }
  onStreamingCompleted?.invoke()
}
```

### Audio State Tracking
Track different audio states clearly:

```kotlin
data class AudioState(
  val isRecording: Boolean = false,
  val isPlaying: Boolean = false,
  val isStreamingPlayback: Boolean = false, // Buffer draining
  val playingMessageId: String? = null,
)
```

---

## Testing

### Test Philosophy
- **No mocks** - Use fakes/test doubles that implement real interfaces
- **Test behavior, not implementation**
- **Use Robolectric for Android framework dependencies**
- **Keep tests fast and focused**

### Test Double Pattern (Fakes)
Create fake implementations for testing:

```kotlin
class FakeAudioPlayback : AudioPlayback {
  var isPlaying = false
    private set
  var playCallCount = 0
    private set
  var lastSamples: FloatArray? = null
    private set

  override fun play(samples: FloatArray, sampleRate: Int) {
    playCallCount++
    lastSamples = samples
    isPlaying = true
  }

  // Simulate callbacks for testing
  fun simulatePlaybackCompleted() {
    isPlaying = false
    onPlaybackCompleted?.invoke()
  }
}
```

### Test Structure
```kotlin
@Test
fun `descriptive test name describing behavior`() = runTest {
  // Arrange
  val viewModel = AudioDemoViewModel(
    application = app,
    audioPlayback = fakeAudioPlayback,
  )

  // Act
  viewModel.onEvent(AudioDemoEvent.PlayAudio(...))

  // Assert
  fakeAudioPlayback.playCallCount shouldBe 1
  viewModel.state.value.isPlaying shouldBe true
}
```

### Integration Tests
Test complex interactions:
```kotlin
@Test
fun `rapid start stop recording should not cause state corruption`() = runTest {
  repeat(10) {
    viewModel.onEvent(AudioDemoEvent.StartRecording)
    viewModel.onEvent(AudioDemoEvent.StopRecording)
  }

  viewModel.state.value.recordingState shouldBe RecordingState.Idle
}
```

---

## Common Pitfalls

### ❌ Avoid These Patterns

**1. Async cleanup with new resource creation:**
```kotlin
// ❌ Race condition - reset() returns immediately
fun start() {
  reset() // Launches async job
  resource = createResource() // Can be nulled by reset()
}
```

**2. Using `System.currentTimeMillis()` for intervals:**
```kotlin
// ❌ Can jump backward/forward during time sync
val elapsed = System.currentTimeMillis() - startTime

// ✅ Use monotonic clock
val elapsed = SystemClock.elapsedRealtime() - startTime
```

**3. Unbounded collections for streaming:**
```kotlin
// ❌ Can cause OOM
val audioSamples = mutableListOf<Float>()

// ✅ Bounded with documented limits
val audioSamples = ArrayList<Float>(720_000) // 30 sec max
```

**4. Swallowing exceptions:**
```kotlin
// ❌ Silent failure
try {
  operation()
} catch (e: Exception) {
  // Nothing
}

// ✅ Log and handle
try {
  operation()
} catch (e: Exception) {
  Log.e(TAG, "Operation failed", e)
  _state.update { it.copy(error = e.message) }
}
```

**5. Blocking the main thread:**
```kotlin
// ❌ ANR risk
override fun onCleared() {
  runBlocking {
    heavyOperation()
  }
}

// ✅ Fire-and-forget for cleanup
override fun onCleared() {
  CoroutineScope(Dispatchers.IO).launch {
    try {
      heavyOperation()
    } catch (e: Exception) {
      Log.e(TAG, "Cleanup failed", e)
    }
  }
}
```

**6. Missing side effect buffering:**
```kotlin
// ❌ Events can be lost if no collector
private val _sideEffect = MutableSharedFlow<SideEffect>()

// ✅ Buffer for reliability
private val _sideEffect = MutableSharedFlow<SideEffect>(
  replay = 0,
  extraBufferCapacity = 1,
  onBufferOverflow = BufferOverflow.DROP_OLDEST
)
```

---

## Error Handling Best Practices

### User-Facing Errors
- Provide clear, actionable error messages
- Suggest recovery actions when possible
- Use Snackbars for transient errors
- Use Dialogs for critical errors requiring user action

```kotlin
sealed interface SideEffect {
  data class ShowSnackbar(val message: String) : SideEffect
  data class ShowError(val title: String, val message: String) : SideEffect
}
```

### Permission Handling
```kotlin
// Request permissions only when needed
LaunchedEffect(state.modelState) {
  if (state.modelState is ModelState.Loading) {
    permissionLauncher.launch(arrayOf(
      Manifest.permission.RECORD_AUDIO
    ))
  }
}

// Show clear explanations on denial
if (deniedPermissions.contains(Manifest.permission.RECORD_AUDIO)) {
  snackbar.show("Microphone permission required for audio recording")
}
```

### Logging Strategy
- Use appropriate log levels (DEBUG, INFO, WARN, ERROR)
- Include context in log messages
- Log entry/exit for complex operations (with DEBUG level)
- Log errors with full stack traces

```kotlin
Log.d(TAG, "Starting streaming playback job")
Log.e(TAG, "Failed to request audio focus", exception)
```

---

## Performance Considerations

### Compose Optimization
- Use `remember` for derived values to prevent recomposition
- Use `key` parameter in lists for stable identity
- Avoid lambda allocations in recomposing scope

```kotlin
val isEnabled = remember(state.modelState) {
  state.modelState is ModelState.Ready
}
```

### Memory Efficiency
- Reuse buffers when possible
- Clear large collections after use
- Use primitive arrays (`FloatArray`) instead of `List<Float>` for performance
- Pre-allocate collections when size is known

---

## Additional Resources

- [Kotlin Coroutines Guide](https://kotlinlang.org/docs/coroutines-guide.html)
- [Android Architecture Components](https://developer.android.com/topic/architecture)
- [Jetpack Compose Best Practices](https://developer.android.com/jetpack/compose/performance)
- [Audio Focus Guidelines](https://developer.android.com/guide/topics/media-apps/audio-focus)
