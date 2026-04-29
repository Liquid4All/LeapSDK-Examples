# LeapVoiceAssistantDemo — macOS

A SwiftUI macOS app that shows `VoiceAssistantWidget` (from `leap-ui`) full-screen on a black
background with real on-device voice inference (LFM2.5-Audio-1.5B). Press and hold to speak;
the model responds with interleaved text and audio.

## Prerequisites

- macOS 15+, Xcode 15+
- [xcodegen](https://github.com/yonaskolb/XcodeGen) (`brew install xcodegen`)

`LeapSDK.xcframework` and `LeapUi.xcframework` are pulled from the
[`leap-sdk`](https://github.com/Liquid4All/leap-sdk) Swift Package (see `project.yml` →
`packages.LeapSDK.exactVersion`); no local Gradle build is required.

## Setup

### 1. Generate the Xcode project

The repository stores `project.yml` (the xcodegen spec) rather than the generated
`LeapVoiceAssistantDemo.xcodeproj`. Run xcodegen once to produce the project file:

```bash
cd macOS/LeapVoiceAssistantDemo
xcodegen generate
```

Re-run `xcodegen generate` any time you change `project.yml` (e.g. to add new source files or
update build settings).

### 2. Open in Xcode

```bash
open macOS/LeapVoiceAssistantDemo/LeapVoiceAssistantDemo.xcodeproj
```

### 3. Run

Select the **LeapVoiceAssistantDemo** scheme, set destination to **My Mac**, and press **Run** (⌘R). The app
will download LFM2.5-Audio-1.5B on first launch (~800 MB); subsequent launches use the cached
model.

macOS will prompt for microphone permission (`NSMicrophoneUsageDescription`) on first
recording attempt.

## Project Structure

```
LeapVoiceAssistantDemo/
  LeapVoiceAssistantDemoApp.swift   — @main App entry point; single Window scene
  ContentView.swift     — NSViewControllerRepresentable wrapping VoiceAssistantNSViewController;
                          status + stats overlay
  DemoViewModel.swift   — model loading, recording, generation, amplitude frame tick
  AudioPipeline.swift   — AudioRecorder (AVAudioEngine) + AudioPlayer (AVAudioPlayerNode)
  Assets.xcassets/
```

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
| `VoiceAssistantNSViewControllerKt.VoiceAssistantNSViewController(…)` | `LeapUi` — Compose NSViewController |
| `Leap.shared.load(model:quantization:progress:)` | `LeapSDK` — downloads and loads the model |
| `runner.createConversation(systemPrompt:)` | `LeapSDK` — creates a multi-turn `Conversation` |
| `conversation.generateResponse(message:generationOptions:)` | `LeapSDK` — streaming inference |
| `ChatMessageContent.fromFloatSamples(_:sampleRate:)` | `LeapSDK` — float PCM → Audio content |
| `MessageResponseAudioSample` | `LeapSDK` — streaming audio chunk |
| `MessageResponseComplete` | `LeapSDK` — generation complete with stats |

> **Note:** Kotlin enum entries are lowercased in Swift ObjC interop:
> `VoiceWidgetMode.IDLE` → `.idle`, `VoiceWidgetMode.LISTENING` → `.listening`, etc.
> If Xcode can't resolve these, open the generated `LeapUi.framework/Headers/LeapUi.h` in the
> XCFramework and check the exact Swift-visible names for your toolchain.
