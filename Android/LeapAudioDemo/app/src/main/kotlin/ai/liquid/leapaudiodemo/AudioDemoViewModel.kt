package ai.liquid.leapaudiodemo

import ai.liquid.leap.Conversation
import ai.liquid.leap.ModelRunner
import ai.liquid.leap.downloader.LeapModelDownloader
import ai.liquid.leap.downloader.LeapModelDownloaderNotificationConfig
import ai.liquid.leap.message.ChatMessage
import ai.liquid.leap.message.ChatMessageContent
import ai.liquid.leap.message.MessageResponse
import android.app.Application
import android.util.Log
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.channels.BufferOverflow
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.withTimeout

class AudioDemoViewModel
@JvmOverloads
constructor(
  application: Application,
  audioPlayback: AudioPlayback? = null,
  audioRecording: AudioRecording? = null,
  stringProvider: StringProvider? = null,
) : AndroidViewModel(application) {
  companion object {
    private const val TAG = "AudioDemoViewModel"
    private const val MODEL_NAME = "LFM2.5-Audio-1.5B"
    private const val QUANTIZATION = "Q4_0"
    // Limit message history to prevent memory issues and slow scrolling
    private const val MAX_MESSAGES = 50
    // Maximum recording duration in seconds (matches AudioRecorder.MAX_RECORDING_SECONDS)
    private const val MAX_RECORDING_SECONDS = 60
  }

  private val _state = MutableStateFlow(AudioDemoState())
  val state: StateFlow<AudioDemoState> = _state.asStateFlow()

  // Side effects with buffering to prevent loss if UI isn't collecting
  private val _sideEffect =
    MutableSharedFlow<AudioDemoSideEffect>(
      replay = 0, // Don't replay to new collectors
      extraBufferCapacity = 1, // Buffer 1 event if no active collectors
      onBufferOverflow = BufferOverflow.DROP_OLDEST,
    )
  val sideEffect: SharedFlow<AudioDemoSideEffect> = _sideEffect.asSharedFlow()

  private var conversation: Conversation? = null
  private var modelRunner: ModelRunner? = null

  // AudioPlayer callbacks - Note: These callbacks create tight coupling between AudioPlayer
  // and ViewModel state. For a production app, consider using a more decoupled architecture
  // (e.g., repository pattern, use cases, or event bus). For this demo, direct coupling is
  // acceptable for simplicity and clarity.
  private val audioPlayer: AudioPlayback =
    audioPlayback
      ?: AudioPlayer(
        context = application,
        onPlaybackInterrupted = {
          // Reset playback state when interrupted by audio focus loss
          _state.update { it.copy(playingMessageId = null, isStreamingPlaybackActive = false) }
        },
        onPlaybackCompleted = {
          // Reset playback state when playback completes naturally
          _state.update { it.copy(playingMessageId = null) }
        },
        onPlaybackError = { errorMessage ->
          // Show error message to user when playback fails
          viewModelScope.launch { _sideEffect.emit(AudioDemoSideEffect.ShowSnackbar(errorMessage)) }
        },
        onStreamingCompleted = {
          // Streaming playback finished draining buffer - clean up resources
          _state.update { it.copy(isStreamingPlaybackActive = false) }
          audioPlayer.stopStreaming()
        },
      )
  private val audioRecorder: AudioRecording = audioRecording ?: AudioRecorder()
  private var downloader: LeapModelDownloader? = null
  private var generationJob: Job? = null
  private var recordingTimerJob: Job? = null
  private val strings: StringProvider = stringProvider ?: AndroidStringProvider(application)

  // Helper to get string resources
  private fun getString(resId: Int, vararg formatArgs: Any): String {
    return strings.getString(resId, *formatArgs)
  }

  fun onEvent(event: AudioDemoEvent) {
    when (event) {
      is AudioDemoEvent.LoadModel -> loadModel()
      is AudioDemoEvent.RetryLoadModel -> retryLoadModel()
      is AudioDemoEvent.CancelDownload -> cancelDownload()
      is AudioDemoEvent.DeleteModel -> deleteModel()
      is AudioDemoEvent.UpdateInputText -> updateInputText(event.text)
      is AudioDemoEvent.SendTextPrompt -> sendTextPrompt()
      is AudioDemoEvent.SendAudioPrompt -> sendAudioPrompt(event.samples, event.sampleRate)
      is AudioDemoEvent.StartRecording -> startRecording()
      is AudioDemoEvent.StopRecording -> stopRecording()
      is AudioDemoEvent.RecordingFailed -> recordingFailed()
      is AudioDemoEvent.PlayAudio -> playAudio(event.messageId, event.audioData, event.sampleRate)
      is AudioDemoEvent.StopAudioPlayback -> stopAudioPlayback()
      is AudioDemoEvent.StopGeneration -> stopGeneration()
    }
  }

  private fun retryLoadModel() {
    // Clear error state and status message before retrying
    _state.update { it.copy(modelState = ModelState.NotLoaded, status = null) }
    loadModel()
  }

  private fun cancelDownload() {
    viewModelScope.launch {
      try {
        downloader?.requestStopDownload(MODEL_NAME, QUANTIZATION)
        _state.update {
          it.copy(
            modelState = ModelState.NotLoaded,
            status = null,
          )
        }
        _sideEffect.emit(AudioDemoSideEffect.ShowSnackbar(getString(R.string.success_download_cancelled)))
      } catch (e: Exception) {
        Log.e(TAG, "Failed to cancel download", e)
      }
    }
  }

  private fun deleteModel() {
    viewModelScope.launch {
      try {
        val modelFolder = downloader?.getModelResourceFolder(MODEL_NAME, QUANTIZATION)
        if (modelFolder?.exists() == true) {
          modelFolder.deleteRecursively()
          _state.update {
            it.copy(
              modelState = ModelState.NotLoaded,
              status = null,
            )
          }
          _sideEffect.emit(AudioDemoSideEffect.ShowSnackbar(getString(R.string.success_model_deleted)))
        }
      } catch (e: Exception) {
        Log.e(TAG, "Failed to delete model", e)
        _sideEffect.emit(
          AudioDemoSideEffect.ShowSnackbar(getString(R.string.error_delete_model, e.message ?: "Unknown error"))
        )
      }
    }
  }

  private fun loadModel() {
    if (
      _state.value.modelState is ModelState.Ready || _state.value.modelState is ModelState.Loading
    )
      return

    viewModelScope.launch {
      _state.update {
        it.copy(
          modelState = ModelState.Loading,
          status = getString(R.string.status_downloading_model, MODEL_NAME),
        )
      }

      try {
        // Initialize downloader
        if (downloader == null) {
          downloader =
            LeapModelDownloader(
              getApplication(),
              notificationConfig =
                LeapModelDownloaderNotificationConfig.build {
                  notificationTitleDownloading = getString(R.string.notification_downloading_model)
                  notificationTitleDownloaded = getString(R.string.notification_model_ready)
                },
            )
        }
        val downloaderInstance = downloader!!

        // Check if model needs to be downloaded
        val currentStatus = downloaderInstance.queryStatus(MODEL_NAME, QUANTIZATION)

        if (currentStatus is LeapModelDownloader.ModelDownloadStatus.NotOnLocal) {
          _state.update { it.copy(status = getString(R.string.status_starting_download)) }

          // Start observing progress before requesting download
          val progressJob =
            viewModelScope.launch {
              downloaderInstance.observeDownloadProgress(MODEL_NAME, QUANTIZATION).collect {
                progress ->
                if (progress != null) {
                  val percentage =
                    if (progress.totalSizeInBytes > 0) {
                      (progress.downloadedSizeInBytes * 100.0 / progress.totalSizeInBytes).toInt()
                    } else 0
                  _state.update {
                    it.copy(status = getString(R.string.status_downloading_progress, percentage))
                  }
                }
              }
            }

          // Request download
          downloaderInstance.requestDownloadModel(MODEL_NAME, QUANTIZATION)

          // Wait until download completes with timeout
          // 30 minutes allows for slow connections and large model (2-3 GB)
          // Calculation: 3 GB / 30 min = 1.7 Mbps minimum speed
          try {
            withTimeout(30 * 60 * 1000L) {
              downloaderInstance.observeDownloadProgress(MODEL_NAME, QUANTIZATION).first { progress ->
                progress == null &&
                  downloaderInstance.queryStatus(MODEL_NAME, QUANTIZATION) is
                    LeapModelDownloader.ModelDownloadStatus.Downloaded
              }
            }
          } catch (e: kotlinx.coroutines.TimeoutCancellationException) {
            // Download timed out after 30 minutes
            progressJob.cancel()
            progressJob.join()
            throw Exception("Download timed out. Please check your connection and try again.")
          }

          // Cancel the progress collection job
          progressJob.cancel()
          progressJob.join()
          _state.update { it.copy(status = getString(R.string.status_download_complete)) }
        }

        // Load the model
        _state.update { it.copy(status = getString(R.string.status_loading_model)) }
        modelRunner =
          downloaderInstance.loadModel(modelName = MODEL_NAME, quantizationType = QUANTIZATION)

        // Create initial conversation
        conversation = modelRunner!!.createConversation(getString(R.string.system_prompt_audio))

        _state.update {
          val updatedMessages = (it.messages +
            AudioDemoMessage(
              role = ChatMessage.Role.ASSISTANT,
              text = getString(R.string.model_loaded, MODEL_NAME, QUANTIZATION),
            )).takeLast(MAX_MESSAGES)
          it.copy(
            modelState = ModelState.Ready,
            status = getString(R.string.status_ready),
            messages = updatedMessages,
          )
        }
      } catch (e: Exception) {
        val errorMessage =
          when {
            e.message?.contains("space", ignoreCase = true) == true ->
              getString(R.string.error_storage_space)
            e.message?.contains("network", ignoreCase = true) == true ||
              e.message?.contains("connection", ignoreCase = true) == true ->
              getString(R.string.error_network)
            else -> getString(R.string.error_load_model, e.message ?: "Unknown error")
          }

        _state.update {
          it.copy(modelState = ModelState.Error(errorMessage, canRetry = true), status = null)
        }
        Log.e(TAG, "Model loading failed", e)
      }
    }
  }

  private fun updateInputText(text: String) {
    _state.update { it.copy(inputText = text) }
  }

  private fun sendTextPrompt() {
    val trimmed = _state.value.inputText.trim()
    if (trimmed.isEmpty()) return

    val message =
      ChatMessage(role = ChatMessage.Role.USER, content = listOf(ChatMessageContent.Text(trimmed)))

    _state.update {
      val updatedMessages = (it.messages + AudioDemoMessage(role = ChatMessage.Role.USER, text = trimmed))
        .takeLast(MAX_MESSAGES)
      it.copy(
        inputText = "",
        messages = updatedMessages,
      )
    }
    streamResponse(message)
  }

  private fun sendAudioPrompt(samples: FloatArray, sampleRate: Int) {
    if (samples.isEmpty()) {
      _state.update { it.copy(status = getString(R.string.error_audio_empty)) }
      return
    }

    // Convert float samples to WAV ByteArray
    val wavData = AudioEncoder.floatArrayToWav(samples, sampleRate)
    val content = ChatMessageContent.Audio(wavData)
    val message = ChatMessage(role = ChatMessage.Role.USER, content = listOf(content))

    val display = getString(R.string.audio_prompt_format, samples.size, sampleRate)

    _state.update {
      val updatedMessages = (it.messages +
        AudioDemoMessage(
          role = ChatMessage.Role.USER,
          text = display,
          audioData = samples,
          sampleRate = sampleRate,
        )).takeLast(MAX_MESSAGES)
      it.copy(messages = updatedMessages)
    }
    streamResponse(message)
  }

  private fun startRecording() {
    val started = audioRecorder.start()
    if (started) {
      _state.update {
        it.copy(
          recordingState = RecordingState.Recording,
          recordingDurationSeconds = MAX_RECORDING_SECONDS,
          status = getString(R.string.status_recording),
        )
      }
      // Start countdown timer
      startRecordingTimer()
    } else {
      recordingFailed()
    }
  }

  private fun startRecordingTimer() {
    recordingTimerJob?.cancel()
    recordingTimerJob = viewModelScope.launch {
      var remainingSeconds = MAX_RECORDING_SECONDS
      while (remainingSeconds > 0 && _state.value.recordingState is RecordingState.Recording) {
        kotlinx.coroutines.delay(1000)
        remainingSeconds--
        _state.update { it.copy(recordingDurationSeconds = remainingSeconds) }
      }
      // Auto-stop when timer reaches 0
      if (remainingSeconds == 0 && _state.value.recordingState is RecordingState.Recording) {
        stopRecording()
      }
    }
  }

  private fun stopRecording() {
    recordingTimerJob?.cancel()
    recordingTimerJob = null
    viewModelScope.launch {
      val capture = audioRecorder.stop()
      _state.update { it.copy(recordingState = RecordingState.Idle, recordingDurationSeconds = 0) }
      if (capture != null) {
        sendAudioPrompt(capture.samples, capture.sampleRate)
      }
    }
  }

  private fun recordingFailed() {
    recordingTimerJob?.cancel()
    recordingTimerJob = null
    _state.update { it.copy(recordingState = RecordingState.Idle, recordingDurationSeconds = 0) }
    viewModelScope.launch {
      _sideEffect.emit(AudioDemoSideEffect.ShowSnackbar(getString(R.string.error_recording_failed)))
    }
  }

  private fun playAudio(messageId: String, audioData: FloatArray, sampleRate: Int) {
    _state.update { it.copy(playingMessageId = messageId) }
    audioPlayer.play(audioData, sampleRate)
    // Playback state will be reset by onPlaybackCompleted callback
  }

  private fun stopAudioPlayback() {
    audioPlayer.stop()
    // Also stop streaming if it's draining the buffer
    if (_state.value.isStreamingPlaybackActive) {
      audioPlayer.stopStreaming()
    }
    _state.update { it.copy(playingMessageId = null, isStreamingPlaybackActive = false) }
  }

  private fun stopGeneration() {
    viewModelScope.launch {
      generationJob?.cancel()
      generationJob?.join()
      generationJob = null
      audioPlayer.stopStreaming()
      _state.update {
        it.copy(
          generationState = GenerationState.Idle,
          status = getString(R.string.status_ready),
          isStreamingPlaybackActive = false,
          playingMessageId = null,
        )
      }
    }
  }

  private fun streamResponse(message: ChatMessage) {
    val currentModelRunner =
      modelRunner
        ?: run {
          _state.update { it.copy(status = getString(R.string.error_model_not_ready)) }
          return
        }

    // Cancel any existing generation job to prevent multiple parallel generations
    generationJob?.cancel()

    generationJob =
      viewModelScope.launch {
        // Create fresh conversation for each message (audio model doesn't support multi-turn)
        val currentConversation = try {
          currentModelRunner.createConversation(getString(R.string.system_prompt_audio))
        } catch (e: Exception) {
          _state.update {
            it.copy(
              status =
                getString(R.string.error_conversation_create, e.message ?: "Unknown error"),
              generationState = GenerationState.Idle,
            )
          }
          return@launch
        }
        _state.update {
          it.copy(
            generationState = GenerationState.GeneratingText(""),
            status = getString(R.string.status_awaiting_response),
          )
        }

        audioPlayer.reset()
        val textBuilder = StringBuilder()
        // Pre-allocate audio buffer for ~30 seconds of audio at 24kHz sample rate
        // (24,000 samples/sec × 30 sec = 720,000 samples ≈ 2.88 MB)
        // Reduces GC pressure for typical responses
        // Falls back to default capacity if pre-allocation fails (low memory device)
        val audioSamplesList = try {
          ArrayList<Float>(720_000)
        } catch (e: OutOfMemoryError) {
          Log.w(TAG, "Failed to pre-allocate audio buffer, using default capacity", e)
          ArrayList<Float>()
        }
        var audioSampleRate = 24000
        var isAudioStreamStarted = false

        try {
          currentConversation.generateResponse(message).collect { event ->
            when (event) {
              is MessageResponse.Chunk -> {
                textBuilder.append(event.text)
                _state.update {
                  it.copy(generationState = GenerationState.GeneratingText(textBuilder.toString()))
                }
              }
              is MessageResponse.ReasoningChunk -> {
                _state.update { it.copy(status = getString(R.string.status_thinking)) }
              }
              is MessageResponse.AudioSample -> {
                // Add samples in batch for efficiency
                audioSamplesList.addAll(event.samples.toList())
                audioSampleRate = event.sampleRate

                // Start streaming on first audio sample
                if (!isAudioStreamStarted) {
                  audioPlayer.startStreaming(audioSampleRate)
                  isAudioStreamStarted = true
                  _state.update {
                    it.copy(
                      generationState = GenerationState.GeneratingWithAudio(textBuilder.toString())
                    )
                  }
                }

                // Write samples to stream immediately
                val written = audioPlayer.writeStream(event.samples)
                if (!written) {
                  Log.w(TAG, "Audio buffer full, dropping ${event.samples.size} samples")
                }
                _state.update { it.copy(status = getString(R.string.status_streaming_audio)) }
              }
              is MessageResponse.Complete -> {
                // Finish audio streaming gracefully - let buffered audio play out
                val shouldKeepStreamingActive = isAudioStreamStarted
                if (isAudioStreamStarted) {
                  audioPlayer.finishStreaming()
                }

                val finalText = textBuilder.toString().trim()
                val audioData =
                  if (audioSamplesList.isNotEmpty()) {
                    audioSamplesList.toFloatArray()
                  } else null

                val newMessage =
                  AudioDemoMessage(
                    role = ChatMessage.Role.ASSISTANT,
                    text =
                      if (finalText.isEmpty()) getString(R.string.audio_response_placeholder)
                      else finalText,
                    audioData = audioData,
                    sampleRate = audioSampleRate,
                  )

                _state.update {
                  val updatedMessages = (it.messages + newMessage).takeLast(MAX_MESSAGES)
                  it.copy(
                    messages = updatedMessages,
                    generationState = GenerationState.Idle,
                    status =
                      if (audioData != null) {
                        getString(R.string.status_response_complete_with_audio)
                      } else {
                        getString(R.string.status_response_complete)
                      },
                    // If we're draining streaming audio buffer, mark it as playing
                    isStreamingPlaybackActive = shouldKeepStreamingActive,
                    playingMessageId = if (shouldKeepStreamingActive) newMessage.id else null,
                  )
                }
              }
              else -> {}
            }
          }
        } catch (e: Exception) {
          // Ensure audio is stopped on error
          if (isAudioStreamStarted) {
            audioPlayer.stopStreaming()
          }

          _state.update {
            it.copy(
              generationState = GenerationState.Idle,
              status = getString(R.string.error_generation_failed, e.message ?: "Unknown error"),
              playingMessageId = null,
            )
          }
          Log.e(TAG, "Response generation failed", e)
        }
      }
  }

  override fun onCleared() {
    super.onCleared()

    // Release audio resources
    try {
      audioPlayer.release()
    } catch (e: Exception) {
      Log.e(TAG, "Error releasing audio player", e)
    }

    // Cancel any ongoing recording
    viewModelScope.launch {
      try {
        audioRecorder.cancel()
      } catch (e: Exception) {
        Log.e(TAG, "Error cancelling audio recorder", e)
      }
    }

    // Unload model asynchronously to avoid ANR
    // Don't use runBlocking - if cleanup takes too long, let it finish in background
    CoroutineScope(Dispatchers.IO).launch {
      try {
        modelRunner?.unload()
      } catch (e: Exception) {
        Log.e(TAG, "Error unloading model", e)
      }
    }
  }
}

