package com.tldw.app.domain.usecase

import com.tldw.app.domain.repository.ModelRepository

class UnloadModelUseCase(private val repository: ModelRepository) {
  suspend operator fun invoke() = repository.unloadModel()
}
