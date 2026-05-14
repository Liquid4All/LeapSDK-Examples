package com.tldw.app.data.repository

import com.tldw.app.data.dto.CaptionTrackDto
import com.tldw.app.data.remote.TranscriptXmlParser
import com.tldw.app.data.remote.YouTubeApiService
import com.tldw.app.domain.model.Transcript
import com.tldw.app.domain.model.TranscriptSnippet
import com.tldw.app.domain.model.VideoInfo
import com.tldw.app.domain.repository.TranscriptRepository
import java.io.IOException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/**
 * Concrete implementation of [TranscriptRepository] that fetches transcript data from YouTube.
 * 1. POST to the InnerTube player endpoint to get video details and captions metadata
 * 2. Find the best matching caption track for the requested language(s)
 * 3. Fetch and parse the transcript XML from the caption track URL
 */
class TranscriptRepositoryImpl(
  private val apiService: YouTubeApiService = YouTubeApiService(),
  private val xmlParser: TranscriptXmlParser = TranscriptXmlParser(),
) : TranscriptRepository {

  override suspend fun fetchTranscript(
    videoId: String,
    languages: List<String>,
  ): Result<Transcript> = runCatching {
    withContext(Dispatchers.IO) {
      val innerTubeResponse = apiService.fetchInnerTubeData(videoId)

      val playabilityStatus = innerTubeResponse.playabilityStatus
      if (playabilityStatus != null && playabilityStatus.status != "OK") {
        val reason = playabilityStatus.reason ?: "Unknown error"
        throw IOException("Video unavailable: $reason")
      }

      val captionTracklist =
        innerTubeResponse.captions?.playerCaptionsTracklistRenderer
          ?: throw IOException("Transcripts are disabled for video: $videoId")

      val captionTracks =
        captionTracklist.captionTracks
          ?: throw IOException("No caption tracks found for video: $videoId")

      val captionTrack =
        findBestCaptionTrack(captionTracks, languages)
          ?: throw IOException(
            "No transcript found for video $videoId in languages: $languages. " +
              "Available: ${captionTracks.joinToString { it.languageCode }}"
          )

      // Strip &fmt=srv3
      val transcriptUrl = captionTrack.baseUrl.replace("&fmt=srv3", "")
      val xml = apiService.fetchTranscriptXml(transcriptUrl)
      val snippetDtos = xmlParser.parse(xml)

      val details = innerTubeResponse.videoDetails
      val videoInfo =
        if (details?.title != null) {
          VideoInfo(
            videoId = videoId,
            title = details.title,
            channelName = details.author ?: "",
            durationSeconds = details.lengthSeconds?.toLongOrNull() ?: 0L,
            viewCount = details.viewCount?.toLongOrNull() ?: 0L,
          )
        } else null

      Transcript(
        videoId = videoId,
        language = captionTrack.name?.runs?.firstOrNull()?.text ?: captionTrack.languageCode,
        languageCode = captionTrack.languageCode,
        isGenerated = captionTrack.kind == "asr",
        snippets =
          snippetDtos.map { dto ->
            TranscriptSnippet(text = dto.text, start = dto.start, duration = dto.duration)
          },
        videoInfo = videoInfo,
      )
    }
  }

  /**
   * Finds the best matching caption track for the given language codes. Manually created
   * transcripts take precedence over auto-generated ones
   */
  private fun findBestCaptionTrack(
    tracks: List<CaptionTrackDto>,
    languages: List<String>,
  ): CaptionTrackDto? {
    val manualTracks = tracks.filter { it.kind != "asr" }
    val generatedTracks = tracks.filter { it.kind == "asr" }

    for (language in languages) {
      manualTracks
        .firstOrNull { it.languageCode == language }
        ?.let {
          return it
        }
    }
    for (language in languages) {
      generatedTracks
        .firstOrNull { it.languageCode == language }
        ?.let {
          return it
        }
    }
    return null
  }
}