// Supporting data classes and sealed interface moved to bottom per AGENTS.md guidelines

/** Represents the state of the model loading process. */
sealed interface ModelState {
  /** Model has not been loaded yet */
  data object NotLoaded : ModelState

  /** Model is currently being downloaded and loaded */
  data object Loading : ModelState

  /** Model failed to load with an error message */
  data class Error(val message: String, val canRetry: Boolean) : ModelState

  /** Model is loaded and ready for inference */
  data object Ready : ModelState
}

/** Represents the state of response generation. */
sealed interface GenerationState {
  /** Not currently generating */
  data object Idle : GenerationState

  /** Generating text response (no audio yet) */
  data class GeneratingText(val streamingText: String) : GenerationState

  /** Generating response with audio streaming */
  data class GeneratingWithAudio(val streamingText: String) : GenerationState
}

/** Represents the state of audio recording. */
sealed interface RecordingState {
  /** Not recording */
  data object Idle : RecordingState

  /** Currently recording audio */
  data object Recording : RecordingState
}

data class AudioDemoMessage(
  val id: String = java.util.UUID.randomUUID().toString(),
  val role: ChatMessage.Role,
  val text: String,
  val audioData: FloatArray? = null,
  val sampleRate: Int = 0,
) {
  val isUser: Boolean
    get() = role == ChatMessage.Role.USER

  // Override equals and hashCode for proper FloatArray comparison
  override fun equals(other: Any?): Boolean {
    if (this === other) return true
    if (javaClass != other?.javaClass) return false

    other as AudioDemoMessage

    if (id != other.id) return false
    if (role != other.role) return false
    if (text != other.text) return false
    if (audioData != null) {
      if (other.audioData == null) return false
      if (!audioData.contentEquals(other.audioData)) return false
    } else if (other.audioData != null) return false
    if (sampleRate != other.sampleRate) return false

    return true
  }

  override fun hashCode(): Int {
    var result = id.hashCode()
    result = 31 * result + role.hashCode()
    result = 31 * result + text.hashCode()
    result = 31 * result + (audioData?.contentHashCode() ?: 0)
    result = 31 * result + sampleRate
    return result
  }
}

