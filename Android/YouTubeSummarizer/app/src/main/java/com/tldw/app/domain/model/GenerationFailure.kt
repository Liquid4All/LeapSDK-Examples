package com.tldw.app.domain.model

data class GenerationFailure(
  val errorCode: String = "INFERENCE_TIMEOUT",
  val elapsedSeconds: Float = 0f,
)
