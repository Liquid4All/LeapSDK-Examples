package com.tldw.app.domain.repository

import com.tldw.app.domain.model.DownloadProgress
import kotlinx.coroutines.flow.Flow

interface ModelRepository {
    suspend fun isModelDownloaded(): Boolean

    fun downloadModel(): Flow<DownloadProgress>

    suspend fun stopDownload()

    suspend fun loadModel(): Result<Unit>

    suspend fun unloadModel()

    fun generateTldr(transcript: String): Flow<String>
}
