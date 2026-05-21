@file:OptIn(kotlinx.cinterop.ExperimentalForeignApi::class)

package ai.liquid.leap.cli

import ai.liquid.leap.ModelLoadingOptions
import ai.liquid.leap.manifest.LeapDownloader
import ai.liquid.leap.message.MessageResponse
import io.ktor.client.HttpClient
import io.ktor.client.engine.curl.Curl
import io.ktor.client.plugins.contentnegotiation.ContentNegotiation
import io.ktor.serialization.kotlinx.json.json
import kotlin.system.exitProcess
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.runBlocking
import kotlinx.serialization.json.Json
import platform.posix.fflush
import platform.posix.fprintf
import platform.posix.stderr

// Kotlin/Native print()/println() go through stdio which is line-buffered on a TTY and fully
// buffered otherwise — flushing keeps the REPL truly streaming. fflush(NULL) flushes all open
// output streams per POSIX, avoiding a per-target stdout symbol lookup.
private fun flushStdout() {
  fflush(null)
}

private fun eprintln(msg: String) {
  fprintf(stderr, "%s\n", msg)
}

private fun Float.toFixed1(): String {
  // round() so 9.95 → "10.0" instead of "9.9" (truncation via toLong()).
  val rounded = kotlin.math.round(this * 10).toLong()
  val intPart = rounded / 10
  val frac = (rounded % 10).let { if (it < 0) -it else it }
  return "$intPart.$frac"
}

/** Defaults match the Android LeapChat demo. ~250 MB on first run; cached afterwards. */
private const val MODEL_NAME = "LFM2.5-350M"
private const val QUANTIZATION_TYPE = "Q4_0"
private const val SYSTEM_PROMPT = "You are a helpful assistant. Be concise."

// KV state cache directory — sibling of the leap_models/ bundle cache. The bounded-LRU cache
// persists per-conversation prefix snapshots so warm reloads (same system prompt + history) skip
// re-prefilling the chat template; meaningful speedup once the demo has been used a few times.
private const val CACHE_DIR = "./leap_models/cache"

fun main(args: Array<String>): Unit = runBlocking {
  if (args.isNotEmpty() && args[0] in setOf("-h", "--help")) {
    println(
      """
      |usage: leap-chat-cli
      |
      |Downloads $MODEL_NAME ($QUANTIZATION_TYPE) on first run and caches it under
      |./leap_models/. Type messages and press Enter to chat. EOF (Ctrl-Z) or
      |:quit exits.
      """
        .trimMargin()
    )
    exitProcess(0)
  }

  // Inject an HttpClient backed by the Curl engine so HTTPS works. The leap-sdk
  // for mingwX64 bundles Ktor CIO which has no TLS support on Native; without this
  // injection, LeapDownloader's call to leap.liquid.ai fails with "TLS sessions
  // are not supported on Native platform."
  //
  // ContentNegotiation + json are required because LeapDownloader's body<Manifest>()
  // expects the client to deserialize the JSON response — same plugins the SDK's
  // own default client installs. Without them: NoTransformationFoundException.
  val http =
    HttpClient(Curl) { install(ContentNegotiation) { json(Json { ignoreUnknownKeys = true }) } }
  http.use { chatLoop(it) }
}

private suspend fun chatLoop(http: HttpClient) {
  val downloader = LeapDownloader(httpClient = http)
  print("Loading $MODEL_NAME ($QUANTIZATION_TYPE) … ")
  flushStdout()
  val runner =
    try {
      downloader.loadModel(
        modelName = MODEL_NAME,
        quantizationType = QUANTIZATION_TYPE,
        options =
          ModelLoadingOptions(cacheOptions = ModelLoadingOptions.cacheOptions(path = CACHE_DIR)),
        progress = { pd ->
          if (pd.total > 0) {
            val pct = (pd.bytes * 100 / pd.total).toInt()
            val mbDone = pd.bytes / 1_000_000
            val mbTotal = pd.total / 1_000_000
            print("\rDownloading: $pct% ($mbDone / $mbTotal MB)")
            flushStdout()
          }
        },
      )
    } catch (e: Exception) {
      eprintln("\nfailed to load model: ${e.message}")
      exitProcess(1)
    }
  println("\nready (model id: ${runner.modelId})")
  println("Type a message and press Enter. EOF (Ctrl-Z) or :quit to exit.")
  println()

  try {
    val conversation = runner.createConversation(systemPrompt = SYSTEM_PROMPT)
    while (true) {
      print("> ")
      flushStdout()
      val line = readlnOrNull() ?: break
      val trimmed = line.trim()
      if (trimmed == ":quit") break
      if (trimmed.isEmpty()) continue

      conversation
        .generateResponse(line)
        .catch { e -> eprintln("\nerror: ${e.message}") }
        .collect { response ->
          when (response) {
            is MessageResponse.Chunk -> {
              print(response.text)
              flushStdout()
            }
            is MessageResponse.Complete -> {
              println()
              response.stats?.let { stats ->
                println("[${stats.completionTokens} tok, ${stats.tokenPerSecond.toFixed1()} tok/s]")
              }
            }
            is MessageResponse.Error -> {
              // Emitted in-band right before the flow closes with the same throwable; the
              // .catch above prints the message and lets the REPL continue on the next prompt.
            }
            else -> Unit // ReasoningChunk / FunctionCalls / AudioSample — not used by this CLI
          }
        }
    }
  } finally {
    runner.unload()
  }
}
