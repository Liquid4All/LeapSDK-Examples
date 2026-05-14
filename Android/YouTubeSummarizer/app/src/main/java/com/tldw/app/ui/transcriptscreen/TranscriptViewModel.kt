package com.tldw.app.ui.transcriptscreen

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.tldw.app.domain.model.GenerationFailure
import com.tldw.app.domain.usecase.CheckModelDownloadedUseCase
import com.tldw.app.domain.usecase.FetchTranscriptUseCase
import com.tldw.app.domain.usecase.GenerateTldrUseCase
import com.tldw.app.domain.usecase.LoadModelUseCase
import com.tldw.app.domain.usecase.UnloadModelUseCase
import com.tldw.app.domain.util.YouTubeUrlParser
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Job
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

class TranscriptViewModel(
  private val fetchTranscriptUseCase: FetchTranscriptUseCase,
  private val checkModelDownloadedUseCase: CheckModelDownloadedUseCase,
  private val loadModelUseCase: LoadModelUseCase,
  private val generateTldrUseCase: GenerateTldrUseCase,
  private val unloadModelUseCase: UnloadModelUseCase,
) : ViewModel() {

  private val _state = MutableStateFlow(TranscriptState())
  val state: StateFlow<TranscriptState> = _state.asStateFlow()

  private var transcriptText: String? = null
  private var fetchJob: Job? = null
  private var generateJob: Job? = null

  fun onEvent(event: TranscriptEvent) {
    when (event) {
      is TranscriptEvent.UrlChanged -> _state.update { it.copy(url = event.url, urlError = false) }
      is TranscriptEvent.Submit -> submitUrl()
      is TranscriptEvent.Cancel -> resetToIdle()
      is TranscriptEvent.RetryGeneration -> retryGeneration()
      is TranscriptEvent.RegenerateTldr -> retryGeneration()
      is TranscriptEvent.DismissError -> _state.update { it.copy(errorMessage = null) }
    }
  }

  private fun submitUrl() {
    val url = _state.value.url.trim()
    if (url.isBlank()) return
    if (YouTubeUrlParser.extractVideoId(url) == null) {
      _state.update { it.copy(urlError = true) }
      return
    }
    startFetch()
  }

  private fun startFetch() {
    val url = _state.value.url.trim()
    if (url.isBlank()) return
    _state.update {
      it.copy(phase = TranscriptPhase.Fetching, urlError = false, errorMessage = null)
    }
    fetchJob = viewModelScope.launch {
      fetchTranscriptUseCase(url)
        .onSuccess { transcript ->
          transcriptText = transcript.toFullText()
          val wordCountIn = countWords(transcriptText!!)
          _state.update {
            it.copy(
              phase = TranscriptPhase.Generating,
              videoInfo = transcript.videoInfo,
              wordCountIn = wordCountIn,
            )
          }
          generateTldr(transcriptText!!)
        }
        .onFailure { error ->
          if (isNoCaptionsError(error)) {
            _state.update { it.copy(phase = TranscriptPhase.FailedNoCaptions) }
          } else {
            _state.update {
              it.copy(
                phase = TranscriptPhase.Idle,
                errorMessage = error.message ?: "Failed to fetch transcript",
              )
            }
          }
        }
    }
  }

  private fun generateTldr(text: String) {
    generateJob?.cancel()
    generateJob = viewModelScope.launch {
      val isDownloaded = checkModelDownloadedUseCase()
      if (!isDownloaded) {
        _state.update { it.copy(isModelNotDownloaded = true) }
        return@launch
      }
      _state.update { it.copy(isModelNotDownloaded = false) }

      val loadResult = loadModelUseCase()
      if (loadResult.isFailure) {
        _state.update {
          it.copy(
            phase = TranscriptPhase.FailedGeneration,
            generationFailure =
              GenerationFailure(
                errorCode =
                  loadResult.exceptionOrNull()?.javaClass?.simpleName?.uppercase() ?: "LOAD_ERROR",
                elapsedSeconds = 0f,
              ),
          )
        }
        return@launch
      }

      val startTime = System.currentTimeMillis()
      try {
        generateTldrUseCase(text).collect { partial ->
          _state.update { it.copy(tldrText = partial) }
        }
        val elapsed = (System.currentTimeMillis() - startTime) / 1000f
        val tldr = _state.value.tldrText ?: ""
        _state.update {
          it.copy(
            phase = TranscriptPhase.Done,
            wordCountOut = countWords(tldr),
            generationTimeSeconds = elapsed,
          )
        }
      } catch (e: CancellationException) {
        throw e
      } catch (e: Exception) {
        val elapsed = (System.currentTimeMillis() - startTime) / 1000f
        _state.update {
          it.copy(
            phase = TranscriptPhase.FailedGeneration,
            generationFailure =
              GenerationFailure(
                errorCode =
                  e.javaClass.simpleName.uppercase().take(24).ifEmpty { "INFERENCE_TIMEOUT" },
                elapsedSeconds = elapsed,
              ),
          )
        }
      } finally {
        // Use NonCancellable to ensure model unload completes even if job is cancelled
        viewModelScope.launch(NonCancellable) { unloadModelUseCase() }
      }
    }
  }

  private fun retryGeneration() {
    val text = transcriptText ?: return
    _state.update {
      it.copy(phase = TranscriptPhase.Generating, generationFailure = null, tldrText = null)
    }
    generateTldr(text)
  }

  private fun resetToIdle() {
    fetchJob?.cancel()
    generateJob?.cancel()
    _state.update { TranscriptState(url = _state.value.url) }
  }

  private fun isNoCaptionsError(error: Throwable): Boolean {
    val msg = error.message ?: return false
    return msg.contains("No caption", ignoreCase = true) ||
      msg.contains("Transcripts are disabled", ignoreCase = true) ||
      msg.contains("No transcript", ignoreCase = true)
  }

  private fun countWords(text: String): Int = text.split("\\s+".toRegex()).count { it.isNotEmpty() }

  fun handleSharedUrl(url: String) {
    _state.update { it.copy(url = url) }
    startFetch()
  }
}
