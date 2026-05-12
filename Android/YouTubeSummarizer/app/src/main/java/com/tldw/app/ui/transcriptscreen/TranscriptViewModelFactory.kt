package com.tldw.app.ui.transcriptscreen

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import com.tldw.app.domain.usecase.CheckModelDownloadedUseCase
import com.tldw.app.domain.usecase.FetchTranscriptUseCase
import com.tldw.app.domain.usecase.GenerateTldrUseCase
import com.tldw.app.domain.usecase.LoadModelUseCase
import com.tldw.app.domain.usecase.UnloadModelUseCase

class TranscriptViewModelFactory(
    private val fetchTranscriptUseCase: FetchTranscriptUseCase,
    private val checkModelDownloadedUseCase: CheckModelDownloadedUseCase,
    private val loadModelUseCase: LoadModelUseCase,
    private val generateTldrUseCase: GenerateTldrUseCase,
    private val unloadModelUseCase: UnloadModelUseCase,
) : ViewModelProvider.Factory {

    @Suppress("UNCHECKED_CAST")
    override fun <T : ViewModel> create(modelClass: Class<T>): T {
        if (modelClass.isAssignableFrom(TranscriptViewModel::class.java)) {
            return TranscriptViewModel(
                fetchTranscriptUseCase = fetchTranscriptUseCase,
                checkModelDownloadedUseCase = checkModelDownloadedUseCase,
                loadModelUseCase = loadModelUseCase,
                generateTldrUseCase = generateTldrUseCase,
                unloadModelUseCase = unloadModelUseCase,
            ) as T
        }
        throw IllegalArgumentException("Unknown ViewModel class: ${modelClass.name}")
    }
}
