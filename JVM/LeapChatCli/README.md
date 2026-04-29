# LeapChatCli — JVM

A REPL-style multi-turn chat CLI on the JVM. Loads a local LeapSDK model
bundle, then streams assistant responses to stdout token-by-token. Cross-OS:
the same artifact runs on Linux, macOS, and Windows because
`leap-sdk-jvm` bundles JNI binaries for all three desktops via
`NativeLibLoader` (extracts the right `.so`/`.dll`/`.dylib` for the host
at runtime).

## Prerequisites

- JDK 21 (Zulu recommended on Apple Silicon)
- Internet access on first run (the demo downloads `LFM2-350M` Q8_0 — about
  370 MB — into `./leap_models/` and reuses the cache afterwards)

## Build

```bash
JAVA_HOME="$(/usr/libexec/java_home -v 21)" ./gradlew installDist
```

This produces a runnable distribution at
`build/install/leap-chat-cli/bin/leap-chat-cli` (`.bat` on Windows).

## Run

```bash
build/install/leap-chat-cli/bin/leap-chat-cli
```

The first run streams a `Downloading: NN% (M / N MB)` line until the model
lands on disk, then drops you into the REPL. Type a message + Enter to send.
Generation streams to stdout token-by-token; a `[<n> tok, <r> tok/s]` summary
lands on stderr after each turn. EOF (Ctrl-D) or `:quit` exits. Subsequent
launches reuse the cached model and start the REPL immediately.

To use a different model or quantization, change the `MODEL_NAME` and
`QUANTIZATION_SLUG` constants at the top of `Main.kt` and rebuild.

## How it works

```kotlin
val downloader = LeapDownloader()
val runner = downloader.loadModel(
  modelName = "LFM2-350M",
  quantizationSlug = "Q8_0",
  progress = { pd -> /* bytes / total → percent for the progress line */ },
)

val conversation = runner.createConversation(systemPrompt = "…")
conversation.generateResponse("Hello").collect { response ->
  when (response) {
    is MessageResponse.Chunk    -> print(response.text)
    is MessageResponse.Complete -> println()
    else -> Unit
  }
}
```

`LeapDownloader` (from `ai.liquid.leap.manifest`, in the common SDK module —
same one the Web demo uses) resolves the manifest from `leap.liquid.ai`,
downloads the model bundle to `./leap_models/<name>-<quant>/`, and returns a
ready `ModelRunner`. `Conversation.generateResponse` returns a
`Flow<MessageResponse>` whose `Chunk`s arrive as the model decodes; the
terminal `Complete` carries generation stats. Per-turn history is kept by
the `Conversation` object so follow-up turns include prior context.

See [`Linux/LeapChatCli/`](../../Linux/LeapChatCli/) and
[`Windows/LeapChatCli/`](../../Windows/LeapChatCli/) for Kotlin/Native
ports of the same demo using `linuxX64` / `mingwX64` targets — same SDK
API, no JVM required.
