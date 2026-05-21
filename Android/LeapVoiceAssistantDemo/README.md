# LeapVoiceAssistantDemo

A full-screen press-and-hold voice assistant powered by the Leap SDK's audio model and the prebuilt `VoiceAssistantWidget` from the `leap-ui` library. Hold the on-screen orb to record, release to stream both text and audio responses back, with the orb animation driven by live microphone / playback amplitude.

## Features

- Single-screen "orb" UI via `VoiceAssistantWidget` (Material 3, dark theme)
- Press-and-hold recording at 16 kHz mono float PCM (max 60 s) using `AudioRecord`
- Audio + text streaming output through `AudioTrack` with adaptive buffering driven by the model's real-time factor (RTF)
- Multi-turn voice conversation managed by the SDK's `VoiceAssistantStore` (MVI: state + intent)
- Real-time amplitude reporting from both the recorder and player feeds the orb visualization
- Automatic model download via `LeapModelDownloader` — backed by a foreground service, so the download survives process death and surfaces progress in the Android notification tray
- ktfmt (Google style) configured for the module

## Model

- **LFM2.5-Audio-1.5B** (`Q4_0`) — downloaded automatically by `LeapModelDownloader` on first launch (~1.5 GB)
- Cached in the downloader's managed storage (returned by `getModelResourceFolder`) plus `context.cacheDir/leap-cache/` for the runtime cache
- System prompt: `"Respond with interleaved text and audio."` so the model emits speech alongside any spoken text

## Prerequisites

- Android Studio Hedgehog (2023.1.1) or newer
- JDK 21 (use `JAVA_HOME=~/.sdkman/candidates/java/21.0.9-zulu` if managing JDKs with SDKman)
- Android device or emulator on API 31 (Android 12) or higher
- Permissions (declared in `AndroidManifest.xml`):
  - `RECORD_AUDIO` — requested at runtime on first launch
  - `INTERNET` — required for the initial model download
  - `POST_NOTIFICATIONS` — used by the downloader's foreground service on API 33+ (the demo does not currently request this at runtime; downloads still work, but progress won't appear in the notification tray until the user grants it in system settings)

## Running

```bash
./gradlew installDebug
# or open the project in Android Studio and press Run
```

Grant the microphone permission when prompted, wait for the orb status to turn green ("Ready"), then press and hold the orb to speak. Release to stream back the response. Generation stats (e.g. RTF, tokens/s) appear above the status text when the model reports them.

## Project Structure

```
app/src/main/kotlin/ai/liquid/leap/uidemo/
├── MainActivity.kt              # Hosts VoiceAssistantWidget, handles permission + status overlay
├── VoiceAssistantViewModel.kt   # Loads model via LeapModelDownloader, owns the VoiceAssistantStore
└── AudioPipeline.kt             # AndroidAudioRecorder, AndroidAudioPlayer, WAV encoder, LeapVoiceConversation
```

## Key SDK Patterns

**Download + load the audio model** (`VoiceAssistantViewModel.loadModel`):

