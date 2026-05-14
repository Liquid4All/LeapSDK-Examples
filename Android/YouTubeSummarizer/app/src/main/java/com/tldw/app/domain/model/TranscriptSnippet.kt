package com.tldw.app.domain.model

/**
 * Domain model representing a single timed snippet from a YouTube transcript.
 *
 * @property text The transcript text for this snippet.
 * @property start Time offset in seconds when this snippet appears on screen.
 * @property duration How long (in seconds) the snippet stays on screen.
 */
data class TranscriptSnippet(val text: String, val start: Float, val duration: Float)
