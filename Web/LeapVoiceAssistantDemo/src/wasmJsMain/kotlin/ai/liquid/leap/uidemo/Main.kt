@file:OptIn(kotlin.js.ExperimentalWasmJsInterop::class)

package ai.liquid.leap.uidemo

import ai.liquid.leap.Conversation
import ai.liquid.leap.GenerationOptions
import ai.liquid.leap.manifest.LeapDownloader
import ai.liquid.leap.message.ChatMessage
import ai.liquid.leap.message.ChatMessageContent
import ai.liquid.leap.message.GenerationStats
import ai.liquid.leap.message.MessageResponse
import ai.liquid.leap.ui.AmplitudeSmoother
import ai.liquid.leap.ui.VoiceAssistantIntent
import ai.liquid.leap.ui.VoiceAssistantState
import ai.liquid.leap.ui.VoiceAssistantWidget
import ai.liquid.leap.ui.VoiceWidgetLabels
import ai.liquid.leap.ui.VoiceWidgetMode
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.ExperimentalComposeUiApi
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.ComposeViewport
import kotlinx.browser.document
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.collect
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch

@JsFun("(msg) => console.error(msg)") private external fun consoleError(msg: String)

@JsFun("() => navigator.hardwareConcurrency || 0") private external fun hardwareConcurrency(): Int

@JsFun("() => typeof navigator.gpu !== 'undefined'") private external fun hasWebGpu(): Boolean

@JsFun("() => typeof SharedArrayBuffer !== 'undefined'")
private external fun hasSharedArrayBuffer(): Boolean

@JsFun("() => typeof WebAssembly.promising === 'function'") private external fun hasJspi(): Boolean

/** Audio model used for voice interaction. */
private const val MODEL_NAME = "LFM2.5-Audio-1.5B"
private const val QUANTIZATION_SLUG = "Q4_0"
private const val SYSTEM_PROMPT = "Respond with interleaved text and audio."

private const val FRAME_DELAY_MS = 16L

/**
 * Browser entry point for the Leap voice demo.
 *
 * Downloads [MODEL_NAME]/[QUANTIZATION_SLUG] to Emscripten MEMFS via the browser Fetch API, then
 * provides an interactive voice assistant: press-and-hold to record, release to send the audio to
 * the model, and stream the audio response back. The orb animation is driven by real microphone /
 * playback amplitude in both directions.
 */
