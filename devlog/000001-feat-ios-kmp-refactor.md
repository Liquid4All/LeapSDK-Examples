# feat/ios-kmp-refactor

## Agent
Claude (claude-opus-4-6) @ repository:LeapSDK-Examples branch:feat/ios-kmp-refactor

## Intent
Update XCFrameworks from the leap-android-sdk KMP repo and finish the iOS refactor: wire up Swift macro package in Package.swift and update example app API usage to match the latest SDK surface.

## What Changed
- 2026-02-18 iOS/Package.swift — Added SwiftSyntax dependency, macro plugin target, and LeapSDKMacros library target
- 2026-02-18 iOS/Sources/LeapSDKMacros/ — Created GeneratableType protocol, convenience extensions, and macro declarations (moved from old pure-Swift SDK)
- 2026-02-18 iOS/Sources/LeapSDKConstrainedGenerationPlugin/ — Updated macro to reference LeapSDKMacros.GeneratableType instead of LeapSDK.GeneratableType
- 2026-02-18 iOS/LeapChatExample — Updated for KMP API: `Leap.shared.load()`, `onEnum(of:)`, `ChatMessage_withArray`, `completion.fullMessage`, `progress.doubleValue`
- 2026-02-18 iOS/LeapAudioDemo — Updated for KMP API: `ChatMessage.Role`, convenience inits, `fromFloatSamples` extension, `generateResponse(message:generationOptions:)`, exhaustive `GenerationFinishReason` switch
- 2026-02-18 iOS/LeapSloganExample — Updated for KMP API: `GenerationOptions()` convenience init, `onEnum(of:)`, added `LeapSDKMacros` dep for constrained generation
- 2026-02-18 iOS/RecipeGenerator — Updated for KMP API: `Leap.shared.load(options:)`, `GenerationOptions()`, `onEnum(of:)`, added `LeapSDKMacros` dep
- 2026-02-18 leap-android-sdk ConvenienceExtensions.swift — Added SDK-level Swift convenience extensions (bundled via SKIE): `GenerationOptions()`, `LiquidInferenceEngineManifestOptions(contextSize:...)`, `ChatMessageContent.fromFloatSamples`, `KotlinByteArray.toData()`, `KotlinFloatArray.toFloatArray()`

## Decisions
- 2026-02-18 Put convenience wrappers in SDK (not examples) — so all consumers benefit from Swift-friendly APIs without depending on LeapSDKMacros
- 2026-02-18 Use SKIE `#if canImport(UIKit)` pattern — ChatMessageContentExtensions.swift already uses this; followed same pattern for platform-conditional code
- 2026-02-18 Use `.init()` syntax to disambiguate ChatMessage — KMP generates both a class init and a free function with identical `(role:content:)` signature; `.init()` forces class initializer resolution
- 2026-02-18 Removed duplicate ChatMessage array wrapper from ConvenienceExtensions — `ChatMessage_withArray` already exists in iosMain; adding `ChatMessage(role:content:[array])` caused ambiguity with single-content overload
- 2026-02-18 GeneratableType protocol defined in LeapSDKMacros — not in KMP SDK; was in old pure-Swift SDK; needed for `@Generatable` macro

## Issues
- ChatMessage ambiguity between class init and Kotlin free function — both have `(role:content: ChatMessageContent)` signature. Fixed with `.init()` syntax.
- `ChatMessage_withArray` only in iosMain (not appleMain) — uses UIKit-dependent file. Can't move to appleMain without Kotlin changes. Used `ChatMessage_withArray` directly in examples.
- LiquidInferenceEngineManifestOptions convenience init not found until derived data cleared — Xcode caches stale module interfaces. Always clean DerivedData after replacing XCFrameworks.

## Commits

## Progress
- [x] Build XCFrameworks from leap-android-sdk (current state)
- [x] Copy updated XCFrameworks into iOS/
- [x] Wire up macro targets in Package.swift (Sources/)
- [x] Update example apps for latest API surface
- [x] Verify all four examples compile (Swift compilation passes, linker errors expected without native libs)
- [ ] Create draft PR
