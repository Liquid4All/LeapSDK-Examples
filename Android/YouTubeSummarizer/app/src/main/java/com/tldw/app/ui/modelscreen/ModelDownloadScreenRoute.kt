package com.tldw.app.ui.modelscreen

import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.platform.LocalContext
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.tldw.app.data.repository.ModelRepositoryImpl
import com.tldw.app.domain.usecase.DownloadModelUseCase

@Composable
fun ModelDownloadScreenRoute(onModelReady: () -> Unit) {
  val context = LocalContext.current
  val viewModel: ModelDownloadViewModel = viewModel {
    val modelRepository = ModelRepositoryImpl(context.applicationContext)
    ModelDownloadViewModelFactory(downloadModelUseCase = DownloadModelUseCase(modelRepository))
      .create(ModelDownloadViewModel::class.java)
  }
  val state by viewModel.state.collectAsStateWithLifecycle()
  ModelDownloadScreen(state = state, onEvent = viewModel::onEvent, onModelReady = onModelReady)
}
