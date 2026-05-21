# LeapKoogAgent Android Example

An Android demo that wires the [Koog](https://docs.koog.ai) agent framework on top of the Leap SDK, running a tool-using LLM agent entirely on-device. Two sample agents are included: a calculator and a weather forecaster (backed by the Open-Meteo public API).

The Leap ↔ Koog bridge is provided by the `koog-edge` AAR (vendored at `app/libs/koog-edge-0.0.1.aar`), which exposes Koog `LLMClient` / `PromptExecutor` interfaces backed by a Leap `ModelRunner`. Koog handles strategy graphs, tool invocation, and event handling; Leap handles inference.

## Features

- **Koog agent strategy graphs** — Calculator uses a single-tool-call loop (`nodeLLMRequest` → `nodeExecuteTool` → `nodeLLMSendToolResult`); Weather uses parallel multi-tool calls (`nodeLLMRequestMultiple` → `nodeExecuteMultipleTools` → `nodeLLMSendMultipleToolResults`)
- **Tool registration** via `ToolRegistry { tool(...) }`, with `@Serializable` `Args` / `Result` types and `@LLMDescription` annotations so Koog can generate schemas for the model
- **Custom Leap-backed `LLMClient`** — `getLeapLLMClient(modelsPath)` from `koog-edge` returns a Koog `LLMClient` that proxies to a Leap `ModelRunner` loaded from a local bundle
- **Streaming Compose UI** with per-tool screens, tool-call event log, and a navigation service
- **MVI view models** with `StateFlow` state and a sealed `Event` interface

## Model

- **LFM2-1.2B-Tool** (referenced as `LeapModels.Chat.LFM2_1_2B_Tool` in `koog-edge`) — a 1.2 B parameter LFM2 variant trained for tool / function calling.
- **Manual bundle push required.** Unlike the other Android demos in this repo, LeapKoogAgent does NOT use `LeapModelDownloader`. The `koog-edge` bridge loads the model from a fixed on-device directory — `/tmp/models` — defined in [`agents/common/Common.kt`](app/src/main/kotlin/ai/liquid/koogleapsdk/agents/common/Common.kt).

Push the bundle once before running:

```bash
adb push lfm2-1.2b-tool.bundle /tmp/models/
```

The exact filename `koog-edge` expects is the one referenced by `LeapModels.Chat.LFM2_1_2B_Tool` — keep the file name as `lfm2-1.2b-tool.bundle` to match.

## Prerequisites

- Android Studio Hedgehog (2023.1.1) or newer
- JDK 17 (the app module uses `jvmToolchain(17)`; JDK 21 via SDKman also works for the outer Gradle build: `JAVA_HOME=~/.sdkman/candidates/java/21.0.9-zulu`)
- Android device or emulator running API 31 (Android 12) or higher
- `adb` on PATH for the model push
- Network access on the device (the weather agent calls the Open-Meteo public API; the app declares `INTERNET`)
- The `koog-edge-0.0.1.aar` file at `app/libs/`. The Gradle dependency declares `implementation(files("libs/koog-edge-0.0.1.aar"))`, so place the AAR there before building.

## Running

```bash
# 1. push the model bundle once
adb push lfm2-1.2b-tool.bundle /tmp/models/

# 2. install + launch
./gradlew installDebug
```

From the tools list screen pick **Calculator** or **Weather Forecast**, type a query, and watch the agent reason and invoke tools.

## Project Structure

```
app/src/main/kotlin/ai/liquid/koogleapsdk/
├── App.kt                                 # Application singleton + context provider
├── MainActivity.kt                        # Compose entry point
├── agents/
│   ├── common/
│   │   ├── AgentProvider.kt               # Interface for agent factories
│   │   ├── Common.kt                      # modelsPath = "/tmp/models"
│   │   └── ExitTool.kt                    # Shared "exit conversation" tool
│   ├── calculator/
│   │   ├── CalculatorAgentProvider.kt     # Koog strategy + LeapLLM wiring
│   │   └── CalculatorTools.kt             # Plus / Minus / Multiply / Divide tools
│   └── weather/
│       ├── WeatherAgentProvider.kt        # Multi-tool parallel strategy
│       ├── WeatherTools.kt                # current_datetime, add_datetime, weather_forecast
│       └── OpenMeteoClient.kt             # HTTP client for open-meteo.com
└── ui/
    ├── navigation/                        # In-app navigation service
    ├── common/MviViewModel.kt             # State + Event base class
    └── screen/
        ├── MainScreen.kt
        ├── toolsList/                     # Agent picker
        ├── calculatorTool/                # Calculator agent UI
        └── weatherTool/                   # Weather agent UI
```

## Key SDK Patterns

### Wiring Leap as Koog's LLM backend

`koog-edge` exposes a `getLeapLLMClient(modelsPath)` factory that loads a Leap `ModelRunner` from a local bundle directory and adapts it to Koog's `LLMClient` interface. Wrap it in a `SingleLLMPromptExecutor` and Koog drives the rest:

```kotlin
import io.github.lemcoder.koog.edge.leap.LeapLLMParams
import io.github.lemcoder.koog.edge.leap.LeapModels
import io.github.lemcoder.koog.edge.leap.getLeapLLMClient
import ai.koog.prompt.executor.llms.SingleLLMPromptExecutor

val leapExecutor = SingleLLMPromptExecutor(getLeapLLMClient(modelsPath))
// modelsPath = "/tmp/models" — koog-edge resolves the bundle for
// LeapModels.Chat.LFM2_1_2B_Tool inside that directory.
```

### Registering tools with `@LLMDescription`

Each tool is a Koog `Tool<Args, Result>` with `@Serializable` arg/result types. `@LLMDescription` annotations are surfaced to the model in the auto-generated schema:

```kotlin
abstract class CalculatorTool(
    override val name: String,
    override val description: String,
) : Tool<CalculatorTool.Args, CalculatorTool.Result>() {
    @Serializable
    data class Args(
        @property:LLMDescription("First number") val a: Float,
        @property:LLMDescription("Second number") val b: Float,
    )

    @Serializable
    class Result(val result: Float)

    final override val argsSerializer = Args.serializer()
    final override val resultSerializer = Result.serializer()
}

object PlusTool : CalculatorTool(name = "plus", description = "Adds a and b") {
    override suspend fun execute(args: Args) = Result(args.a + args.b)
}
```

Tools are then bundled into a `ToolRegistry`:

```kotlin
val toolRegistry = ToolRegistry {
    tool(CalculatorTools.PlusTool)
    tool(CalculatorTools.MinusTool)
    tool(CalculatorTools.DivideTool)
    tool(CalculatorTools.MultiplyTool)
    tool(ExitTool)
}
```

### Building an agent with a strategy graph

Koog strategies describe the LLM ↔ tool loop as a graph of nodes connected by `edge(... forwardTo ...)`. The calculator example:

```kotlin
val strategy = strategy(title) {
    val nodeRequestLLM by nodeLLMRequest()
    val nodeToolExecute by nodeExecuteTool()
    val nodeSendToolResult by nodeLLMSendToolResult()

    edge(nodeStart forwardTo nodeRequestLLM)
    edge(nodeRequestLLM forwardTo nodeToolExecute onToolCall { true })
    edge(nodeToolExecute forwardTo nodeSendToolResult)
    edge(nodeSendToolResult forwardTo nodeFinish onAssistantMessage { true })
}

val agentConfig = AIAgentConfig(
    prompt = prompt("calc", params = LeapLLMParams(temperature = 0f)) {
        system("You are a calculator. Use tools at your disposal to solve it.")
    },
    model = LeapModels.Chat.LFM2_1_2B_Tool,
    maxAgentIterations = 10,
)

val agent = AIAgent(
    promptExecutor = leapExecutor,
    strategy = strategy,
    agentConfig = agentConfig,
    toolRegistry = toolRegistry,
) {
    handleEvents {
        onToolCallStarting { ctx -> /* forward to UI */ }
        onAgentExecutionFailed { ctx -> /* surface error */ }
    }
}
```

`agent.run("What is the result of: 12 * 4")` drives the loop end-to-end.

## Notes / Troubleshooting

- **Model not found.** Make sure `adb push` placed the bundle in `/tmp/models/` and the filename matches what `LeapModels.Chat.LFM2_1_2B_Tool` expects. On a real device, `/tmp` is the same path the Leap runtime opens directly — there's no host-side rewriting.
- **Stale model file.** Re-push the bundle if you upgraded the SDK or switched model variants.
- **Koog Netty resource conflict.** The Gradle config strips `META-INF/*` via the `packaging` block as a known workaround. Don't remove it until the upstream Koog/Netty issue is resolved.
- **No automatic download.** Adopting `LeapModelDownloader` here would require changes to the `koog-edge` bridge (it currently expects a pre-existing on-device path). The other Android demos in this repo (RecipeGenerator, VLMExample, LeapAudioDemo, etc.) show the `LeapModelDownloader` pattern if you need a reference.

## Koog Framework

[Koog](https://docs.koog.ai) is an open-source framework for building AI agents with:

- Strategy DSL for orchestrating multi-step LLM workflows
- Pluggable `LLMClient` / `PromptExecutor` interfaces (this demo plugs Leap in via `koog-edge`)
- Tool registry with auto-generated JSON schemas from `@Serializable` Kotlin types
- Event handlers for observability
- MCP server integration for remote tool sourcing

## License

This project is licensed under the MIT License.
