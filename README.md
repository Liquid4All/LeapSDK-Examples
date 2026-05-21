# LeapSDK Examples

Example applications demonstrating [LeapSDK](https://leap.liquid.ai) for on-device AI inference across mobile, desktop, and the browser. All demos auto-download their models on first launch — no manual setup required.

Current SDK pin: **v0.10.8**. Each demo picks the model best suited to what it showcases — see the individual demo README for the exact model and quantization.

## Platforms

### 📱 [iOS](./iOS/) — Swift Package Manager + XcodeGen

| Demo | Description |
| --- | --- |
| [LeapSloganExample](./iOS/LeapSloganExample) | Simple SwiftUI slogan generator |
| [LeapChatExample](./iOS/LeapChatExample) | Multimodal chat with streaming + image attachments |
| [LeapAudioDemo](./iOS/LeapAudioDemo) | Speech-to-speech conversation with audio I/O |
| [LeapVLMExample](./iOS/LeapVLMExample) | Vision language model demo |
| [LeapVoiceAssistantDemo](./iOS/LeapVoiceAssistantDemo) | Press-and-hold voice assistant via `VoiceAssistantWidget` (`leap-ui`) |
| [RecipeGenerator](./iOS/RecipeGenerator) | Constrained JSON output with `@Generatable` macros |

### 🖥️ [macOS](./macOS/) — Swift Package Manager + XcodeGen

| Demo | Description |
| --- | --- |
| [LeapVLMExample](./macOS/LeapVLMExample) | Vision language model demo |
| [LeapVoiceAssistantDemo](./macOS/LeapVoiceAssistantDemo) | Press-and-hold voice assistant (macOS counterpart of the iOS demo) |

### 🤖 [Android](./Android/) — Gradle + Jetpack Compose

| Demo | Description |
| --- | --- |
| [SloganApp](./Android/SloganApp) | Basic slogan generator |
| [LeapChat](./Android/LeapChat) | Full-featured chat application |
| [LeapAudioDemo](./Android/LeapAudioDemo) | Audio input/output with streaming playback |
| [ShareAI](./Android/ShareAI) | Summarize a web page by sharing it to the app |
| [RecipeGenerator](./Android/RecipeGenerator) | Constrained JSON output |
| [VLMExample](./Android/VLMExample) | Vision language model demo |
| [LeapVoiceAssistantDemo](./Android/LeapVoiceAssistantDemo) | Press-and-hold voice assistant via `VoiceAssistantWidget` (`leap-ui`) |
| [LeapKoogAgent](./Android/LeapKoogAgent) | Agent demo integrating the [Koog framework](https://docs.koog.ai) |

### 🌐 [Web](./Web/) — Kotlin/Wasm + Compose for Web

| Demo | Description |
| --- | --- |
| [LeapVoiceAssistantDemo](./Web/LeapVoiceAssistantDemo) | Compose-for-Web port of the voice assistant |

### ☕ [JVM](./JVM/) — Kotlin/JVM (Linux, macOS, Windows)

| Demo | Description |
| --- | --- |
| [LeapChatCli](./JVM/LeapChatCli) | REPL chat CLI using `leap-sdk-jvm`. Single artifact runs on Linux/macOS/Windows — JNI binaries are bundled in the JAR and extracted at runtime. |

### 🐧 [Linux](./Linux/) — Kotlin/Native (linuxX64)

| Demo | Description |
| --- | --- |
| [LeapChatCli](./Linux/LeapChatCli) | Native x86_64 executable using `leap-sdk-linuxx64`. The `ai.liquid.leap.nativelibs` Gradle plugin auto-extracts `libinference_engine.so` from the `:natives@zip` classifier alongside the binary. |

### 🪟 [Windows](./Windows/) — Kotlin/Native (mingwX64)

| Demo | Description |
| --- | --- |
| [LeapChatCli](./Windows/LeapChatCli) | Native x86_64 `.exe` using `leap-sdk-mingwx64`. Same `ai.liquid.leap.nativelibs` plugin auto-extracts `inference_engine.dll`. |

## Quick Start

### iOS / macOS

Requires Xcode 15+ and [XcodeGen](https://github.com/yonaskolb/XcodeGen) (`brew install xcodegen`).

```bash
cd iOS/LeapSloganExample        # or any iOS/macOS demo
make setup && make open
```

### Android

Requires JDK 21 and the Android SDK.

```bash
cd Android/SloganApp
./gradlew installDebug
```

### Web

```bash
cd Web/LeapVoiceAssistantDemo
./gradlew wasmJsBrowserDevelopmentRun
# open http://localhost:8080
```

### JVM / Linux / Windows

```bash
# JVM (cross-platform)
cd JVM/LeapChatCli && ./gradlew installDist

# Linux native (run on a Linux x86_64 host)
cd Linux/LeapChatCli && ./gradlew linkReleaseExecutableLinuxX64

# Windows native (run on a Windows x86_64 host)
cd Windows/LeapChatCli && ./gradlew linkReleaseExecutableMingwX64
```

## What is LeapSDK?

LeapSDK runs Liquid AI models locally using the Liquid Inference Engine. Key features:

- **On-device inference** — no internet required after the initial model download
- **Real-time streaming** — token-by-token response generation
- **Cross-platform** — iOS, macOS, Android, Web (Kotlin/Wasm), JVM, Linux, and Windows from a unified API
- **Multimodal** — text, vision, and audio (speech-to-speech) models supported
- **Constrained generation** — structured JSON output via schema constraints (`@Generatable` on Swift; equivalent APIs on Kotlin)

## Documentation

- 📚 [iOS Quick Start](https://leap.liquid.ai/docs/edge-sdk/ios/ios-quick-start-guide)
- 📚 [Android Quick Start](https://leap.liquid.ai/docs/edge-sdk/android/android-quick-start-guide)
- 🔗 [iOS SDK repository](https://github.com/Liquid4All/leap-ios)
- 🔗 [Android / KMP SDK repository](https://github.com/Liquid4All/leap-android-sdk)
- 🔗 [Model registry](https://leap.liquid.ai/api/models)

## License

See [LICENSE](./LICENSE).
