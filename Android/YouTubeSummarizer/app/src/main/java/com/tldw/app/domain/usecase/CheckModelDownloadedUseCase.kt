package com.tldw.app.domain.usecase

import com.tldw.app.domain.repository.ModelRepository

class CheckModelDownloadedUseCase(private val repository: ModelRepository) {
    suspend operator fun invoke(): Boolean = repository.isModelDownloaded()
}
