# LeapSDK Examples

This repository contains example applications demonstrating how to use the LeapSDK for on-device AI inference across multiple platforms.

## Platforms

### 📱 [iOS Examples](./iOS/)

- **LeapSloganExample**: Simple SwiftUI app for slogan generation
- **LeapChatExample**: Comprehensive chat application with real-time streaming
- **LeapAudioDemo**: Audio processing and transcription demo
- **LeapVLMExample**: Vision language model demo for image understanding
- **LeapVoiceAssistantDemo**: Press-and-hold voice assistant powered by `VoiceAssistantWidget` from `leap-ui`
- **RecipeGenerator**: Recipe generation with constrained JSON output

Swift Package Manager integration with XcodeGen project generation.

### 🖥️ [macOS Examples](./macOS/)

- **LeapVLMExample**: Vision language model demo for image understanding
- **LeapVoiceAssistantDemo**: Press-and-hold voice assistant (macOS counterpart of the iOS demo)

Swift Package Manager integration with XcodeGen project generation.

### 🤖 [Android Examples](./Android/)

- **SloganApp**: Basic slogan generator using Jetpack Compose
- **LeapChat**: Full-featured chat application with modern Android UI
- **LeapAudioDemo**: Audio input/output demo with streaming playback and interactive controls
- **ShareAI**: Web page summary generator
- **RecipeGenerator**: Recipe generation with constrained JSON output
- **VLMExample**: Vision language model demo for image understanding
- **LeapVoiceAssistantDemo**: Press-and-hold voice assistant powered by `VoiceAssistantWidget` from `leap-ui`
- **LeapKoogAgent**: AI agent demo integrating [Koog framework](https://docs.koog.ai) with LeapSDK

Gradle-based projects using the LeapSDK Android library.

### 🌐 [Web Examples](./Web/)

- **LeapVoiceAssistantDemo**: Kotlin/Wasm Compose-for-Web port of the voice assistant. Run with `./gradlew wasmJsBrowserDevelopmentRun` (dev server) or `./gradlew wasmJsBrowserDistribution` (static bundle).

### ☕ [JVM Examples](./JVM/)

- **LeapChatCli**: REPL chat CLI on the JVM using `leap-sdk-jvm`. Single artifact runs on Linux/macOS/Windows — JNI binaries are bundled in the JAR and extracted at runtime.

### 🐧 [Linux Examples](./Linux/)

- **LeapChatCli**: REPL chat CLI as a native Linux x86_64 executable using Kotlin/Native (`linuxX64`) and `leap-sdk-linuxx64`. The `ai.liquid.leap.nativelibs` Gradle plugin auto-extracts `libinference_engine.so` from the published `:natives@zip` classifier alongside the binary.

### 🪟 [Windows Examples](./Windows/)

- **LeapChatCli**: REPL chat CLI as a native Windows x86_64 `.exe` using Kotlin/Native (`mingwX64`) and `leap-sdk-mingwx64`. Same `ai.liquid.leap.nativelibs` plugin auto-extracts `inference_engine.dll`.

## Quick Start

### iOS

```bash
cd iOS/LeapSloganExample
make setup && make open
```

### Android

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
cd JVM/LeapChatCli   # or Linux/LeapChatCli, Windows/LeapChatCli
./gradlew installDist                       # JVM
./gradlew linkReleaseExecutableLinuxX64     # Linux  (run on a Linux host)
./gradlew linkReleaseExecutableMingwX64     # Windows (run on a Windows host)
```

## What is LeapSDK?

LeapSDK enables running AI models locally across mobile, desktop, and browser using the Liquid Inference Engine. It provides:

- **On-device inference** - No internet required
- **Real-time streaming** - Token-by-token response generation
- **Cross-platform** - iOS, macOS, Android, Web (Kotlin/Wasm), JVM, Linux, and Windows
- **High performance** - Optimized for mobile and desktop hardware
- **Easy integration** - Simple API for chat and text generation

## Documentation

- 📚 [iOS Quick Start Guide](https://leap.liquid.ai/docs/edge-sdk/ios/ios-quick-start-guide)
- 📚 [Android Quick Start Guide](https://leap.liquid.ai/docs/edge-sdk/android/android-quick-start-guide)
- 🔗 [iOS SDK Repository](https://github.com/Liquid4All/leap-ios)
- 🔗 [Android SDK Repository](https://github.com/Liquid4All/leap-android-sdk)

## License

See [LICENSE](./LICENSE) for details.
