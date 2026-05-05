# LeapChatCli — Linux

A REPL-style multi-turn chat CLI built as a native Linux executable using
Kotlin/Native (`linuxX64` target). Loads a local LeapSDK model bundle and
streams responses to stdout. No JVM at runtime.

## Prerequisites

- Linux x86_64 host (Ubuntu 22.04+ tested)
- JDK 21 to run Gradle (Zulu recommended)
- Internet access on first run (the demo downloads `LFM2-350M` Q8_0 — about
  370 MB — into `./leap_models/` and reuses the cache afterwards)

> **Note on cross-compile**: Kotlin/Native can cross-compile `linuxX64` Kotlin
> code from macOS, but cross-linking against the shared `libinference_engine.so`
> from macOS isn't supported (lld rejects `-Wl,-rpath,$ORIGIN` cinterop syntax,
> and the natives directory isn't wired into the linker search path in
> cross-host mode). Build + run from a Linux host.

## How the native libraries get there

The `ai.liquid.leap.nativelibs` Gradle plugin (declared in `build.gradle.kts`)
auto-resolves `ai.liquid.leap:leap-sdk-linuxx64:0.10.1:natives@zip` from
Maven and extracts:

- `libinference_engine.so`
- `libinference_engine_llamacpp_backend.so`
- `libie_zip.so`

into `build/bin/linuxX64/releaseExecutable/` so the cinterop `$ORIGIN` rpath
finds them at runtime. No `installVendor`, no S3, no manual binary drops.

## Build

```bash
# Set JAVA_HOME to your JDK 21 install. On Debian/Ubuntu after installing
# `openjdk-21-jdk` it lives at /usr/lib/jvm/java-21-openjdk-amd64/. With
# SDKMAN it's $HOME/.sdkman/candidates/java/current/. Pick whichever fits.
export JAVA_HOME=/usr/lib/jvm/java-21-openjdk-amd64
./gradlew linkReleaseExecutableLinuxX64
```

Output: `build/bin/linuxX64/releaseExecutable/leap-chat-cli.kexe` plus the
three `.so` files alongside it.

## Run

```bash
build/bin/linuxX64/releaseExecutable/leap-chat-cli.kexe
```

The first run streams a `Downloading: NN% (M / N MB)` line until the model
lands on disk, then drops you into the REPL. Type a message + Enter to send.
EOF (Ctrl-D) or `:quit` exits. Subsequent launches reuse the cached model.

To use a different model or quantization, change the `MODEL_NAME` and
`QUANTIZATION_SLUG` constants at the top of `Main.kt` and rebuild.

## How it works

Same `LeapDownloader().loadModel(...)` + `Conversation.generateResponse(...)`
flow as the [JVM CLI](../../JVM/LeapChatCli/) and the [Web demo](../../Web/LeapVoiceAssistantDemo/) — a single Kotlin source file
(`src/linuxX64Main/kotlin/ai/liquid/leap/cli/Main.kt`) drives the REPL. The
Kotlin Multiplatform machinery picks the published `leap-sdk-linuxx64`
variant (cinterop bindings to the bare C inference engine API) instead of
the JNI-backed JVM artifact.
