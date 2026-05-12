package com.tldw.app.domain.util

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class YouTubeUrlParserTest {

    @Test
    fun `extract video id from standard watch url`() {
        val result = YouTubeUrlParser.extractVideoId("https://www.youtube.com/watch?v=dQw4w9WgXcQ")
        assertEquals("dQw4w9WgXcQ", result)
    }

    @Test
    fun `extract video id from short url`() {
        val result = YouTubeUrlParser.extractVideoId("https://youtu.be/dQw4w9WgXcQ")
        assertEquals("dQw4w9WgXcQ", result)
    }

    @Test
    fun `extract video id from mobile url`() {
        val result = YouTubeUrlParser.extractVideoId("https://m.youtube.com/watch?v=dQw4w9WgXcQ")
        assertEquals("dQw4w9WgXcQ", result)
    }

    @Test
    fun `extract video id from shorts url`() {
        val result = YouTubeUrlParser.extractVideoId("https://www.youtube.com/shorts/dQw4w9WgXcQ")
        assertEquals("dQw4w9WgXcQ", result)
    }

    @Test
    fun `extract bare video id`() {
        val result = YouTubeUrlParser.extractVideoId("dQw4w9WgXcQ")
        assertEquals("dQw4w9WgXcQ", result)
    }

    @Test
    fun `return null for invalid url`() {
        val result = YouTubeUrlParser.extractVideoId("not-a-youtube-url")
        assertNull(result)
    }

    @Test
    fun `return null for empty string`() {
        val result = YouTubeUrlParser.extractVideoId("")
        assertNull(result)
    }

    @Test
    fun `trim whitespace before parsing`() {
        val result = YouTubeUrlParser.extractVideoId("  https://youtu.be/dQw4w9WgXcQ  ")
        assertEquals("dQw4w9WgXcQ", result)
    }

    @Test
    fun `url with extra query params extracts correct id`() {
        val result =
            YouTubeUrlParser.extractVideoId(
                "https://www.youtube.com/watch?v=dQw4w9WgXcQ&t=42s&feature=shared"
            )
        assertEquals("dQw4w9WgXcQ", result)
    }
}
