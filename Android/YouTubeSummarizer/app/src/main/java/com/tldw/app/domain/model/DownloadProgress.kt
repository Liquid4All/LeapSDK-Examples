package com.tldw.app.domain.model

data class DownloadProgress(val downloadedBytes: Long, val totalBytes: Long, val percentage: Int)