@OptIn(ExperimentalComposeUiApi::class)
fun main() {
  ComposeViewport(document.body!!) {
    MaterialTheme(colorScheme = darkColorScheme(background = Color.Black, surface = Color.Black)) {
      var widgetState by remember { mutableStateOf(VoiceAssistantState()) }
      var idleLabel by remember { mutableStateOf("Initializing…") }
      var debugInfo by remember { mutableStateOf("") }
      var lastStats by remember { mutableStateOf<GenerationStats?>(null) }

      val scope = rememberCoroutineScope()
      val conversationRef = remember { mutableStateOf<Conversation?>(null) }
      val recorder = remember { WebAudioRecorder() }
      val player = remember { WebAudioPlayer() }
      val ampSmoother = remember { AmplitudeSmoother() }
      var generationJob by remember { mutableStateOf<Job?>(null) }

      // ── Download + load model ─────────────────────────────────────────────
      LaunchedEffect(Unit) {
        runCatching {
            val downloader = LeapDownloader()
            idleLabel = "Resolving manifest…"
            val runner =
              downloader.loadModel(
                modelName = MODEL_NAME,
                quantizationSlug = QUANTIZATION_SLUG,
                progress = { pd ->
                  val pct = if (pd.total > 0) " ${(pd.bytes * 100 / pd.total).toInt()}%" else ""
                  idleLabel = "Downloading$pct"
                },
              )
            conversationRef.value = runner.createConversation(systemPrompt = SYSTEM_PROMPT)
            idleLabel = "Tap and hold to speak"
            val threads = hardwareConcurrency()
            val backends = if (hasWebGpu()) "WebGPU, CPU" else "CPU"
            val wasmFeatures = buildString {
              append("SIMD128")
              if (hasSharedArrayBuffer()) append(", pthreads")
              if (hasJspi()) append(", JSPI")
            }
            debugInfo =
              "$MODEL_NAME $QUANTIZATION_SLUG\nBackends: $backends\nThreads: $threads\nWASM: $wasmFeatures"
          }
          .onFailure { e ->
            consoleError(e.stackTraceToString())
            idleLabel = "Failed to load model"
          }
      }

      // ── Frame tick: drive orb amplitude from real audio ───────────────────
      LaunchedEffect(Unit) {
        while (true) {
          delay(FRAME_DELAY_MS)
          when (widgetState.mode) {
            VoiceWidgetMode.LISTENING -> {
              val smoothed = ampSmoother.update(recorder.amplitude)
              widgetState = widgetState.copy(amplitude = smoothed)
            }
            VoiceWidgetMode.RESPONDING -> {
              val smoothed = ampSmoother.update(player.amplitude)
              widgetState = widgetState.copy(amplitude = smoothed)
            }
            else -> Unit
          }
        }
      }

      Box(modifier = Modifier.fillMaxSize().background(Color.Black)) {
        VoiceAssistantWidget(
          state = widgetState,
          labels = VoiceWidgetLabels(idle = idleLabel),
          onIntent = { intent ->
            when (intent) {
              VoiceAssistantIntent.StartRecording -> {
                if (conversationRef.value != null) {
                  scope.launch {
                    widgetState = VoiceAssistantState(mode = VoiceWidgetMode.LISTENING)
                    recorder.start()
                  }
                }
              }
              VoiceAssistantIntent.StopRecording -> {
                val conv = conversationRef.value
                if (conv != null) {
                  generationJob =
                    scope.launch {
                      val wavBytes = recorder.stop()
                      if (wavBytes.isEmpty()) {
                        widgetState = VoiceAssistantState()
                        return@launch
                      }
                      player.initialize()
                      widgetState = VoiceAssistantState(mode = VoiceWidgetMode.RESPONDING)
                      runCatching {
                          conv
                            .generateResponse(
                              message =
                                ChatMessage(
                                  role = ChatMessage.Role.USER,
                                  content =
                                    listOf(
                                      ChatMessageContent.Audio(
                                        ChatMessageContent.Audio.InputAudio(wavBytes, "wav")
                                      )
                                    ),
                                ),
                              generationOptions = GenerationOptions(),
                            )
                            .collect { response ->
                              when (response) {
                                is MessageResponse.AudioSample ->
                                  player.enqueue(response.samples, response.sampleRate)
                                is MessageResponse.Complete -> lastStats = response.stats
                                else -> Unit
                              }
                            }
                        }
                        .onFailure { e -> consoleError(e.stackTraceToString()) }
                      player.drain() // wait for all buffered audio to finish before stopping
                      player.stop()
                      widgetState = VoiceAssistantState()
                      // Only update if not cancelled; CancelGeneration may have already set a
                      // fresh conversation and we must not overwrite it.
                      if (isActive) {
                        conversationRef.value =
                          conv.modelRunner.createConversation(systemPrompt = SYSTEM_PROMPT)
                      }
                    }
                }
              }
              VoiceAssistantIntent.CancelGeneration -> {
                generationJob?.cancel()
                generationJob = null
                recorder.cancel()
                player.stop()
                widgetState = VoiceAssistantState()
                conversationRef.value?.let { conv ->
                  conversationRef.value =
                    conv.modelRunner.createConversation(systemPrompt = SYSTEM_PROMPT)
                }
              }
            }
          },
          modifier = Modifier.fillMaxSize(),
        )
        if (debugInfo.isNotEmpty()) {
          val stats = lastStats
          val statsLine =
            if (stats != null) {
              val tps = stats.tokenPerSecond
              val tpsStr = "${tps.toInt()}.${((tps * 10).toInt() % 10)}"
              "\nLast gen: $tpsStr tok/s | ${stats.promptTokens} prompt | ${stats.completionTokens} gen tokens"
            } else ""
          Text(
            text = debugInfo + statsLine,
            color = Color.White.copy(alpha = 0.7f),
            fontSize = 11.sp,
            fontFamily = FontFamily.Monospace,
            modifier =
              Modifier.align(Alignment.BottomStart)
                .padding(12.dp)
                .background(Color.Black.copy(alpha = 0.55f))
                .padding(horizontal = 8.dp, vertical = 6.dp),
          )
        }
      }
    }
  }
}
