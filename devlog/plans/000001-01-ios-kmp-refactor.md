## Context

The iOS examples need updated XCFrameworks from the leap-android-sdk KMP repo, and the refactor needs finishing: both wiring up the Swift macro package in `Package.swift` and updating example app API usage to match the latest SDK surface. The `iOS/Sources/` directory with macro code is currently untracked.

## Plan

### 1. Build XCFrameworks from leap-android-sdk
- Build from `~/development/leap-android-sdk` (current state, no changes needed)
- Artifacts at `leap_sdk/build/XCFrameworks/release/LeapSDK.xcframework` and `leap_sdk_model_downloader/build/XCFrameworks/release/LeapModelDownloader.xcframework`

### 2. Copy updated XCFrameworks into examples repo
- Replace `iOS/LeapSDK.xcframework` and `iOS/LeapModelDownloader.xcframework`

### 3. Wire up macro targets in Package.swift
- Add `SwiftSyntax` dependency (509.0.0+)
- Add `LeapSDKConstrainedGenerationPlugin` macro target
- Add `LeapSDKMacros` library target
- Track `iOS/Sources/` in git

### 4. Update example apps for latest API surface
- Build each example, fix compilation errors against new XCFrameworks
- Key files: AudioDemoStore.swift, ChatStore.swift, SloganStore.swift, SloganGeneratable.swift, GeneratorViewModel.swift, Recipe.swift, project.yml files

### 5. Verify and PR
- All four examples compile successfully
- Create draft PR
