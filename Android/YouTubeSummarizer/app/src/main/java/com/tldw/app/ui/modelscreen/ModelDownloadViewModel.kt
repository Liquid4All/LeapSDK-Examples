package com.tldw.app.ui.modelscreen

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.tldw.app.domain.model.DownloadFailure
import com.tldw.app.domain.usecase.DownloadModelUseCase
import java.net.ConnectException
import java.net.UnknownHostException
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.collect
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

class ModelDownloadViewModel(private val downloadModelUseCase: DownloadModelUseCase) : ViewModel() {

  private val _state = MutableStateFlow(ModelDownloadState())
  val state: StateFlow<ModelDownloadState> = _state.asStateFlow()

  private var downloadJob: Job? = null

  fun onEvent(event: ModelDownloadEvent) {
    when (event) {
      ModelDownloadEvent.StartDownload -> startDownload()
      ModelDownloadEvent.PauseDownload -> pauseDownload()
      ModelDownloadEvent.RetryDownload -> startDownload()
      ModelDownloadEvent.DismissError ->
        _state.update { it.copy(errorMessage = null, downloadFailure = null) }
    }
  }

  private fun startDownload() {
    _state.update { it.copy(downloadFailure = null, errorMessage = null) }
    downloadJob = viewModelScope.launch {
      _state.update { it.copy(isDownloading = true) }
      try {
        downloadModelUseCase().collect { progress ->
          _state.update { it.copy(downloadProgress = progress) }
        }
        _state.update {
          it.copy(isDownloading = false, isModelReady = true, downloadProgress = null)
        }
      } catch (e: CancellationException) {
        throw e
      } catch (e: Exception) {
        val failure =
          when {
            e.isNoInternetError() -> DownloadFailure.NoInternet
            else ->
              DownloadFailure.Interrupted(
                errorCode =
                  e.javaClass.simpleName.uppercase().take(24).ifEmpty { "NETWORK_TIMEOUT" },
                savedBytes = _state.value.downloadProgress?.downloadedBytes ?: 0L,
              )
          }
        _state.update { it.copy(isDownloading = false, downloadFailure = failure) }
      }
    }
  }

  private fun pauseDownload() {
    downloadJob?.cancel()
    _state.update { it.copy(isDownloading = false, downloadProgress = null) }
  }

  private fun Exception.isNoInternetError(): Boolean =
    this is UnknownHostException ||
      this is ConnectException ||
      cause is UnknownHostException ||
      cause is ConnectException
}
