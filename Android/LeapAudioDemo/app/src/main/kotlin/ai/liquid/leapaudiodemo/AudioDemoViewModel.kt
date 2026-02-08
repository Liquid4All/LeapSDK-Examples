package ai.liquid.leapaudiodemo

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import ai.liquid.leap.Conversation
import ai.liquid.leap.ModelRunner
import ai.liquid.leap.downloader.LeapModelDownloader
import ai.liquid.leap.downloader.LeapModelDownloaderNotificationConfig
import ai.liquid.leap.message.ChatMessage
import ai.liquid.leap.message.ChatMessageContent
import ai.liquid.leap.message.MessageResponse
import android.util.Log
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import java.io.ByteArrayOutputStream
import java.nio.ByteBuffer
import java.nio.ByteOrder

class AudioDemoViewModel(application: Application) : AndroidViewModel(application) {
    companion object {
        private const val TAG = "AudioDemoViewModel"
        private const val MODEL_NAME = "LFM2.5-Audio-1.5B"
        private const val QUANTIZATION = "Q4_0"
        // Audio model doesn't support multi-turn conversations properly
        private const val ENABLE_MULTI_TURN = false
    }

    private val _state = MutableStateFlow(AudioDemoState())
    val state: StateFlow<AudioDemoState> = _state.asStateFlow()

    private var conversation: Conversation? = null
    private var modelRunner: ModelRunner? = null
    private val audioPlayer = AudioPlayer(
        context = application,
        onPlaybackInterrupted = {
            // Reset playback state when interrupted by audio focus loss
            _state.update {
                it.copy(
                    playingMessageId = null,
                    isStreamingAudio = false
                )
            }
        },
        onPlaybackCompleted = {
            // Reset playback state when playback completes naturally
            _state.update {
                it.copy(playingMessageId = null)
            }
        }
    )
    private var downloader: LeapModelDownloader? = null
    private var generationJob: Job? = null

    // Helper to get string resources
    private fun getString(resId: Int, vararg formatArgs: Any): String {
        return getApplication<Application>().getString(resId, *formatArgs)
    }

    fun onEvent(event: AudioDemoEvent) {
        when (event) {
            is AudioDemoEvent.LoadModel -> loadModel()
            is AudioDemoEvent.RetryLoadModel -> retryLoadModel()
            is AudioDemoEvent.UpdateInputText -> updateInputText(event.text)
            is AudioDemoEvent.SendTextPrompt -> sendTextPrompt()
            is AudioDemoEvent.SendAudioPrompt -> sendAudioPrompt(event.samples, event.sampleRate)
            is AudioDemoEvent.ToggleRecording -> toggleRecording()
            is AudioDemoEvent.RecordingFailed -> recordingFailed()
            is AudioDemoEvent.PlayAudio -> playAudio(event.messageId, event.audioData, event.sampleRate)
            is AudioDemoEvent.StopAudioPlayback -> stopAudioPlayback()
            is AudioDemoEvent.StopGeneration -> stopGeneration()
        }
    }

    private fun retryLoadModel() {
        _state.update { it.copy(loadError = null, canRetryLoad = false) }
        loadModel()
    }

