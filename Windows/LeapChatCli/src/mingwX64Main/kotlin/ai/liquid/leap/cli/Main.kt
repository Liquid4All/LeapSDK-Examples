package ai.liquid.leap.cli

import ai.liquid.leap.LeapClient
import ai.liquid.leap.message.MessageResponse
import kotlin.system.exitProcess
import kotlinx.coroutines.flow.collect
import kotlinx.coroutines.runBlocking

private const val DEFAULT_SYSTEM_PROMPT = "You are a helpful assistant. Be concise."

fun main(args: Array<String>): Unit = runBlocking {
  if (args.isEmpty() || args[0] in setOf("-h", "--help")) {
    println(
      """
      |usage: leap-chat-cli <model-bundle-path> [system-prompt]
      |
      |  <model-bundle-path>  Path to a .bundle file (e.g. LFM2-1.2B-Q4_0.bundle)
      |  [system-prompt]      Optional. Defaults to "$DEFAULT_SYSTEM_PROMPT"
      |
      |Type messages and press Enter to chat. EOF (Ctrl-Z) or :quit exits.
      """
        .trimMargin()
    )
    exitProcess(if (args.isEmpty()) 64 else 0)
  }

  val modelPath = args[0]
  val systemPrompt = args.getOrNull(1) ?: DEFAULT_SYSTEM_PROMPT

  println("Loading model from $modelPath … ")
  val runner =
    try {
      LeapClient.loadModel(modelPath = modelPath)
    } catch (e: Exception) {
      println("failed to load model: ${e.message}")
      exitProcess(1)
    }
  println("ready (model id: ${runner.modelId})")
  println("Type a message and press Enter. EOF (Ctrl-Z) or :quit to exit.")
  println()

  try {
    val conversation = runner.createConversation(systemPrompt = systemPrompt)
    while (true) {
      print("> ")
      val line = readlnOrNull() ?: break
      val trimmed = line.trim()
      if (trimmed == ":quit") break
      if (trimmed.isEmpty()) continue

      conversation.generateResponse(line).collect { response ->
        when (response) {
          is MessageResponse.Chunk -> print(response.text)
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