```kotlin
val downloader = LeapModelDownloader(
    app,
    notificationConfig = LeapModelDownloaderNotificationConfig.build {
        notificationTitleDownloading = app.getString(R.string.notification_downloading_model)
        notificationTitleDownloaded  = app.getString(R.string.notification_model_ready)
    },
)

// 1. If the model isn't on disk yet, request a download and observe progress.
if (downloader.queryStatus(MODEL_NAME, QUANTIZATION_TYPE) is LeapModelDownloader.ModelDownloadStatus.NotOnLocal) {
    val progressJob = viewModelScope.launch {
        downloader.observeDownloadProgress(MODEL_NAME, QUANTIZATION_TYPE).collect { progress ->
            if (progress != null && progress.totalSizeInBytes > 0) {
                val frac = (progress.downloadedSizeInBytes.toFloat() / progress.totalSizeInBytes)
                    .coerceIn(0f, 1f)
                store.setModelProgress(frac, "Downloading (${(frac * 100).toInt()}%)")
            }
        }
    }
    downloader.requestDownloadModel(MODEL_NAME, QUANTIZATION_TYPE)
    // Wait for completion — progress emits null + status becomes Downloaded when done.
    downloader.observeDownloadProgress(MODEL_NAME, QUANTIZATION_TYPE).first { progress ->
        progress == null &&
            downloader.queryStatus(MODEL_NAME, QUANTIZATION_TYPE) is LeapModelDownloader.ModelDownloadStatus.Downloaded
    }
    progressJob.cancel(); progressJob.join()
}

// 2. Now that the model is on disk, load it.
val runner = downloader.loadModel(
    modelName = MODEL_NAME,
    quantizationType = QUANTIZATION_TYPE,
    options = ModelLoadingOptions(
        cacheOptions = ModelLoadingOptions.cacheOptions(
            path = app.cacheDir.resolve("leap-cache").absolutePath
        )
    ),
)
store.setConversation(
    LeapVoiceConversation(
        conv = runner.createConversation(systemPrompt = SYSTEM_PROMPT),
        systemPrompt = SYSTEM_PROMPT,
    )
)
```

`LeapModelDownloader` runs the download in a foreground service with its own notification channel, so the user sees download progress in the tray and the download keeps running even if the activity is destroyed. The `notificationConfig` titles come from `res/values/strings.xml` (`notification_downloading_model`, `notification_model_ready`).

**Drive the prebuilt widget** (`MainActivity`):

```kotlin
val vm = viewModel<VoiceAssistantViewModel>()
val state by vm.state.collectAsState()

VoiceAssistantWidget(
    state = state.widgetState,
    onIntent = vm::processIntent,
    modifier = Modifier.fillMaxSize().background(Color.Black),
)
```

`VoiceAssistantWidget` is provided by `ai.liquid.leap:leap-ui` (matching the SDK version). The view model forwards intents (e.g. `StartRecording`, `StopRecording`, `Reset`) to `VoiceAssistantStore`, which owns the recorder, player, and conversation.

**Bridge raw PCM to the SDK as a WAV `ChatMessageContent.Audio`** (`LeapVoiceConversation`):

```kotlin
override suspend fun generateResponse(
    audioSamples: FloatArray,
    sampleRate: Int,
    onAudioChunk: (samples: FloatArray, sampleRate: Int) -> Unit,
): GenerationStats? {
    val wavBytes = encodePcm16Wav(audioSamples, sampleRate)
    var stats: GenerationStats? = null
    conv.generateResponse(
        message = ChatMessage(
            role = ChatMessage.Role.USER,
            content = listOf(ChatMessageContent.Audio(wavBytes)),
        ),
        generationOptions = GenerationOptions(),
    ).collect { response ->
        when (response) {
            is MessageResponse.AudioSample -> onAudioChunk(response.samples, response.sampleRate)
            is MessageResponse.Complete    -> stats = response.stats
            else -> Unit
        }
    }
    return stats
}
```

The widget calls back with `AudioSample` events as soon as the model emits audio, so playback can begin before generation completes. `AndroidAudioPlayer` buffers samples adaptively based on the measured RTF (between 0.2 s and 3.0 s of pre-roll) to avoid underruns when the model is generating slower than real time.

## Notes

- Recording is hard-capped at 60 seconds in `AndroidAudioRecorder` to bound memory use.
- The audio model is single-turn in practice — `VoiceAssistantStore.reset()` swaps in a fresh `Conversation` (carrying the same system prompt) so history does not accumulate between turns.
- `leap-ui`, `leap-sdk`, and `leap-model-downloader` are all version-pinned together in `gradle/libs.versions.toml` via the shared `leapSdk` version reference.
- The downloader queues a single in-flight download per `(modelName, quantization)` pair — re-launching the activity while a download is in progress simply re-attaches to the existing job rather than restarting it.
