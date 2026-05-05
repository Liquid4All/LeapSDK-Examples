package ai.liquid.leap.cli

import ai.liquid.leap.manifest.LeapDownloader
import ai.liquid.leap.message.MessageResponse
import kotlin.system.exitProcess
import kotlinx.coroutines.flow.collect
import kotlinx.coroutines.runBlocking

/** Defaults match the Android LeapChat demo. ~370 MB on first run; cached afterwards. */
private const val MODEL_NAME = "LFM2-350M"
private const val QUANTIZATION_SLUG = "Q8_0"
private const val SYSTEM_PROMPT = "You are a helpful assistant. Be concise."

fun main(args: Array<String>): Unit = runBlocking {
  if (args.isNotEmpty() && args[0] in setOf("-h", "--help")) {
    System.err.println(
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

  val downloader = LeapDownloader()
  print("Loading $MODEL_NAME ($QUANTIZATION_SLUG) … ")
  System.out.flush()
  val runner =
    try {
      downloader.loadModel(
        modelName = MODEL_NAME,
        quantizationSlug = QUANTIZATION_SLUG,
        progress = { pd ->
          if (pd.total > 0) {
            val pct = (pd.bytes * 100 / pd.total).toInt()
            print("\rDownloading: %3d%% (%d / %d MB)".format(pct, pd.bytes / 1_000_000, pd.total / 1_000_000))
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

      conversation.generateResponse(line).collect { response ->
        when (response) {
          is MessageResponse.Chunk -> {
            print(response.text)
            System.out.flush()
          }
          is MessageResponse.Complete -> {
            println()
            response.stats?.let { stats ->
              System.err.println(
                "[${stats.completionTokens} tok, " + "%.1f tok/s]".format(stats.tokenPerSecond)
              )
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
