package com.tldw.app.domain.model

/**
 * Domain model representing a fetched YouTube transcript.
 *
 * @property videoId The YouTube video ID.
 * @property language Human-readable language name (e.g. "English").
 * @property languageCode BCP-47 language code (e.g. "en").
 * @property isGenerated `true` if this is an auto-generated transcript; `false` if manually
 *   created.
 * @property snippets Ordered list of timed transcript snippets.
 */
data class Transcript(
  val videoId: String,
  val language: String,
  val languageCode: String,
  val isGenerated: Boolean,
  val snippets: List<TranscriptSnippet>,
  val videoInfo: VideoInfo? = null,
) {
  /** Returns the full transcript as a single plain-text string. */
  fun toFullText(): String = snippets.joinToString(separator = " ") { it.text }
}
