package ai.liquid.leap.uidemo

import ai.liquid.leap.manifest.LeapDownloader
import ai.liquid.leap.manifest.LeapDownloaderConfig
import ai.liquid.leap.ui.VoiceAssistantIntent
import ai.liquid.leap.ui.VoiceAssistantStore
import ai.liquid.leap.ui.VoiceAssistantStoreState
import ai.liquid.leap.ui.VoiceAudioPlayer
import ai.liquid.leap.ui.VoiceAudioRecorder
import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import java.io.File
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch

private const val MODEL_NAME = "LFM2.5-Audio-1.5B"
private const val QUANTIZATION_SLUG = "Q4_0"
private const val SYSTEM_PROMPT = "Respond with interleaved text and audio."

class VoiceAssistantViewModel(application: Application) : AndroidViewModel(application) {
  private val recorder: VoiceAudioRecorder = AndroidAudioRecorder()
  private val player: VoiceAudioPlayer = AndroidAudioPlayer()

  val store = VoiceAssistantStore(recorder = recorder, player = player, scope = viewModelScope)

  val state: StateFlow<VoiceAssistantStoreState> = store.state

  private val modelDir = File(application.filesDir, "leap_models").apply { mkdirs() }

  init {
    viewModelScope.launch { loadModel() }
  }

  fun processIntent(intent: VoiceAssistantIntent) = store.processIntent(intent)

  private suspend fun loadModel() {
    runCatching {
        val downloader = LeapDownloader(LeapDownloaderConfig(saveDir = modelDir.absolutePath))
        store.setModelProgress(0f, "Resolving manifest\u2026")
        val runner =
          downloader.loadModel(
            modelName = MODEL_NAME,
            quantizationSlug = QUANTIZATION_SLUG,
            progress = { pd ->
              val pct = if (pd.total > 0) " (${(pd.bytes * 100 / pd.total).toInt()}%)" else ""
              store.setModelProgress(
                fraction = if (pd.total > 0) pd.bytes.toFloat() / pd.total else 0f,
                message = "Downloading$pct",
              )
            },
          )
        store.setConversation(
          LeapVoiceConversation(
            conv = runner.createConversation(systemPrompt = SYSTEM_PROMPT),
            systemPrompt = SYSTEM_PROMPT,
          )
        )
      }
      .onFailure { e -> store.setModelError("\u2717 ${e.message}") }
  }

  override fun onCleared() {
    super.onCleared()
    store.close()
  }
}
