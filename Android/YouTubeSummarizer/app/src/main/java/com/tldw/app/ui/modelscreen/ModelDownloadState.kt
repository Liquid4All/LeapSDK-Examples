package com.tldw.app.ui.modelscreen

import com.tldw.app.domain.model.DownloadFailure
import com.tldw.app.domain.model.DownloadProgress

data class ModelDownloadState(
    val isDownloading: Boolean = false,
    val isModelReady: Boolean = false,
    val downloadProgress: DownloadProgress? = null,
    val downloadFailure: DownloadFailure? = null,
    val errorMessage: String? = null,
)
