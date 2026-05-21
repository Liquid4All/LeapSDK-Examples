# LeapVLMExample

A SwiftUI demo that runs a vision-language model on-device with LeapSDK. Tap a button and the model describes a bundled image, streaming tokens back to the UI.

## Features

- On-device vision-language inference via `LeapModelDownloader`
- Automatic model fetch + on-disk caching on first launch
- Multimodal `ChatMessage` carrying both an image and a text prompt
- Streamed `MessageResponse` consumption with `onEnum(of:)`
- Image resizing + JPEG encoding before handing pixels to the model

## Model

- **LFM2.5-VL-1.6B** (Q4_0) — a vision-language model resolved via the SDK's manifest.
- Downloaded automatically on first launch through `Leap.shared.load(model:quantization:options:progress:)`. Progress is surfaced to the UI through the `progress` callback.
- Cached under the app's Caches directory at `leap-cache/` (`FileManager.default.urls(for: .cachesDirectory, ...)`). Subsequent launches load from disk and run offline.

## Prerequisites

- Xcode 15+
- iOS 17.0+ deployment target
- [XcodeGen](https://github.com/yonaskolb/XcodeGen): `brew install xcodegen`

## Running

```bash
cd iOS/LeapVLMExample
make setup    # xcodegen generate
make open     # opens LeapVLMExample.xcodeproj in Xcode
# or: make build / make run
```

Then select an iOS 17+ simulator or device and run. On a generic iOS Simulator destination, exclude `x86_64` (the v0.10.8 xcframeworks ship arm64-only simulator slices) — see `iOS/README.md` for the full incantation.

## Project Structure

```
LeapVLMExample/
├── project.yml                       # XcodeGen configuration (SDK pin, linker flags, codesign script)
└── LeapVLMExample/
    ├── LeapVLMExampleApp.swift       # @main App entry
    ├── ContentView.swift             # Image + Describe button + streamed text
    ├── VLMStore.swift                # LeapSDK integration: load model, build multimodal message, stream response
    └── Assets.xcassets/
        └── pug.imageset/             # Bundled sample image
```

`project.yml` pins LeapSDK at `revision: v0.10.8` and depends on the `LeapModelDownloader` product, which re-exports the SDK types this demo uses (`Leap`, `ModelRunner`, `ChatMessage`, `ChatMessageContent`, `LiquidInferenceEngineManifestOptions`). It also wires in the linker flags and a post-build script that re-signs the nested inference-engine dylibs inside `LeapModelDownloader.framework/Frameworks`.

## Key SDK Patterns

### Load a VLM with cached manifest downloading

```swift
let cachePath = FileManager.default
  .urls(for: .cachesDirectory, in: .userDomainMask).first!
  .appendingPathComponent("leap-cache").path
try? FileManager.default.createDirectory(atPath: cachePath, withIntermediateDirectories: true)

let runner = try await Leap.shared.load(
  model: "LFM2.5-VL-1.6B",
  quantization: "Q4_0",
  options: LiquidInferenceEngineManifestOptions(
    cacheOptions: .enabled(path: cachePath)
  ),
  progress: { progress, _ in
    // progress is 0.0 ... 1.0; speed is bytes/sec
  }
)
```

### Build a multimodal message (image + text)

`ChatMessageContent.Image.fromJPEGData(_:)` wraps raw JPEG bytes; multiple `ChatMessageContent` values can be combined in a single user turn:

```swift
let imageContent = ChatMessageContent.Image.fromJPEGData(jpegData)
let message = ChatMessage(
  role: .user,
  content: [imageContent as ChatMessageContent, ChatMessageContent.text("Describe this image.")],
  reasoningContent: nil,
  functionCalls: nil
)
```

### Stream the response

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

`onEnum(of:)` is the SKIE-bridged helper that lets Swift exhaustively pattern-match the sealed `MessageResponse` hierarchy.

## Notes / Troubleshooting

- The bundled image (`pug`) is loaded via `UIImage(named:)`, resized to fit a 512×512 box, then JPEG-encoded at quality 0.9 before being passed to the model. Send your own image by replacing `pug.imageset` or swapping the asset name in `VLMStore.describeImage()`.
- On first launch the UI sits on "Downloading: X%" while the manifest pulls the weights. Subsequent launches go straight to "Model ready".
- iOS Simulator builds may need `EXCLUDED_ARCHS=x86_64` — see the root `iOS/README.md` troubleshooting section.
