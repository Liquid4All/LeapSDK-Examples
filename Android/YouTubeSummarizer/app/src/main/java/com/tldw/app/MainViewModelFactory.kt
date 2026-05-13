package com.tldw.app

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import com.tldw.app.domain.usecase.CheckModelDownloadedUseCase

class MainViewModelFactory(private val checkModelDownloadedUseCase: CheckModelDownloadedUseCase) :
  ViewModelProvider.Factory {
  @Suppress("UNCHECKED_CAST")
  override fun <T : ViewModel> create(modelClass: Class<T>): T {
    if (modelClass.isAssignableFrom(MainViewModel::class.java)) {
      return MainViewModel(checkModelDownloadedUseCase) as T
    }
    throw IllegalArgumentException("Unknown ViewModel class")
  }
}
