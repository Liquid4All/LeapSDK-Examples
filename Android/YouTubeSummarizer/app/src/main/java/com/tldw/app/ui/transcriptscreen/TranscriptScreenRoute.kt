package com.tldw.app.ui.transcriptscreen

import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.ui.platform.LocalContext
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.tldw.app.data.repository.ModelRepositoryImpl
import com.tldw.app.data.repository.TranscriptRepositoryImpl
import com.tldw.app.domain.usecase.CheckModelDownloadedUseCase
import com.tldw.app.domain.usecase.FetchTranscriptUseCase
import com.tldw.app.domain.usecase.GenerateTldrUseCase
import com.tldw.app.domain.usecase.LoadModelUseCase
import com.tldw.app.domain.usecase.UnloadModelUseCase

@Composable
fun TranscriptScreenRoute(
    sharedUrl: String? = null,
    onNavigateToModelDownload: () -> Unit = {}
) {
    val context = LocalContext.current
    val viewModel: TranscriptViewModel = viewModel {
        val modelRepository = ModelRepositoryImpl(context.applicationContext)
        val transcriptRepository = TranscriptRepositoryImpl()
        TranscriptViewModelFactory(
            fetchTranscriptUseCase = FetchTranscriptUseCase(transcriptRepository),
            checkModelDownloadedUseCase = CheckModelDownloadedUseCase(modelRepository),
            loadModelUseCase = LoadModelUseCase(modelRepository),
            generateTldrUseCase = GenerateTldrUseCase(modelRepository),
            unloadModelUseCase = UnloadModelUseCase(modelRepository),
        )
            .create(TranscriptViewModel::class.java)
    }
    val state by viewModel.state.collectAsStateWithLifecycle()

    LaunchedEffect(sharedUrl) {
        if (sharedUrl != null) {
            viewModel.handleSharedUrl(sharedUrl)
        }
    }

    TranscriptScreen(
        state = state,
        onEvent = viewModel::onEvent,
        onNavigateToModelDownload = onNavigateToModelDownload,
    )
}
