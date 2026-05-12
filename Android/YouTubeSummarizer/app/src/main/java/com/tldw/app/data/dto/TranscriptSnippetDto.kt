package com.tldw.app.data.dto

/** Represents a single snippet from the raw transcript XML response. */
data class TranscriptSnippetDto(val text: String, val start: Float, val duration: Float)
