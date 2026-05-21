package ai.liquid.leap.cli

import ai.liquid.leap.ModelLoadingOptions
import ai.liquid.leap.manifest.LeapDownloader
import ai.liquid.leap.message.MessageResponse
import kotlin.system.exitProcess
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.runBlocking

/** Defaults match the Android LeapChat demo. ~250 MB on first run; cached afterwards. */
private const val MODEL_NAME = "LFM2.5-350M"
private const val QUANTIZATION_TYPE = "Q4_0"
private const val SYSTEM_PROMPT = "You are a helpful assistant. Be concise."

// KV state cache directory — sibling of the leap_models/ bundle cache. The bounded-LRU cache
// persists per-conversation prefix snapshots so warm reloads (same system prompt + history) skip
// re-prefilling the chat template; meaningful speedup once the demo has been used a few times.
private const val CACHE_DIR = "./leap_models/cache"

fun main(args: Array<String>): Unit = runBlocking {
  if (args.isNotEmpty() && (args[0] == "-h" || args[0] == "--help")) {
    println(
      """
      |usage: leap-chat-cli
      |
      |Downloads $MODEL_NAME ($QUANTIZATION_TYPE) on first run and caches it under
      |./leap_models/. Type messages and press Enter to chat. EOF (Ctrl-D) or
      |:quit exits.
      """
        .trimMargin()
    )
    exitProcess(0)
  }

  val downloader = LeapDownloader()
  print("Loading $MODEL_NAME ($QUANTIZATION_TYPE) … ")
  System.out.flush()
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
            print(
              "\rDownloading: %3d%% (%d / %d MB)"
                .format(pct, pd.bytes / 1_000_000, pd.total / 1_000_000)
            )
            System.out.flush()
          }
        },
      )
    } catch (e: Exception) {
      System.err.println("\nfailed to load model: ${e.message}")
      exitProcess(1)
    }
  println("\nready (model id: ${runner.modelId})")
  println("Type a message and press Enter. EOF (Ctrl-D) or :quit to exit.")
  println()

  try {
    val conversation = runner.createConversation(systemPrompt = SYSTEM_PROMPT)
    while (true) {
      print("> ")
      System.out.flush()
      val line = readlnOrNull() ?: break
      val trimmed = line.trim()
      if (trimmed == ":quit") break
      if (trimmed.isEmpty()) continue

      conversation
        .generateResponse(line)
        .catch { e -> System.err.println("\nerror: ${e.message}") }
        .collect { response ->
          when (response) {
            is MessageResponse.Chunk -> {
              print(response.text)
              System.out.flush()
            }
            is MessageResponse.Complete -> {
              println()
              response.stats?.let { stats ->
                System.err.println(
                  "[${stats.completionTokens} tok, %.1f tok/s]".format(stats.tokenPerSecond)
                )
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
