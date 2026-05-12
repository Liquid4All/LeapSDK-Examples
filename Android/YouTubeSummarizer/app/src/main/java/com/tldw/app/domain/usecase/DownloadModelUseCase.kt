package com.tldw.app.domain.usecase

import com.tldw.app.domain.model.DownloadProgress
import com.tldw.app.domain.repository.ModelRepository
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.onCompletion
import kotlinx.coroutines.withContext

class DownloadModelUseCase(private val repository: ModelRepository) {
    operator fun invoke(): Flow<DownloadProgress> =
        repository.downloadModel().onCompletion { cause ->
            if (cause is CancellationException) {
                withContext(NonCancellable) { repository.stopDownload() }
            }
        }
}