data class AudioDemoState(
  val messages: List<AudioDemoMessage> = emptyList(),
  val inputText: String = "",
  val status: String? = null,
  val modelState: ModelState = ModelState.NotLoaded,
  val generationState: GenerationState = GenerationState.Idle,
  val recordingState: RecordingState = RecordingState.Idle,
  val playingMessageId: String? = null,
  val isStreamingPlaybackActive: Boolean = false,
  val recordingDurationSeconds: Int = 0,
)

sealed interface AudioDemoEvent {
  data object LoadModel : AudioDemoEvent

  data object RetryLoadModel : AudioDemoEvent

  data object CancelDownload : AudioDemoEvent

  data object DeleteModel : AudioDemoEvent

  data class UpdateInputText(val text: String) : AudioDemoEvent

  data object SendTextPrompt : AudioDemoEvent

  data class SendAudioPrompt(val samples: FloatArray, val sampleRate: Int) : AudioDemoEvent

  data object StartRecording : AudioDemoEvent

  data object StopRecording : AudioDemoEvent

  data object RecordingFailed : AudioDemoEvent

  data class PlayAudio(val messageId: String, val audioData: FloatArray, val sampleRate: Int) :
    AudioDemoEvent

  data object StopAudioPlayback : AudioDemoEvent

  data object StopGeneration : AudioDemoEvent
}

sealed interface AudioDemoSideEffect {
  data class ShowSnackbar(val message: String) : AudioDemoSideEffect
}
