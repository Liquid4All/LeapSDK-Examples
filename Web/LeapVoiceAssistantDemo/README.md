# LeapVoiceAssistantDemo — Web

A Kotlin/Wasm Compose-for-Web port of the voice assistant. Shows
`VoiceAssistantWidget` (from `leap-ui`) full-screen on a black background with real
in-browser voice inference (LFM2.5-Audio-1.5B). Press and hold to speak; the model
responds with streaming audio.

## Prerequisites

- JDK 21 (Zulu recommended for Apple Silicon)
- A modern browser with WebAssembly + SharedArrayBuffer support (Chrome 91+, Firefox
  89+, Safari 15.4+)
- Microphone access — the page must be served from `http://localhost` (or HTTPS) for
  `getUserMedia`

No `installVendor` / S3 / sibling-clone setup is needed: the inference engine binaries
(`inference_engine.js`, `inference_engine.wasm`, `inference_engine_worker.js`) ship
inside the published `ai.liquid.leap:leap-sdk-wasm-js:<version>.klib` on Maven
Central. The Gradle build extracts them to `build/wasmStatic/wasm/`, and webpack
copies them into the bundle output via `copy-webpack-plugin`.

## Run the dev server

```bash
JAVA_HOME="$(/usr/libexec/java_home -v 21)" ./gradlew wasmJsBrowserDevelopmentRun
```

Open <http://localhost:8080>. The page sets COOP/COEP headers (configured in
`webpack.config.d/headers.js`) so the cross-origin isolation needed by
`SharedArrayBuffer` is in effect.

`/api/*` requests are proxied to `https://leap.liquid.ai` by
`webpack.config.d/dev-proxy.js` for model manifest resolution — no API key required.

## Build a static bundle

```bash
JAVA_HOME="$(/usr/libexec/java_home -v 21)" ./gradlew wasmJsBrowserDistribution
```

Output: `build/dist/wasmJs/productionExecutable/`. The directory is fully
self-contained (HTML, JS, WASM, inference engine binaries) and can be served by any
static HTTP server that sets the same COOP/COEP headers.

## Project structure

```
Web/LeapVoiceAssistantDemo/
  build.gradle.kts                         — Kotlin Multiplatform (wasmJs target),
                                             jetbrains-compose, copy-webpack-plugin,
                                             extractWasmVendor task
  settings.gradle.kts                      — root project name + repos
  gradle/libs.versions.toml                — Kotlin 2.3.20, Compose MP 1.10.x, ktfmt
  webpack.config.d/
    inference-engine-copy.js               — CopyWebpackPlugin → bundles vendor wasm
    headers.js                             — COOP/COEP for SharedArrayBuffer
    dev-proxy.js                           — /api/* → leap.liquid.ai
  src/wasmJsMain/
    kotlin/ai/liquid/leap/uidemo/
      Main.kt                              — Compose entry, model load, widget wiring
      AudioPipeline.kt                     — getUserMedia + AudioContext I/O bridges
    resources/index.html                   — page shell, loads inference_engine.js
```
