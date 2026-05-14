package com.tldw.app.domain.usecase

import com.tldw.app.domain.model.Transcript
import com.tldw.app.domain.model.TranscriptSnippet
import com.tldw.app.domain.repository.TranscriptRepository
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.mockito.kotlin.any
import org.mockito.kotlin.mock
import org.mockito.kotlin.never
import org.mockito.kotlin.verify
import org.mockito.kotlin.whenever

class FetchTranscriptUseCaseTest {

  private val repository: TranscriptRepository = mock()
  private val useCase = FetchTranscriptUseCase(repository)

  private val sampleTranscript =
    Transcript(
      videoId = "dQw4w9WgXcQ",
      language = "English",
      languageCode = "en",
      isGenerated = false,
      snippets = listOf(TranscriptSnippet(text = "Hello world", start = 0f, duration = 2f)),
    )

  @Test
  fun `invoke with valid url calls repository with extracted video id`() = runTest {
    whenever(repository.fetchTranscript(any(), any())).thenReturn(Result.success(sampleTranscript))

    val result = useCase("https://www.youtube.com/watch?v=dQw4w9WgXcQ")

    assertTrue(result.isSuccess)
    assertEquals(sampleTranscript, result.getOrNull())
    verify(repository).fetchTranscript(videoId = "dQw4w9WgXcQ", languages = listOf("en"))
  }

  @Test
  fun `invoke with youtu_be url calls repository with correct video id`() = runTest {
    whenever(repository.fetchTranscript(any(), any())).thenReturn(Result.success(sampleTranscript))

    val result = useCase("https://youtu.be/dQw4w9WgXcQ")

    assertTrue(result.isSuccess)
    verify(repository).fetchTranscript(videoId = "dQw4w9WgXcQ", languages = listOf("en"))
  }

  @Test
  fun `invoke with invalid url returns failure without calling repository`() = runTest {
    val result = useCase("not-a-valid-youtube-url")

    assertTrue(result.isFailure)
    assertTrue(result.exceptionOrNull() is IllegalArgumentException)
    verify(repository, never()).fetchTranscript(any(), any())
  }

  @Test
  fun `invoke propagates repository failure`() = runTest {
    val error = Exception("Network error")
    whenever(repository.fetchTranscript(any(), any())).thenReturn(Result.failure(error))

    val result = useCase("https://youtu.be/dQw4w9WgXcQ")

    assertTrue(result.isFailure)
    assertEquals("Network error", result.exceptionOrNull()?.message)
  }

  @Test
  fun `invoke with custom languages passes them to repository`() = runTest {
    whenever(repository.fetchTranscript(any(), any())).thenReturn(Result.success(sampleTranscript))

    useCase("https://youtu.be/dQw4w9WgXcQ", languages = listOf("de", "en"))

    verify(repository).fetchTranscript(videoId = "dQw4w9WgXcQ", languages = listOf("de", "en"))
  }
}
