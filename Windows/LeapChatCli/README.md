# LeapChatCli — Windows

A REPL-style multi-turn chat CLI built as a native Windows executable using
Kotlin/Native (`mingwX64` target). Loads a local LeapSDK model bundle and
streams responses to stdout. No JVM at runtime.

## Prerequisites

- Windows x86_64 host (Windows 10/11 tested; or build via WSL2 / Linux host
  with mingw cross-toolchain)
- JDK 21 to run Gradle (Zulu recommended)
- A LeapSDK model bundle on local disk (e.g. `LFM2-1.2B-Q4_0.bundle`)

> **Note on cross-compile**: Kotlin/Native can compile `mingwX64` Kotlin code
> from any host (verified on macOS), but cross-linking the final `.exe`
> against `inference_engine.dll` from non-Windows hosts isn't supported by
> Kotlin/Native. Build + run from Windows.

## How the native libraries get there

The `ai.liquid.leap.nativelibs` Gradle plugin (declared in `build.gradle.kts`)
auto-resolves `ai.liquid.leap:leap-sdk-mingwx64:0.10.1:natives@zip` from
Maven and extracts:

- `inference_engine.dll`
- `libinference_engine_llamacpp_backend.dll`
- `ie_zip.dll`

into `build/bin/mingwX64/releaseExecutable/` so standard Windows
DLL co-location loads them at runtime when you launch the `.exe`. No
`installVendor`, no S3, no manual binary drops.

## Build

```powershell
$env:JAVA_HOME = "C:\Path\To\jdk-21"
.\gradlew.bat linkReleaseExecutableMingwX64
```

Output: `build\bin\mingwX64\releaseExecutable\leap-chat-cli.exe` plus the
three `.dll` files alongside it.

## Run

```powershell
.\build\bin\mingwX64\releaseExecutable\leap-chat-cli.exe C:\path\to\model.bundle
```

Optionally pass a custom system prompt:

```powershell
.\build\bin\mingwX64\releaseExecutable\leap-chat-cli.exe C:\path\to\model.bundle `
  "You are a terse Windows admin. Answer in one sentence."
```

Type a message + Enter to send. EOF (Ctrl-Z + Enter on Windows) or `:quit`
exits.

## How it works

Same `LeapClient.loadModel(modelPath)` + `Conversation.generateResponse(...)`
flow as the [JVM CLI](../../JVM/LeapChatCli/) — a single Kotlin source file
(`src/mingwX64Main/kotlin/ai/liquid/leap/cli/Main.kt`) drives the REPL. The
Kotlin Multiplatform machinery picks the published `leap-sdk-mingwx64`
variant (cinterop bindings to the bare C inference engine API) instead of
the JNI-backed JVM artifact.
