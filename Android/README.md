# LeapSDK Android Example Apps

Gradle-based Android demos using LeapSDK v0.10.8. Each app has its own README with model, build, and integration details.

## Demos

| App | What it shows |
| --- | --- |
| [LeapChat](LeapChat/) | Multi-turn chat, streaming, reasoning chunks, function calling (`compute_sum`), conversation persistence with `kotlinx.serialization` |
| [SloganApp](SloganApp/) | Single-turn slogan generation with an Android Views UI |
| [LeapAudioDemo](LeapAudioDemo/) | Audio input/output with streaming playback and `AudioTrack` buffering |
| [ShareAI](ShareAI/) | Receives shared URLs, scrapes the page with Jsoup, and streams a Markdown summary |
| [RecipeGenerator](RecipeGenerator/) | [Constrained JSON generation](https://leap.liquid.ai/docs/edge-sdk/android/constrained-generation) with the `@Generatable` annotation |
| [VLMExample](VLMExample/) | Vision language model — image attachment via `ChatMessageContent.Image` |
| [LeapVoiceAssistantDemo](LeapVoiceAssistantDemo/) | Press-and-hold voice assistant via `VoiceAssistantWidget` from `leap-ui` |
| [LeapKoogAgent](LeapKoogAgent/) | Agent integration with the [Koog framework](https://docs.koog.ai). Requires a vendored `koog-edge` AAR and a manually-pushed model bundle. |

## Model downloading

Every demo (except `LeapKoogAgent`, which uses a manually-pushed bundle) uses **`LeapModelDownloader`** from `ai.liquid.leap.downloader`. The downloader runs in a foreground service so downloads survive process death and surface progress in the Android notification tray. The standard pattern is:

1. `queryStatus(modelName, quantization)` — check the local cache
2. If `NotOnLocal`: `observeDownloadProgress(...)` to drive UI, then `requestDownloadModel(...)`
3. `loadModel(...)` — returns a `ModelRunner` ready for inference

See `LeapAudioDemo/README.md` or `LeapVoiceAssistantDemo/README.md` for a worked example of the full lifecycle including progress reporting.

## Prerequisites

- Android Studio Hedgehog (2023.1.1) or newer
- JDK 21 (e.g. `JAVA_HOME=~/.sdkman/candidates/java/21.0.9-zulu`)
- Android device or emulator with internet access (for the initial model download — except `LeapKoogAgent`, which uses a manually pushed bundle)
