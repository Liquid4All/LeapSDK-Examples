package com.tldw.app.domain.repository

import com.tldw.app.domain.model.Transcript

/** Contract for fetching YouTube transcripts. */
interface TranscriptRepository {

  /**
   * Fetches the transcript for the given [videoId].
   *
   * @param videoId The YouTube video ID (not the full URL).
   * @param languages Language codes in descending priority order (e.g. ["en"]).
   * @return [Result.success] with the [Transcript], or [Result.failure] with a descriptive
   *   exception.
   */
  suspend fun fetchTranscript(
    videoId: String,
    languages: List<String> = listOf("en"),
  ): Result<Transcript>
}
