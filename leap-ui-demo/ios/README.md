# LeapVoiceAssistantDemo — iOS

A SwiftUI iOS app that shows `VoiceAssistantWidget` (from `leap-ui`) full-screen on a black
background with real on-device voice inference (LFM2.5-Audio-1.5B). Press and hold to speak;
the model responds with interleaved text and audio.

## Prerequisites

- iOS 16+, Xcode 15+
- [xcodegen](https://github.com/yonaskolb/XcodeGen) (`brew install xcodegen`)
- Gradle 9+ with JDK 21 (for building the XCFrameworks)

## Setup

### 1. Build the XCFrameworks

From the repository root:

```bash
export JAVA_HOME="$(/usr/libexec/java_home -v 21)"
./gradlew installVendor
./gradlew :leap-ui:assembleLeapUiXCFramework
./gradlew :leap-sdk:assembleLeapSDKXCFramework
```

This produces:
- `leap-ui/build/XCFrameworks/release/LeapUi.xcframework` — UI widget
- `leap-sdk/build/XCFrameworks/release/LeapSDK.xcframework` — inference engine + Swift API

Both are referenced at those relative paths from the Xcode project.

### 2. Generate the Xcode project

The repository stores `project.yml` (the xcodegen spec) rather than the generated
`LeapVoiceAssistantDemo.xcodeproj`. Run xcodegen once to produce the project file:

```bash
cd leap-ui-demo/ios
xcodegen generate
```

Re-run `xcodegen generate` any time you change `project.yml` (e.g. to add new source files or
update build settings).

### 3. Open in Xcode

```bash
open leap-ui-demo/ios/LeapVoiceAssistantDemo.xcodeproj
```

### 4. Run

Select the **LeapVoiceAssistantDemo** scheme, choose an **iPhone device** (ARM64) or **iOS Simulator on Apple
Silicon**, and press **Run** (⌘R). The app will download LFM2.5-Audio-1.5B on first launch
(~800 MB); subsequent launches use the cached model.

> **Note:** The XCFrameworks are ARM64-only. Intel Mac simulators (x86_64) are not supported.
> In Xcode target build settings, add `EXCLUDED_ARCHS = x86_64`.

## Project Structure

```
LeapVoiceAssistantDemo/
  LeapVoiceAssistantDemoApp.swift   — @main App entry point; WindowGroup scene
  ContentView.swift     — UIViewControllerRepresentable wrapping VoiceAssistantViewController;
                          status + stats overlay
  DemoViewModel.swift   — model loading, recording, generation, amplitude frame tick
  AudioPipeline.swift   — AudioRecorder (AVAudioEngine) + AudioPlayer (AVAudioPlayerNode)
  Assets.xcassets/
```

## Info.plist requirements

| Key | Value | Reason |
|---|---|---|
| `NSMicrophoneUsageDescription` | (present) | Required for AVAudioEngine microphone access |
| `CADisableMinimumFrameDurationOnPhone` | `true` | Enables ProMotion on high-refresh-rate iPhones (required by CMP since 1.9) |
| `UIApplicationSceneManifest` | dict | SwiftUI `@main` App lifecycle requires scene manifest |

## Framework integration notes

The demo links two XCFrameworks:

| Framework | Contents | Import |
|---|---|---|
| `LeapUi.xcframework` | `VoiceAssistantStateHolder`, `VoiceAssistantWidget`, widget colors/labels | `import LeapUi` |
| `LeapSDK.xcframework` | `Leap`, `Conversation`, `ChatMessageContent`, `MessageResponse`, Swift convenience extensions | `import LeapSDK` |

Key Swift symbols used:

| Swift symbol | Origin |
|---|---|
| `VoiceAssistantState(mode:amplitude:)` | `LeapUi` — immutable widget state snapshot |
| `VoiceAssistantStateHolder(initial:)` | `LeapUi` — observable bridge for Compose recomposition |
| `VoiceAssistantViewControllerKt.VoiceAssistantViewController(…)` | `LeapUi` — Compose ViewController |
| `Leap.shared.load(model:quantization:progress:)` | `LeapSDK` — downloads and loads the model |
| `runner.createConversation(systemPrompt:)` | `LeapSDK` — creates a multi-turn `Conversation` |
| `conversation.generateResponse(message:generationOptions:)` | `LeapSDK` — streaming inference |
| `ChatMessageContent.fromFloatSamples(_:sampleRate:)` | `LeapSDK` — float PCM → Audio content |
| `MessageResponseAudioSample` | `LeapSDK` — streaming audio chunk |
| `MessageResponseComplete` | `LeapSDK` — generation complete with stats |

> **Note:** Kotlin enum entries are lowercased in Swift ObjC interop:
> `VoiceWidgetMode.IDLE` → `.idle`, `VoiceWidgetMode.LISTENING` → `.listening`, etc.
