package com.tldw.app.domain.usecase

import com.tldw.app.domain.repository.ModelRepository

class LoadModelUseCase(private val repository: ModelRepository) {
    suspend operator fun invoke(): Result<Unit> = repository.loadModel()
}
