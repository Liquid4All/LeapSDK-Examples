# LeapVLMExample (macOS)

A minimal SwiftUI macOS app that loads a vision-language model on-device and describes a
bundled image (a pug) when the user taps **Describe**. Demonstrates how to attach a JPEG to a
`ChatMessage` and stream the model's response token-by-token.

## Features

- On-device VLM inference via `LeapSDK` (`Leap.shared.load`)
- Automatic model download with progress reporting and on-disk caching
- Multimodal `ChatMessage` containing both an image (`ChatMessageContent.Image.fromJPEGData`)
  and a text prompt
- Streaming response handled with `onEnum(of:)` over
  `conversation.generateResponse(message:)`
- macOS image pipeline: load `NSImage` from asset catalog, resize, and re-encode to JPEG via
  `NSBitmapImageRep`

## Model

- **LFM2.5-VL-1.6B** (`Q4_0`) — downloaded on first launch by `Leap.shared.load` and cached
  under `~/Library/Caches/.../leap-cache`. Subsequent launches reuse the cached weights.

## Prerequisites

- Xcode 15+
- macOS 15.0+ deployment target
- XcodeGen (`brew install xcodegen`)

`LeapSDK` and `LeapModelDownloader` are pulled from the
[`leap-sdk`](https://github.com/Liquid4All/leap-sdk) Swift Package (see `project.yml` →
`packages.LeapSDK.revision`, currently pinned to `v0.10.8`).

## Running

```bash
cd macOS/LeapVLMExample
xcodegen generate
open LeapVLMExample.xcodeproj
```

In Xcode, select the **LeapVLMExample** scheme, set destination to **My Mac**, and press
**Run** (⌘R). The first launch downloads ~1 GB of weights; subsequent launches use the
cached model.

Re-run `xcodegen generate` any time you change `project.yml`.

## Project Structure

```
LeapVLMExample/
  project.yml                         — XcodeGen spec (SDK pin, deployment target, signing)
  LeapVLMExample/
    LeapVLMExampleApp.swift           — @main App entry point; single WindowGroup
    ContentView.swift                 — SwiftUI view: image, Describe button, status, output
    VLMStore.swift                    — @Observable store: model load, image prep, streaming
    Assets.xcassets/                  — Includes the `pug` image used as the VLM input
```

## Key SDK Patterns

### Loading the VLM with download progress

```swift
let runner = try await Leap.shared.load(
  model: "LFM2.5-VL-1.6B",
  quantization: "Q4_0",
  options: LiquidInferenceEngineManifestOptions(
    cacheOptions: .enabled(path: cachePath)
  ),
  progress: { progress, speed in
    // progress is 0.0...1.0 during download
  }
)
```

### Building a multimodal `ChatMessage`

`ChatMessageContent.Image.fromJPEGData` constructs the image content; the message's
`content` array carries both the image and the text prompt in a single user turn.

```swift
let imageContent = ChatMessageContent.Image.fromJPEGData(jpegData)
let message = ChatMessage(
  role: .user,
  content: [imageContent as ChatMessageContent, ChatMessageContent.text("Describe this image.")],
  reasoningContent: nil,
  functionCalls: nil
)
```

### Streaming the response

The runner returns a `Conversation`; `generateResponse(message:)` yields an async sequence
of typed responses. Use `onEnum(of:)` to switch over the chunk / complete cases.

```swift
let conversation = runner.createConversation(systemPrompt: nil)

for try await resp in conversation.generateResponse(message: message) {
  switch onEnum(of: resp) {
  case .chunk(let chunk):
    generatedText.append(chunk.text)
  case .complete:
    isGenerating = false
  default:
    break
  }
}
```

## Notes

- **NSImage → JPEG.** The VLM expects JPEG bytes. `VLMStore.resizedJPEGData(forAsset:maxSize:)`
  loads the asset as an `NSImage`, scales it to fit a 512×512 box (preserving aspect ratio),
  then re-encodes via `NSBitmapImageRep` with `.jpeg` and `compressionFactor: 0.9`.
- **App Sandbox is disabled** (`ENABLE_APP_SANDBOX: NO` in `project.yml`) so the cache
  directory under `~/Library/Caches` is writable without entitlements. If you re-enable the
  sandbox, add the appropriate file-access entitlements before shipping.
- **Ad-hoc signing** (`CODE_SIGN_IDENTITY: "-"`) is used by default — fine for local runs,
  swap in a Developer ID before distributing.
- The bundled `pug` asset is the only input wired up; to try other images, drop them into
  `Assets.xcassets` and change the asset name in `VLMStore.describeImage()`, or extend the
  view with an `NSOpenPanel` file picker.
