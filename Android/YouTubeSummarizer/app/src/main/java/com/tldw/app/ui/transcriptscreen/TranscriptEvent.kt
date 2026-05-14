package com.tldw.app.ui.transcriptscreen

sealed interface TranscriptEvent {
  data class UrlChanged(val url: String) : TranscriptEvent

  data object Submit : TranscriptEvent

  data object Cancel : TranscriptEvent

  data object RetryGeneration : TranscriptEvent

  data object RegenerateTldr : TranscriptEvent

  data object DismissError : TranscriptEvent
}
