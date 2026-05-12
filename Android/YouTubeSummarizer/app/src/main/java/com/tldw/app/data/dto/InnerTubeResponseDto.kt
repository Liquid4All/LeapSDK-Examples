package com.tldw.app.data.dto

import com.google.gson.annotations.SerializedName

/** Top-level response DTO from the YouTube InnerTube player endpoint. */
data class InnerTubeResponseDto(
    @SerializedName("playabilityStatus") val playabilityStatus: PlayabilityStatusDto?,
    @SerializedName("captions") val captions: CaptionsDto?,
    @SerializedName("videoDetails") val videoDetails: VideoDetailsDto?,
)

data class VideoDetailsDto(
    @SerializedName("videoId") val videoId: String?,
    @SerializedName("title") val title: String?,
    @SerializedName("lengthSeconds") val lengthSeconds: String?,
    @SerializedName("author") val author: String?,
    @SerializedName("viewCount") val viewCount: String?,
)

data class PlayabilityStatusDto(
    @SerializedName("status") val status: String?,
    @SerializedName("reason") val reason: String?,
    @SerializedName("errorScreen") val errorScreen: ErrorScreenDto?,
)

data class ErrorScreenDto(
    @SerializedName("playerErrorMessageRenderer")
    val playerErrorMessageRenderer: PlayerErrorMessageRendererDto?
)

data class PlayerErrorMessageRendererDto(@SerializedName("subreason") val subreason: SubreasonDto?)

data class SubreasonDto(@SerializedName("runs") val runs: List<RunDto>?)

data class RunDto(@SerializedName("text") val text: String?)

data class CaptionsDto(
    @SerializedName("playerCaptionsTracklistRenderer")
    val playerCaptionsTracklistRenderer: PlayerCaptionsTracklistRendererDto?
)

data class PlayerCaptionsTracklistRendererDto(
    @SerializedName("captionTracks") val captionTracks: List<CaptionTrackDto>?,
    @SerializedName("translationLanguages") val translationLanguages: List<TranslationLanguageDto>?,
)

data class CaptionTrackDto(
    @SerializedName("baseUrl") val baseUrl: String,
    @SerializedName("name") val name: NameDto?,
    @SerializedName("languageCode") val languageCode: String,
    @SerializedName("kind") val kind: String?,
    @SerializedName("isTranslatable") val isTranslatable: Boolean?,
)

data class NameDto(@SerializedName("runs") val runs: List<RunDto>?)

data class TranslationLanguageDto(
    @SerializedName("languageName") val languageName: LanguageNameDto?,
    @SerializedName("languageCode") val languageCode: String?,
)

data class LanguageNameDto(@SerializedName("runs") val runs: List<RunDto>?)
