# LeapChatCli — Linux

A REPL-style multi-turn chat CLI built as a native Linux executable using
Kotlin/Native (`linuxX64` target). Loads a local LeapSDK model bundle and
streams responses to stdout. No JVM at runtime.

## Prerequisites

- Linux x86_64 host (Ubuntu 22.04+ tested)
- JDK 21 to run Gradle (Zulu recommended)
- A LeapSDK model bundle on local disk (e.g. `LFM2-1.2B-Q4_0.bundle`)

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
JAVA_HOME="$(/usr/libexec/java_home -v 21)" ./gradlew linkReleaseExecutableLinuxX64
```

Output: `build/bin/linuxX64/releaseExecutable/leap-chat-cli.kexe` plus the
three `.so` files alongside it.

## Run

```bash
build/bin/linuxX64/releaseExecutable/leap-chat-cli.kexe /path/to/model.bundle
```

Optionally pass a custom system prompt:

```bash
build/bin/linuxX64/releaseExecutable/leap-chat-cli.kexe /path/to/model.bundle \
  "You are a terse Linux sysadmin. Answer in one sentence."
```

Type a message + Enter to send. EOF (Ctrl-D) or `:quit` exits.

## How it works

Same `LeapClient.loadModel(modelPath)` + `Conversation.generateResponse(...)`
flow as the [JVM CLI](../../JVM/LeapChatCli/) — a single Kotlin source file
(`src/linuxX64Main/kotlin/ai/liquid/leap/cli/Main.kt`) drives the REPL. The
Kotlin Multiplatform machinery picks the published `leap-sdk-linuxx64`
variant (cinterop bindings to the bare C inference engine API) instead of
the JNI-backed JVM artifact.
