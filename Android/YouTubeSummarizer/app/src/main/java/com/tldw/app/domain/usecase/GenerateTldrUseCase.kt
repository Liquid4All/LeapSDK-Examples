package com.tldw.app.domain.usecase

import com.tldw.app.domain.repository.ModelRepository
import kotlinx.coroutines.flow.Flow

class GenerateTldrUseCase(private val repository: ModelRepository) {
    operator fun invoke(transcript: String): Flow<String> = repository.generateTldr(transcript)
}
