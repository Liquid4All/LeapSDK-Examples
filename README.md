# LeapSDK Examples

This repository contains example applications demonstrating how to use the LeapSDK for on-device AI inference across multiple platforms.

## Platforms

### 📱 [iOS Examples](./iOS/)
- **LeapSloganExample**: Simple SwiftUI app for slogan generation
- **LeapChatExample**: Comprehensive chat application with real-time streaming

Swift Package Manager integration with XcodeGen project generation.

### 🤖 [Android Examples](./Android/)
- **SloganApp**: Basic slogan generator using Jetpack Compose
- **LeapChat**: Full-featured chat application with modern Android UI
- **ShareAI**: Web page summary generator

Gradle-based projects using the LeapSDK Android library.

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

## What is LeapSDK?

LeapSDK enables running AI models locally on mobile devices using the Liquid Inference Engine. It provides:

- **On-device inference** - No internet required
- **Real-time streaming** - Token-by-token response generation  
- **Cross-platform** - iOS and Android support
- **High performance** - Optimized for mobile hardware
- **Easy integration** - Simple API for chat and text generation

## Documentation

- 📚 [iOS Quick Start Guide](https://leap.liquid.ai/docs/ios-quick-start-guide)
- 📚 [Android Quick Start Guide](https://leap.liquid.ai/docs/android-quick-start-guide)
- 🔗 [iOS SDK Repository](https://github.com/Liquid4All/leap-ios)
- 🔗 [Android SDK Repository](https://github.com/Liquid4All/leap-android-sdk)

## License

See [LICENSE](./LICENSE) for details.
