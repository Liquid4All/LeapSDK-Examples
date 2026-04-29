# LeapChatCli — JVM

A REPL-style multi-turn chat CLI on the JVM. Loads a local LeapSDK model
bundle, then streams assistant responses to stdout token-by-token. Cross-OS:
the same artifact runs on Linux, macOS, and Windows because
`leap-sdk-jvm` bundles JNI binaries for all three desktops via
`NativeLibLoader` (extracts the right `.so`/`.dll`/`.dylib` for the host
at runtime).

## Prerequisites

- JDK 21 (Zulu recommended on Apple Silicon)
- A LeapSDK model bundle on local disk (e.g. `LFM2-1.2B-Q4_0.bundle`)

## Build

```bash
JAVA_HOME="$(/usr/libexec/java_home -v 21)" ./gradlew installDist
```

This produces a runnable distribution at
`build/install/leap-chat-cli/bin/leap-chat-cli` (`.bat` on Windows).

## Run

```bash
build/install/leap-chat-cli/bin/leap-chat-cli /path/to/model.bundle
```

Optionally pass a custom system prompt as the second argument:

```bash
build/install/leap-chat-cli/bin/leap-chat-cli /path/to/model.bundle \
  "You are a terse Linux sysadmin. Answer in one sentence."
```

Type a message + Enter to send. Generation streams to stdout token-by-token;
a `[<n> tok, <r> tok/s]` summary lands on stderr after each turn. EOF
(Ctrl-D) or `:quit` exits.

## How it works

```kotlin
val runner = LeapClient.loadModel(modelPath = "/path/to/model.bundle")
val conversation = runner.createConversation(systemPrompt = "…")

conversation.generateResponse("Hello").collect { response ->
  when (response) {
    is MessageResponse.Chunk    -> print(response.text)
    is MessageResponse.Complete -> println()
    else -> Unit
  }
}
```

`Conversation.generateResponse` returns a `Flow<MessageResponse>` whose
`Chunk`s arrive as the model decodes; the terminal `Complete` carries
generation stats. Per-turn history is kept by the `Conversation` object so
follow-up turns include prior context.

See [`Linux/LeapChatCli/`](../../Linux/LeapChatCli/) and
[`Windows/LeapChatCli/`](../../Windows/LeapChatCli/) for Kotlin/Native
ports of the same demo using `linuxX64` / `mingwX64` targets — same SDK
API, no JVM required.
