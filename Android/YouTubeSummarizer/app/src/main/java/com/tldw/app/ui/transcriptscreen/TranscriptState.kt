package com.tldw.app.ui.transcriptscreen

import com.tldw.app.domain.model.GenerationFailure
import com.tldw.app.domain.model.VideoInfo

enum class TranscriptPhase {
  Idle,
  Fetching,
  Generating,
  Done,
  FailedNoCaptions,
  FailedGeneration,
}

data class TranscriptState(
  val url: String = "",
  val urlError: Boolean = false,
  val phase: TranscriptPhase = TranscriptPhase.Idle,
  val videoInfo: VideoInfo? = null,
  val wordCountIn: Int = 0,
  val wordCountOut: Int = 0,
  val tldrText: String? = null,
  val generationTimeSeconds: Float = 0f,
  val generationFailure: GenerationFailure? = null,
  val isModelNotDownloaded: Boolean = false,
  val errorMessage: String? = null,
)
