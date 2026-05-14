package com.tldw.app.domain.model

sealed class DownloadFailure {
  data object NoInternet : DownloadFailure()

  data class Interrupted(val errorCode: String = "NETWORK_TIMEOUT", val savedBytes: Long = 0L) :
    DownloadFailure()
}
