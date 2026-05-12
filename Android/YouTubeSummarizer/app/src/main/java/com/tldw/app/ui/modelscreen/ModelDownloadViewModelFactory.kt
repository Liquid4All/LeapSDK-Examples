package com.tldw.app.ui.modelscreen

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import com.tldw.app.domain.usecase.DownloadModelUseCase

class ModelDownloadViewModelFactory(
    private val downloadModelUseCase: DownloadModelUseCase,
) : ViewModelProvider.Factory {
    @Suppress("UNCHECKED_CAST")
    override fun <T : ViewModel> create(modelClass: Class<T>): T {
        if (modelClass.isAssignableFrom(ModelDownloadViewModel::class.java)) {
            return ModelDownloadViewModel(
                downloadModelUseCase = downloadModelUseCase,
            ) as T
        }
        throw IllegalArgumentException("Unknown ViewModel class: ${modelClass.name}")
    }
}
