package com.tldw.app.domain.model

import java.util.Locale

data class VideoInfo(
  val videoId: String,
  val title: String,
  val channelName: String,
  val durationSeconds: Long,
  val viewCount: Long,
) {
  fun formattedDuration(): String {
    val h = durationSeconds / 3600
    val m = (durationSeconds % 3600) / 60
    val s = durationSeconds % 60
    return if (h > 0) "$h:${m.toString().padStart(2, '0')}:${s.toString().padStart(2, '0')}"
    else "$m:${s.toString().padStart(2, '0')}"
  }

  fun thumbnailUrl(): String = "https://img.youtube.com/vi/$videoId/mqdefault.jpg"

  fun formattedDurationShort(): String = "${durationSeconds / 60}m"

  fun formattedViewCount(): String =
    when {
      viewCount >= 1_000_000 -> "${viewCount / 1_000_000}M"
      viewCount >= 1_000 -> "${viewCount / 1_000}K"
      else -> "$viewCount"
    }
}

fun Int.formatWordCount(): String =
  when {
    this >= 10_000 -> "${this / 1_000}K"
    this >= 1_000 -> String.format(Locale.US, "%.1fK", this / 1000.0)
    else -> "$this"
  }
