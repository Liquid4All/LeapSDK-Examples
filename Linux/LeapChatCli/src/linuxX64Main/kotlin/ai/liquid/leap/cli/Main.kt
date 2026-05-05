@file:OptIn(kotlinx.cinterop.ExperimentalForeignApi::class)

package ai.liquid.leap.cli

import ai.liquid.leap.manifest.LeapDownloader
import ai.liquid.leap.message.MessageResponse
import io.ktor.client.HttpClient
import io.ktor.client.engine.curl.Curl
import io.ktor.client.plugins.contentnegotiation.ContentNegotiation
import io.ktor.serialization.kotlinx.json.json
import kotlin.system.exitProcess
import kotlinx.coroutines.flow.collect
import kotlinx.coroutines.runBlocking
import kotlinx.serialization.json.Json
import platform.posix.fflush

// Kotlin/Native print()/println() go through stdio which is line-buffered on a TTY and fully
// buffered otherwise — flushing keeps the REPL truly streaming. fflush(NULL) flushes all open
// output streams per POSIX, avoiding a per-target stdout symbol lookup.
private fun flushStdout() {
  fflush(null)
}

/** Defaults match the Android LeapChat demo. ~370 MB on first run; cached afterwards. */
private const val MODEL_NAME = "LFM2-350M"
private const val QUANTIZATION_SLUG = "Q8_0"
private const val SYSTEM_PROMPT = "You are a helpful assistant. Be concise."

fun main(args: Array<String>): Unit = runBlocking {
  if (args.isNotEmpty() && args[0] in setOf("-h", "--help")) {
    println(
      """
      |usage: leap-chat-cli
      |
      |Downloads $MODEL_NAME ($QUANTIZATION_SLUG) on first run and caches it under
      |./leap_models/. Type messages and press Enter to chat. EOF (Ctrl-D) or
      |:quit exits.
      """
        .trimMargin()
    )
    exitProcess(0)
  }

  // Inject an HttpClient backed by the Curl engine so HTTPS works. The leap-sdk
  // for linuxX64 bundles Ktor CIO which has no TLS support on Native; without this
  // injection, LeapDownloader's call to leap.liquid.ai fails with "TLS sessions
  // are not supported on Native platform."
  //
  // ContentNegotiation + json are required because LeapDownloader's body<Manifest>()
  // expects the client to deserialize the JSON response — same plugins the SDK's
  // own default client installs. Without them: NoTransformationFoundException.
  val http = HttpClient(Curl) {
    install(ContentNegotiation) { json(Json { ignoreUnknownKeys = true }) }
  }
  http.use { chatLoop(it) }
}

private suspend fun chatLoop(http: HttpClient) {
  val downloader = LeapDownloader(httpClient = http)
  print("Loading $MODEL_NAME ($QUANTIZATION_SLUG) … ")
  flushStdout()
  val runner =
    try {
      downloader.loadModel(
        modelName = MODEL_NAME,
        quantizationSlug = QUANTIZATION_SLUG,
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
      println("\nfailed to load model: ${e.message}")
      exitProcess(1)
    }
  println("\nready (model id: ${runner.modelId})")
  println("Type a message and press Enter. EOF (Ctrl-D) or :quit to exit.")
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

      conversation.generateResponse(line).collect { response ->
        when (response) {
          is MessageResponse.Chunk -> {
            print(response.text)
            flushStdout()
          }
          is MessageResponse.Complete -> {
            println()
            response.stats?.let { stats ->
              println("[${stats.completionTokens} tok, ${stats.tokenPerSecond} tok/s]")
            }
          }
          else -> Unit // ReasoningChunk / FunctionCalls / AudioSample — not used by this CLI
        }
      }
    }
  } finally {
    runner.unload()
  }
}
