# feat/ios-sdk-v0.10.0

**Agent:** Claude Sonnet 4.6 (claude-sonnet-4-6) @ LeapSDK-Examples branch feat/ios-sdk-v0.10.0

## Intent

Update all iOS examples to use the published `leap-sdk` v0.10.0-SNAPSHOT instead of local XCFramework paths. Fix API breakages introduced by the KMP/SKIE build.

## Progress

- [x] Step 1: Update all 4 `project.yml` files to remote `leap-sdk` URL
- [x] Step 2: Delete `iOS/Package.swift`
- [x] Step 3: Fix API — `Leap.shared.load`, `onEnum(of:)`, `ChatMessageContent.text(...)`, single-content `ChatMessage`, `ChatMessage.Role`
- [x] Step 4: Fix known API breakages — all 4 store files rewritten
- [x] Step 5: Build & iterate — all 4 examples build for `arm64` simulator
- [x] Step 6: Update `iOS/README.md`

## What Changed

2026-03-10T07:42-0700 `iOS/LeapSloganExample/project.yml` — switched from `path: ..` local to `url:` + `exactVersion: 0.10.0-SNAPSHOT`, added `LeapModelDownloader` + `LeapSDKMacros` products, added linker flags for inference engine dylibs

2026-03-10T07:42-0700 `iOS/LeapChatExample/project.yml` — same package config + linker flags

2026-03-10T07:42-0700 `iOS/RecipeGenerator/project.yml` — same package config + linker flags

2026-03-10T07:42-0700 `iOS/LeapAudioDemo/project.yml` — same package config + linker flags

2026-03-10T07:42-0700 `iOS/Package.swift` — deleted (local xcframework dev setup, no longer needed)

2026-03-10T07:42-0700 `iOS/LeapChatExample/LeapChatExample/ChatStore.swift` — full rewrite for KMP API: `Leap.shared.load(model:quantization:options:progress:)`, `(any ModelRunner)?`/`(any Conversation)?`, `ChatMessageContent.text(...)`, `ChatMessage_withArray(role:content:)`, `onEnum(of:)` switch pattern, `completion.fullMessage`

2026-03-10T07:42-0700 `iOS/RecipeGenerator/RecipeGenerator/GeneratorViewModel.swift` — full rewrite for KMP API: same patterns as ChatStore

2026-03-10T07:42-0700 `iOS/LeapAudioDemo/LeapAudioDemo/AudioDemoStore.swift` — full rewrite for KMP API: `Leap.shared.load`, `onEnum(of:)` for all event switches, `MessageResponseComplete.fullMessage`, `audioSample.samples.toFloatArray()`, `audioContent.data.toData()`, `GenerationFinishReason` with `default:` case, `ChatMessage.Role` (not `ChatMessageRole`)

2026-03-10T07:42-0700 `iOS/LeapSloganExample/LeapSloganExample/SloganStore.swift` — full rewrite for KMP API: `Leap.shared.load`, `(any ModelRunner)?`/`(any Conversation)?`, `ChatMessageContent.text(...)`, `options.jsonSchemaConstraint = SloganResponse.jsonSchema()`, raw Kotlin flow bridged via `rawFlow as! SkieSwiftFlow<any MessageResponse>` for constrained generation, `onEnum(of:)` switch

2026-03-10T07:42-0700 `iOS/LeapSloganExample/LeapSloganExample/SloganGeneratable.swift` — added `import LeapSDKMacros`

2026-03-10T07:42-0700 `iOS/README.md` — updated: `leap-ios` → `leap-sdk`, v0.9.2 → v0.10.0-SNAPSHOT, new API snippets, `EXCLUDED_ARCHS=x86_64` note for simulator builds, macro trust note, constrained generation with `jsonSchemaConstraint`

## Decisions

2026-03-10T07:42-0700 Build for `arm64` simulator only (`EXCLUDED_ARCHS=x86_64`) — the v0.10.0-SNAPSHOT xcframeworks include only `arm64-simulator` slice; `x86_64` slice is absent causing the SKIE Swift extensions to be skipped (compiler falls back to raw ObjC bridge). Building with `EXCLUDED_ARCHS=x86_64` resolves this.

2026-03-10T07:42-0700 `generateResponse(message:generationOptions:)` bridging — this overload returns `any Kotlinx_coroutines_coreFlow` (raw Kotlin flow, not SKIE-wrapped). For constrained generation in SloganStore, bridge via `rawFlow as! SkieSwiftFlow<any MessageResponse>`. The underlying object IS a `SkieKotlinFlow` which bridges to `SkieSwiftFlow` via `_ObjectiveCBridgeable`.

2026-03-10T07:42-0700 No `LeapModelDownloader` import needed — `import LeapSDK` is sufficient; the `LeapModelDownloader` product includes `LeapSDK` transitively, and both are linked automatically.

2026-03-10T07:42-0700 `ChatMessage.Role` not `ChatMessageRole` — the KMP SDK uses `ChatMessage.Role` (a SKIE `@frozen enum __Bridge__ChatMessage_Role` aliased as `ChatMessage.Role`). No top-level `ChatMessageRole` type exists.

## Issues

**x86_64 simulator architecture**: When building for `generic/platform=iOS Simulator`, Xcode compiles for both `arm64` and `x86_64`. The xcframework only has `arm64-simulator`, so `x86_64` builds fall back to the raw ObjC bridge — SKIE Swift extensions (`onEnum`, `ChatMessageContent.text`, etc.) are unavailable, causing dozens of compile errors. Fix: `EXCLUDED_ARCHS=x86_64`.

**Macro trust**: LeapSDKConstrainedGenerationPlugin macro from the `LeapSDKMacros` product requires explicit trust via Xcode UI or `defaults write com.apple.dt.Xcode IDESkipMacroFingerprintValidation -bool YES`.

**Device slice linker issue (known SDK bug)**: The `ios-arm64` slice of `libinference_engine.dylib` exports zero symbols. Workaround: build for simulator. The linker flags `OTHER_LDFLAGS` / `LD_RUNPATH_SEARCH_PATHS` are still needed for the simulator slice to link correctly.

**`Resources/` directory missing in RecipeGenerator**: xcodegen failed because `project.yml` referenced a `Resources` source directory that didn't exist. Created empty `iOS/RecipeGenerator/Resources/` directory.

## Research & Discoveries

- `Leap.shared` is the singleton access (NOT `Leap.load(...)` static). The Swift extension adds `load(model:quantization:options:progress:)` as an INSTANCE method on `Leap`.
- SKIE wraps `generateResponse(message:)` → `SkieSwiftFlow<any MessageResponse>`, but NOT `generateResponse(message:generationOptions:)` (raw Kotlin flow).
- `GenerationOptions.jsonSchemaConstraint: String?` is the property to set for constrained generation. `@Generatable` macro generates `static func jsonSchema() -> String`.
- `ChatMessageContent.fromFloatSamples` returns `ChatMessageContent.Audio` (a subtype of `ChatMessageContent` in the ObjC class hierarchy — it extends `LSDKChatMessageContent`), so it can be passed directly to `ChatMessage(role:content:)`.
- `ChatMessage.Role` is the correct SKIE-bridged type (a `@frozen enum` aliased inside `ChatMessage`).

## Commits

