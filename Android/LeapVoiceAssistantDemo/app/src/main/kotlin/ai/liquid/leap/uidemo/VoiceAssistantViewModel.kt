package ai.liquid.leap.uidemo

import ai.liquid.leap.ModelLoadingOptions
import ai.liquid.leap.ModelRunner
import ai.liquid.leap.downloader.LeapModelDownloader
import ai.liquid.leap.downloader.LeapModelDownloaderNotificationConfig
import ai.liquid.leap.ui.VoiceAssistantIntent
import ai.liquid.leap.ui.VoiceAssistantStore
import ai.liquid.leap.ui.VoiceAssistantStoreState
import ai.liquid.leap.ui.VoiceAudioPlayer
import ai.liquid.leap.ui.VoiceAudioRecorder
import android.app.Application
import android.util.Log
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.withTimeout

private const val TAG = "VoiceAssistantViewModel"
private const val MODEL_NAME = "LFM2.5-Audio-1.5B"
private const val QUANTIZATION_TYPE = "Q4_0"
private const val SYSTEM_PROMPT = "Respond with interleaved text and audio."

class VoiceAssistantViewModel(application: Application) : AndroidViewModel(application) {
  private val recorder: VoiceAudioRecorder = AndroidAudioRecorder()
  private val player: VoiceAudioPlayer = AndroidAudioPlayer()

  val store = VoiceAssistantStore(recorder = recorder, player = player, scope = viewModelScope)

  val state: StateFlow<VoiceAssistantStoreState> = store.state

  @Volatile private var modelRunner: ModelRunner? = null

  init {
    viewModelScope.launch { loadModel() }
  }

  fun processIntent(intent: VoiceAssistantIntent) = store.processIntent(intent)

  private suspend fun loadModel() {
    runCatching {
        val app = getApplication<Application>()
        val downloader =
          LeapModelDownloader(
            app,
            notificationConfig =
              LeapModelDownloaderNotificationConfig.build {
                notificationTitleDownloading =
                  app.getString(R.string.notification_downloading_model)
                notificationTitleDownloaded = app.getString(R.string.notification_model_ready)
              },
          )

        store.setModelProgress(0f, "Checking model…")

        if (
          downloader.queryStatus(MODEL_NAME, QUANTIZATION_TYPE)
            is LeapModelDownloader.ModelDownloadStatus.NotOnLocal
        ) {
          downloader.requestDownloadModel(MODEL_NAME, QUANTIZATION_TYPE)
          // Single observer that reports progress and exits when the download completes.
          // 30-minute timeout matches LeapAudioDemo and accommodates slow connections.
          withTimeout(30 * 60 * 1000L) {
            downloader.observeDownloadProgress(MODEL_NAME, QUANTIZATION_TYPE).first { progress ->
              if (progress != null && progress.totalSizeInBytes > 0) {
                val frac =
                  (progress.downloadedSizeInBytes.toFloat() / progress.totalSizeInBytes.toFloat())
                    .coerceIn(0f, 1f)
                store.setModelProgress(frac, "Downloading (${(frac * 100).toInt()}%)")
              }
              progress == null &&
                downloader.queryStatus(MODEL_NAME, QUANTIZATION_TYPE) is
                  LeapModelDownloader.ModelDownloadStatus.Downloaded
            }
          }
        }

        store.setModelProgress(1f, "Loading…")
        val runner =
          downloader.loadModel(
            modelName = MODEL_NAME,
            quantizationType = QUANTIZATION_TYPE,
            options =
              ModelLoadingOptions(
                cacheOptions =
                  ModelLoadingOptions.cacheOptions(
                    path = app.cacheDir.resolve("leap-cache").absolutePath
                  )
              ),
          )
        modelRunner = runner
        store.setConversation(
          LeapVoiceConversation(
            conv = runner.createConversation(systemPrompt = SYSTEM_PROMPT),
            systemPrompt = SYSTEM_PROMPT,
          )
        )
      }
      .onFailure { e -> store.setModelError("✗ ${e.message}") }
  }

  override fun onCleared() {
    super.onCleared()
    store.close()
    // Unload on a fresh scope: viewModelScope is already cancelled by the time onCleared runs,
    // so a launch on it would never execute. Fire-and-forget on IO matches the other demos.
    val runner = modelRunner ?: return
    modelRunner = null
    CoroutineScope(Dispatchers.IO).launch {
      try {
        runner.unload()
      } catch (e: Exception) {
        Log.e(TAG, "Error unloading model", e)
      }
    }
  }
}
