package com.tldw.app.domain.util

/**
 * Extracts the YouTube video ID from various URL formats.
 *
 * Supported formats:
 * - `https://www.youtube.com/watch?v=VIDEO_ID`
 * - `https://youtu.be/VIDEO_ID`
 * - `https://m.youtube.com/watch?v=VIDEO_ID`
 * - `https://www.youtube.com/shorts/VIDEO_ID`
 * - A bare video ID (11 characters, alphanumeric + `-` + `_`)
 */
object YouTubeUrlParser {

  private val VIDEO_ID_PATTERN = Regex("[a-zA-Z0-9_-]{11}")

  /**
   * Parses a YouTube URL or bare video ID and returns the video ID, or `null` if the input cannot
   * be parsed.
   */
  fun extractVideoId(input: String): String? {
    val trimmed = input.trim()

    // youtu.be short links: https://youtu.be/VIDEO_ID
    if (trimmed.contains("youtu.be/")) {
      val id = trimmed.substringAfter("youtu.be/").substringBefore("?").substringBefore("&")
      if (VIDEO_ID_PATTERN.matches(id)) return id
    }

    // youtube.com/shorts/VIDEO_ID
    if (trimmed.contains("/shorts/")) {
      val id = trimmed.substringAfter("/shorts/").substringBefore("?").substringBefore("&")
      if (VIDEO_ID_PATTERN.matches(id)) return id
    }

    // Standard watch URL: ?v=VIDEO_ID
    if (trimmed.contains("v=")) {
      val id = trimmed.substringAfter("v=").substringBefore("&").substringBefore("#")
      if (VIDEO_ID_PATTERN.matches(id)) return id
    }

    // Bare video ID
    if (VIDEO_ID_PATTERN.matches(trimmed)) return trimmed

    return null
  }
}
