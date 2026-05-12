package com.tldw.app

import com.tldw.app.domain.model.VideoInfo

object Consts {
    const val MODEL_NAME = "LFM2-350M"
    const val QUANTIZATION_SLUG = "Q8_0"
    const val TLDR_SYSTEM_PROMPT =
        "You are a helpful assistant. Given a YouTube video transcript, produce a concise TL;DR summary in 3-5 bullet points."
    const val MAX_TRANSCRIPT_CHARS = 4000

    val VIDEO_PRINTING_PRESS =
        VideoInfo(
            videoId = "Y0NopNiSkKw",
            title = "The Complete History of the Printing Press | How Printing Changed the World",
            channelName = "History of Innovations",
            durationSeconds = 595L,
            viewCount = 425L,
        )

    val VIDEO_SLEEP_SUPERPOWER =
        VideoInfo(
            videoId = "5MuIMqhT8DM",
            title = "Sleep Is Your Superpower | Matt Walker | TED",
            channelName = "TED",
            durationSeconds = 1158L,
            viewCount = 16505865L,
        )

    val VIDEO_BUILD_GPT =
        VideoInfo(
            videoId = "kCc8FmEb1nY",
            title = "Let's build GPT: from scratch, in code, spelled out.",
            channelName = "Andrej Karpathy",
            durationSeconds = 6980L,
            viewCount = 7168320L,
        )
}
