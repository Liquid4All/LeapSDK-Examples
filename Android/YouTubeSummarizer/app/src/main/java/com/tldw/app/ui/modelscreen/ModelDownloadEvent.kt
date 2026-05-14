package com.tldw.app.ui.modelscreen

sealed interface ModelDownloadEvent {
  data object StartDownload : ModelDownloadEvent

  data object PauseDownload : ModelDownloadEvent

  data object RetryDownload : ModelDownloadEvent

  data object DismissError : ModelDownloadEvent
}
