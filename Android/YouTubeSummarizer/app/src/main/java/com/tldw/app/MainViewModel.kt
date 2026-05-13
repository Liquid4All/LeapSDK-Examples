package com.tldw.app

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.tldw.app.domain.usecase.CheckModelDownloadedUseCase
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

data class MainState(val isCheckingStatus: Boolean = true, val isModelDownloaded: Boolean = false)

class MainViewModel(private val checkModelDownloadedUseCase: CheckModelDownloadedUseCase) :
  ViewModel() {

  private val _state = MutableStateFlow(MainState())
  val state: StateFlow<MainState> = _state.asStateFlow()

  init {
    checkStatus()
  }

  fun checkStatus() {
    viewModelScope.launch {
      _state.update { it.copy(isCheckingStatus = true) }
      try {
        val isDownloaded = checkModelDownloadedUseCase()
        _state.update { it.copy(isCheckingStatus = false, isModelDownloaded = isDownloaded) }
      } catch (e: Exception) {
        // If it fails, assume not downloaded or handle gracefully.
        _state.update { it.copy(isCheckingStatus = false, isModelDownloaded = false) }
      }
    }
  }
}