    private fun loadModel() {
        if (_state.value.isModelLoaded || _state.value.isModelLoading) return

        viewModelScope.launch {
            _state.update {
                it.copy(
                    isModelLoading = true,
                    status = getString(R.string.status_downloading_model, MODEL_NAME),
                    loadError = null,
                    canRetryLoad = false
                )
            }

            try {
                // Initialize downloader
                if (downloader == null) {
                    downloader = LeapModelDownloader(
                        getApplication(),
                        notificationConfig = LeapModelDownloaderNotificationConfig.build {
                            notificationTitleDownloading = getString(R.string.notification_downloading_model)
                            notificationTitleDownloaded = getString(R.string.notification_model_ready)
                        }
                    )
                }
                val downloaderInstance = downloader!!

                // Check if model needs to be downloaded
                val currentStatus = downloaderInstance.queryStatus(MODEL_NAME, QUANTIZATION)

                if (currentStatus is LeapModelDownloader.ModelDownloadStatus.NotOnLocal) {
                    _state.update { it.copy(status = getString(R.string.status_starting_download)) }

                    // Start observing progress before requesting download
                    val progressJob = viewModelScope.launch {
                        downloaderInstance.observeDownloadProgress(MODEL_NAME, QUANTIZATION).collect { progress ->
                            if (progress != null) {
                                val percentage = if (progress.totalSizeInBytes > 0) {
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

                    // Wait until download completes
                    downloaderInstance.observeDownloadProgress(MODEL_NAME, QUANTIZATION).first { progress ->
                        progress == null &&
                            downloaderInstance.queryStatus(MODEL_NAME, QUANTIZATION) is
                                LeapModelDownloader.ModelDownloadStatus.Downloaded
                    }

                    // Cancel the progress collection job
                    progressJob.cancel()
                    _state.update { it.copy(status = getString(R.string.status_download_complete)) }
                }

                // Load the model
                _state.update { it.copy(status = getString(R.string.status_loading_model)) }
                modelRunner = downloaderInstance.loadModel(
                    modelName = MODEL_NAME,
                    quantizationType = QUANTIZATION
                )

                // Create initial conversation
                conversation = modelRunner!!.createConversation(getString(R.string.system_prompt_audio))

                _state.update {
                    it.copy(
                        isModelLoading = false,
                        isModelLoaded = true,
                        status = getString(R.string.status_ready),
                        messages = it.messages + AudioDemoMessage(
                            role = ChatMessage.Role.ASSISTANT,
                            text = getString(R.string.model_loaded, MODEL_NAME, QUANTIZATION)
                        )
                    )
                }
            } catch (e: Exception) {
                val errorMessage = when {
                    e.message?.contains("space", ignoreCase = true) == true ->
                        getString(R.string.error_storage_space)
                    e.message?.contains("network", ignoreCase = true) == true ||
                    e.message?.contains("connection", ignoreCase = true) == true ->
                        getString(R.string.error_network)
                    else -> getString(R.string.error_load_model, e.message ?: "Unknown error")
                }

                _state.update {
                    it.copy(
                        isModelLoading = false,
                        status = null,
                        loadError = errorMessage,
                        canRetryLoad = true
                    )
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

        val message = ChatMessage(
            role = ChatMessage.Role.USER,
            content = listOf(ChatMessageContent.Text(trimmed))
        )

        _state.update {
            it.copy(
                inputText = "",
                messages = it.messages + AudioDemoMessage(
                    role = ChatMessage.Role.USER,
                    text = trimmed
                )
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
        val wavData = floatArrayToWav(samples, sampleRate)
        val content = ChatMessageContent.Audio(wavData)
        val message = ChatMessage(
            role = ChatMessage.Role.USER,
            content = listOf(content)
        )

        val display = getString(R.string.audio_prompt_format, samples.size, sampleRate)

        _state.update {
            it.copy(
                messages = it.messages + AudioDemoMessage(
                    role = ChatMessage.Role.USER,
                    text = display,
                    audioData = samples,
                    sampleRate = sampleRate
                )
            )
        }

        streamResponse(message)
    }

    private fun toggleRecording() {
        _state.update { it.copy(isRecording = !it.isRecording) }
        if (_state.value.isRecording) {
            _state.update { it.copy(status = getString(R.string.status_recording)) }
        }
    }

    private fun recordingFailed() {
        _state.update {
            it.copy(
                status = getString(R.string.error_recording_failed),
                isRecording = false
            )
        }
    }

    private fun playAudio(messageId: String, audioData: FloatArray, sampleRate: Int) {
        _state.update { it.copy(playingMessageId = messageId) }
        audioPlayer.play(audioData, sampleRate)
        // Playback state will be reset by onPlaybackCompleted callback
    }

    private fun stopAudioPlayback() {
        audioPlayer.stop()
        _state.update { it.copy(playingMessageId = null) }
    }

    private fun stopGeneration() {
        generationJob?.cancel()
        generationJob = null
        audioPlayer.stopStreaming()
        _state.update {
            it.copy(
                isGenerating = false,
                isStreamingAudio = false,
                streamingText = "",
                status = getString(R.string.status_ready)
            )
        }
    }

    private fun streamResponse(message: ChatMessage) {
        val currentModelRunner = modelRunner ?: run {
            _state.update { it.copy(status = getString(R.string.error_model_not_ready)) }
            return
        }

        generationJob = viewModelScope.launch {
            // Create conversation - either fresh (single-turn) or reuse existing (multi-turn)
            if (conversation == null || !ENABLE_MULTI_TURN) {
                try {
                    conversation = currentModelRunner.createConversation(getString(R.string.system_prompt_audio))
                } catch (e: Exception) {
                    _state.update {
                        it.copy(
                            status = getString(R.string.error_conversation_create, e.message ?: "Unknown error"),
                            isGenerating = false
                        )
                    }
                    return@launch
                }
            }

            val currentConversation = conversation ?: run {
                _state.update { it.copy(status = getString(R.string.error_conversation_not_initialized)) }
                return@launch
            }
            _state.update {
                it.copy(
                    streamingText = "",
                    status = getString(R.string.status_awaiting_response),
                    isGenerating = true
                )
            }

            audioPlayer.reset()
            val responseTextBuilder = StringBuilder()
            val streamingTextBuilder = StringBuilder()
            val audioSamplesList = mutableListOf<Float>()
            var audioSampleRate = 24000
            var isAudioStreamStarted = false

            try {
                currentConversation.generateResponse(message).collect { event ->
                    when (event) {
                        is MessageResponse.Chunk -> {
                            responseTextBuilder.append(event.text)
                            streamingTextBuilder.append(event.text)
                            _state.update {
                                it.copy(streamingText = streamingTextBuilder.toString())
                            }
                        }
                        is MessageResponse.ReasoningChunk -> {
                            _state.update { it.copy(status = getString(R.string.status_thinking)) }
                        }
                        is MessageResponse.AudioSample -> {
                            audioSamplesList.addAll(event.samples.toList())
                            audioSampleRate = event.sampleRate

                            // Start streaming on first audio sample
                            if (!isAudioStreamStarted) {
                                audioPlayer.startStreaming(audioSampleRate)
                                isAudioStreamStarted = true
                                _state.update { it.copy(isStreamingAudio = true) }
                            }

                            // Write samples to stream immediately
                            audioPlayer.writeStream(event.samples)
                            _state.update { it.copy(status = getString(R.string.status_streaming_audio)) }
                        }
                        is MessageResponse.Complete -> {
                            // Stop audio streaming
                            if (isAudioStreamStarted) {
                                audioPlayer.stopStreaming()
                            }

                            val finalText = responseTextBuilder.toString().trim()
                            val audioData = if (audioSamplesList.isNotEmpty()) {
                                audioSamplesList.toFloatArray()
                            } else null

                            _state.update {
                                it.copy(
                                    messages = it.messages + AudioDemoMessage(
                                        role = ChatMessage.Role.ASSISTANT,
                                        text = if (finalText.isEmpty()) getString(R.string.audio_response_placeholder) else finalText,
                                        audioData = audioData,
                                        sampleRate = audioSampleRate
                                    ),
                                    streamingText = "",
                                    isGenerating = false,
                                    isStreamingAudio = false,
                                    status = if (audioData != null) {
                                        getString(R.string.status_response_complete_with_audio)
                                    } else {
                                        getString(R.string.status_response_complete)
                                    }
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
                        isGenerating = false,
                        isStreamingAudio = false,
                        streamingText = "",
                        status = getString(R.string.error_generation_failed, e.message ?: "Unknown error"),
                        playingMessageId = null
                    )
                }
                Log.e(TAG, "Response generation failed", e)
            }
        }
    }

    private fun floatArrayToWav(samples: FloatArray, sampleRate: Int): ByteArray {
        val byteRate = sampleRate * 2 // 16-bit = 2 bytes per sample
        val dataSize = samples.size * 2

        val output = ByteArrayOutputStream()

        // WAV header
        output.write("RIFF".toByteArray())
        output.write(intToBytes(36 + dataSize)) // File size - 8
        output.write("WAVE".toByteArray())
        output.write("fmt ".toByteArray())
        output.write(intToBytes(16)) // PCM header size
        output.write(shortToBytes(1)) // PCM format
        output.write(shortToBytes(1)) // Mono
        output.write(intToBytes(sampleRate))
        output.write(intToBytes(byteRate))
        output.write(shortToBytes(2)) // Block align
        output.write(shortToBytes(16)) // Bits per sample
        output.write("data".toByteArray())
        output.write(intToBytes(dataSize))

        // Convert float samples to 16-bit PCM
        val buffer = ByteBuffer.allocate(samples.size * 2).order(ByteOrder.LITTLE_ENDIAN)
        for (sample in samples) {
            val pcm = (sample * 32767).toInt().coerceIn(-32768, 32767).toShort()
            buffer.putShort(pcm)
        }
        output.write(buffer.array())

        return output.toByteArray()
    }

    private fun intToBytes(value: Int): ByteArray {
        return byteArrayOf(
            (value and 0xFF).toByte(),
            (value shr 8 and 0xFF).toByte(),
            (value shr 16 and 0xFF).toByte(),
            (value shr 24 and 0xFF).toByte()
        )
    }

    private fun shortToBytes(value: Int): ByteArray {
        return byteArrayOf(
            (value and 0xFF).toByte(),
            (value shr 8 and 0xFF).toByte()
        )
    }

    override fun onCleared() {
        super.onCleared()
        try {
            audioPlayer.release()
        } catch (e: Exception) {
            Log.e(TAG, "Error releasing audio player", e)
        }

        // Use runBlocking to ensure model is unloaded before ViewModel is destroyed
        // viewModelScope is cancelled during clearing, so we need a non-cancelled context
        try {
            runBlocking(Dispatchers.IO) {
                modelRunner?.unload()
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error unloading model", e)
        }
    }
}

// Supporting data classes and sealed interface moved to bottom per AGENTS.md guidelines
data class AudioDemoMessage(
    val id: String = java.util.UUID.randomUUID().toString(),
    val role: ChatMessage.Role,
    val text: String,
    val audioData: FloatArray? = null,
    val sampleRate: Int = 0
) {
    val isUser: Boolean get() = role == ChatMessage.Role.USER
}

data class AudioDemoState(
    val messages: List<AudioDemoMessage> = emptyList(),
    val inputText: String = "",
    val status: String? = null,
    val streamingText: String = "",
    val isModelLoading: Boolean = false,
    val isGenerating: Boolean = false,
    val isRecording: Boolean = false,
    val isModelLoaded: Boolean = false,
    val loadError: String? = null,
    val canRetryLoad: Boolean = false,
    val playingMessageId: String? = null,
    val isStreamingAudio: Boolean = false
)

sealed interface AudioDemoEvent {
    data object LoadModel : AudioDemoEvent
    data object RetryLoadModel : AudioDemoEvent
    data class UpdateInputText(val text: String) : AudioDemoEvent
    data object SendTextPrompt : AudioDemoEvent
    data class SendAudioPrompt(val samples: FloatArray, val sampleRate: Int) : AudioDemoEvent
    data object ToggleRecording : AudioDemoEvent
    data object RecordingFailed : AudioDemoEvent
    data class PlayAudio(val messageId: String, val audioData: FloatArray, val sampleRate: Int) : AudioDemoEvent
    data object StopAudioPlayback : AudioDemoEvent
    data object StopGeneration : AudioDemoEvent
}
