package com.tldw.app.domain.usecase

import com.tldw.app.domain.model.Transcript
import com.tldw.app.domain.repository.TranscriptRepository
import com.tldw.app.domain.util.YouTubeUrlParser

/**
 * Use case for fetching the transcript of a YouTube video.
 *
 * Accepts either a full YouTube URL or a bare video ID, extracts the video ID, and delegates
 * fetching to [TranscriptRepository].
 */
class FetchTranscriptUseCase(private val repository: TranscriptRepository) {

  /**
   * Fetches the transcript for the given YouTube [url] or video ID.
   *
   * @param url A YouTube URL (any supported format) or a bare 11-character video ID.
   * @param languages Language codes in descending priority (default: ["en"]).
   * @return [Result.success] with the [Transcript], or [Result.failure] if the URL is invalid or
   *   the network/parsing step fails.
   */
  suspend operator fun invoke(
    url: String,
    languages: List<String> = listOf("en"),
  ): Result<Transcript> {
    val videoId =
      YouTubeUrlParser.extractVideoId(url)
        ?: return Result.failure(IllegalArgumentException("Could not extract video ID from: $url"))
    return repository.fetchTranscript(videoId = videoId, languages = languages)
  }
}
