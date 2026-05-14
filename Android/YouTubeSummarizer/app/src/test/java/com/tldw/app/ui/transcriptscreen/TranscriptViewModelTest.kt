package com.tldw.app.ui.transcriptscreen

import com.tldw.app.domain.model.Transcript
import com.tldw.app.domain.model.TranscriptSnippet
import com.tldw.app.domain.model.VideoInfo
import com.tldw.app.domain.usecase.CheckModelDownloadedUseCase
import com.tldw.app.domain.usecase.FetchTranscriptUseCase
import com.tldw.app.domain.usecase.GenerateTldrUseCase
import com.tldw.app.domain.usecase.LoadModelUseCase
import com.tldw.app.domain.usecase.UnloadModelUseCase
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.mockito.kotlin.any
import org.mockito.kotlin.mock
import org.mockito.kotlin.whenever

@OptIn(ExperimentalCoroutinesApi::class)
class TranscriptViewModelTest {

  private val testDispatcher = StandardTestDispatcher()
  private val fetchTranscriptUseCase: FetchTranscriptUseCase = mock()
  private val checkModelDownloadedUseCase: CheckModelDownloadedUseCase = mock()
  private val loadModelUseCase: LoadModelUseCase = mock()
  private val generateTldrUseCase: GenerateTldrUseCase = mock()
  private val unloadModelUseCase: UnloadModelUseCase = mock()
  private lateinit var viewModel: TranscriptViewModel

  private val sampleVideoInfo =
    VideoInfo(
      videoId = "dQw4w9WgXcQ",
      title = "Never Gonna Give You Up",
      channelName = "Rick Astley",
      durationSeconds = 213L,
      viewCount = 1_200_000L,
    )

  private val sampleTranscript =
    Transcript(
      videoId = "dQw4w9WgXcQ",
      language = "English",
      languageCode = "en",
      isGenerated = false,
      snippets =
        listOf(
          TranscriptSnippet(text = "Hello", start = 0f, duration = 1f),
          TranscriptSnippet(text = "world", start = 1f, duration = 1f),
        ),
      videoInfo = sampleVideoInfo,
    )

  @Before
  fun setUp() {
    Dispatchers.setMain(testDispatcher)
    viewModel =
      TranscriptViewModel(
        fetchTranscriptUseCase,
        checkModelDownloadedUseCase,
        loadModelUseCase,
        generateTldrUseCase,
        unloadModelUseCase,
      )
  }

  @After
  fun tearDown() {
    Dispatchers.resetMain()
  }

  // ── Initial state ─────────────────────────────────────────────────────────

  @Test
  fun `initial state is correct`() {
    val state = viewModel.state.value
    assertEquals("", state.url)
    assertEquals(TranscriptPhase.Idle, state.phase)
    assertFalse(state.urlError)
    assertNull(state.videoInfo)
    assertNull(state.tldrText)
    assertNull(state.errorMessage)
  }

  // ── URL input ─────────────────────────────────────────────────────────────

  @Test
  fun `UrlChanged updates url and clears urlError`() {
    viewModel.onEvent(TranscriptEvent.UrlChanged("https://youtu.be/dQw4w9WgXcQ"))
    assertEquals("https://youtu.be/dQw4w9WgXcQ", viewModel.state.value.url)
    assertFalse(viewModel.state.value.urlError)
  }

  @Test
  fun `UrlChanged after urlError clears the error flag`() {
    // First set an error by submitting invalid URL
    viewModel.onEvent(TranscriptEvent.UrlChanged("https://example.com/blog/post-12"))
    viewModel.onEvent(TranscriptEvent.Submit)
    assertTrue(viewModel.state.value.urlError)

    // Then typing clears the error
    viewModel.onEvent(TranscriptEvent.UrlChanged("https://youtu.be/dQw4w9WgXcQ"))
    assertFalse(viewModel.state.value.urlError)
  }

  @Test
  fun `Submit with blank url does nothing`() = runTest {
    viewModel.onEvent(TranscriptEvent.Submit)
    advanceUntilIdle()

    assertEquals(TranscriptPhase.Idle, viewModel.state.value.phase)
    assertFalse(viewModel.state.value.urlError)
  }

  @Test
  fun `Submit with invalid YouTube URL sets urlError`() = runTest {
    viewModel.onEvent(TranscriptEvent.UrlChanged("https://example.com/blog/post-12"))
    viewModel.onEvent(TranscriptEvent.Submit)
    advanceUntilIdle()

    assertTrue(viewModel.state.value.urlError)
    assertEquals(TranscriptPhase.Idle, viewModel.state.value.phase)
  }

  // ── Happy path ────────────────────────────────────────────────────────────

  @Test
  fun `Submit with valid URL moves to Fetching phase`() = runTest {
    whenever(fetchTranscriptUseCase(any(), any())).thenReturn(Result.success(sampleTranscript))
    whenever(checkModelDownloadedUseCase()).thenReturn(false)

    viewModel.onEvent(TranscriptEvent.UrlChanged("https://youtu.be/dQw4w9WgXcQ"))
    viewModel.onEvent(TranscriptEvent.Submit)

    assertEquals(TranscriptPhase.Fetching, viewModel.state.value.phase)
  }

  @Test
  fun `successful fetch sets videoInfo and moves to Generating`() = runTest {
    whenever(fetchTranscriptUseCase(any(), any())).thenReturn(Result.success(sampleTranscript))
    whenever(checkModelDownloadedUseCase()).thenReturn(false)

    viewModel.onEvent(TranscriptEvent.UrlChanged("https://youtu.be/dQw4w9WgXcQ"))
    viewModel.onEvent(TranscriptEvent.Submit)
    advanceUntilIdle()

    val state = viewModel.state.value
    assertEquals(TranscriptPhase.Generating, state.phase)
    assertEquals(sampleVideoInfo, state.videoInfo)
    assertEquals(2, state.wordCountIn) // "Hello world" = 2 words
    assertTrue(state.isModelNotDownloaded)
  }

  @Test
  fun `successful fetch and generation moves to Done`() = runTest {
    whenever(fetchTranscriptUseCase(any(), any())).thenReturn(Result.success(sampleTranscript))
    whenever(checkModelDownloadedUseCase()).thenReturn(true)
    whenever(loadModelUseCase()).thenReturn(Result.success(Unit))
    whenever(generateTldrUseCase(any())).thenReturn(flowOf("Summary text"))

    viewModel.onEvent(TranscriptEvent.UrlChanged("https://youtu.be/dQw4w9WgXcQ"))
    viewModel.onEvent(TranscriptEvent.Submit)
    advanceUntilIdle()

    val state = viewModel.state.value
    assertEquals(TranscriptPhase.Done, state.phase)
    assertEquals("Summary text", state.tldrText)
    assertEquals(2, state.wordCountOut) // "Summary text" = 2 words
    assertTrue(state.generationTimeSeconds >= 0f)
  }

  // ── Error cases ───────────────────────────────────────────────────────────

  @Test
  fun `fetch failure with generic error returns to Idle with errorMessage`() = runTest {
    whenever(fetchTranscriptUseCase(any(), any()))
      .thenReturn(Result.failure(Exception("Network error")))

    viewModel.onEvent(TranscriptEvent.UrlChanged("https://youtu.be/dQw4w9WgXcQ"))
    viewModel.onEvent(TranscriptEvent.Submit)
    advanceUntilIdle()

    val state = viewModel.state.value
    assertEquals(TranscriptPhase.Idle, state.phase)
    assertEquals("Network error", state.errorMessage)
  }

  @Test
  fun `fetch failure with no captions message sets FailedNoCaptions phase`() = runTest {
    whenever(fetchTranscriptUseCase(any(), any()))
      .thenReturn(Result.failure(Exception("No caption tracks found for video: dQw4w9WgXcQ")))

    viewModel.onEvent(TranscriptEvent.UrlChanged("https://youtu.be/dQw4w9WgXcQ"))
    viewModel.onEvent(TranscriptEvent.Submit)
    advanceUntilIdle()

    assertEquals(TranscriptPhase.FailedNoCaptions, viewModel.state.value.phase)
    assertNull(viewModel.state.value.errorMessage)
  }

  @Test
  fun `fetch failure with disabled transcripts sets FailedNoCaptions phase`() = runTest {
    whenever(fetchTranscriptUseCase(any(), any()))
      .thenReturn(Result.failure(Exception("Transcripts are disabled for video: abc")))

    viewModel.onEvent(TranscriptEvent.UrlChanged("https://youtu.be/abcdefghijk"))
    viewModel.onEvent(TranscriptEvent.Submit)
    advanceUntilIdle()

    assertEquals(TranscriptPhase.FailedNoCaptions, viewModel.state.value.phase)
  }

  @Test
  fun `generation failure sets FailedGeneration phase with failure info`() = runTest {
    whenever(fetchTranscriptUseCase(any(), any())).thenReturn(Result.success(sampleTranscript))
    whenever(checkModelDownloadedUseCase()).thenReturn(true)
    whenever(loadModelUseCase()).thenReturn(Result.success(Unit))
    whenever(generateTldrUseCase(any())).thenThrow(RuntimeException("inference timed out"))

    viewModel.onEvent(TranscriptEvent.UrlChanged("https://youtu.be/dQw4w9WgXcQ"))
    viewModel.onEvent(TranscriptEvent.Submit)
    advanceUntilIdle()

    val state = viewModel.state.value
    assertEquals(TranscriptPhase.FailedGeneration, state.phase)
    assertNotNull(state.generationFailure)
  }

  @Test
  fun `model load failure sets FailedGeneration phase`() = runTest {
    whenever(fetchTranscriptUseCase(any(), any())).thenReturn(Result.success(sampleTranscript))
    whenever(checkModelDownloadedUseCase()).thenReturn(true)
    whenever(loadModelUseCase()).thenReturn(Result.failure(Exception("OOM")))

    viewModel.onEvent(TranscriptEvent.UrlChanged("https://youtu.be/dQw4w9WgXcQ"))
    viewModel.onEvent(TranscriptEvent.Submit)
    advanceUntilIdle()

    assertEquals(TranscriptPhase.FailedGeneration, viewModel.state.value.phase)
    assertEquals("EXCEPTION", viewModel.state.value.generationFailure?.errorCode)
  }

  // ── Navigation ────────────────────────────────────────────────────────────

  @Test
  fun `Cancel from Fetching returns to Idle and preserves url`() = runTest {
    whenever(fetchTranscriptUseCase(any(), any())).thenReturn(Result.success(sampleTranscript))
    whenever(checkModelDownloadedUseCase()).thenReturn(false)

    viewModel.onEvent(TranscriptEvent.UrlChanged("https://youtu.be/dQw4w9WgXcQ"))
    viewModel.onEvent(TranscriptEvent.Submit)
    // Cancel before coroutine finishes
    viewModel.onEvent(TranscriptEvent.Cancel)

    val state = viewModel.state.value
    assertEquals(TranscriptPhase.Idle, state.phase)
    assertEquals("https://youtu.be/dQw4w9WgXcQ", state.url)
    assertNull(state.videoInfo)
  }

  @Test
  fun `Cancel from Done returns to Idle`() = runTest {
    whenever(fetchTranscriptUseCase(any(), any())).thenReturn(Result.success(sampleTranscript))
    whenever(checkModelDownloadedUseCase()).thenReturn(true)
    whenever(loadModelUseCase()).thenReturn(Result.success(Unit))
    whenever(generateTldrUseCase(any())).thenReturn(flowOf("Summary"))

    viewModel.onEvent(TranscriptEvent.UrlChanged("https://youtu.be/dQw4w9WgXcQ"))
    viewModel.onEvent(TranscriptEvent.Submit)
    advanceUntilIdle()
    assertEquals(TranscriptPhase.Done, viewModel.state.value.phase)

    viewModel.onEvent(TranscriptEvent.Cancel)
    assertEquals(TranscriptPhase.Idle, viewModel.state.value.phase)
  }

  // ── Retry ─────────────────────────────────────────────────────────────────

  @Test
  fun `RetryGeneration after failure starts generation again`() = runTest {
    // First: fetch ok, generation fails
    whenever(fetchTranscriptUseCase(any(), any())).thenReturn(Result.success(sampleTranscript))
    whenever(checkModelDownloadedUseCase()).thenReturn(true)
    whenever(loadModelUseCase()).thenReturn(Result.success(Unit))
    whenever(generateTldrUseCase(any()))
      .thenThrow(RuntimeException("fail"))
      .thenReturn(flowOf("Retry success"))

    viewModel.onEvent(TranscriptEvent.UrlChanged("https://youtu.be/dQw4w9WgXcQ"))
    viewModel.onEvent(TranscriptEvent.Submit)
    advanceUntilIdle()
    assertEquals(TranscriptPhase.FailedGeneration, viewModel.state.value.phase)

    // Retry
    viewModel.onEvent(TranscriptEvent.RetryGeneration)
    advanceUntilIdle()

    val state = viewModel.state.value
    assertEquals(TranscriptPhase.Done, state.phase)
    assertEquals("Retry success", state.tldrText)
  }

  @Test
  fun `RegenerateTldr from Done starts generation again`() = runTest {
    whenever(fetchTranscriptUseCase(any(), any())).thenReturn(Result.success(sampleTranscript))
    whenever(checkModelDownloadedUseCase()).thenReturn(true)
    whenever(loadModelUseCase()).thenReturn(Result.success(Unit))
    whenever(generateTldrUseCase(any()))
      .thenReturn(flowOf("First summary"))
      .thenReturn(flowOf("Second summary"))

    viewModel.onEvent(TranscriptEvent.UrlChanged("https://youtu.be/dQw4w9WgXcQ"))
    viewModel.onEvent(TranscriptEvent.Submit)
    advanceUntilIdle()
    assertEquals("First summary", viewModel.state.value.tldrText)

    viewModel.onEvent(TranscriptEvent.RegenerateTldr)
    advanceUntilIdle()

    assertEquals(TranscriptPhase.Done, viewModel.state.value.phase)
    assertEquals("Second summary", viewModel.state.value.tldrText)
  }

  // ── handleSharedUrl ───────────────────────────────────────────────────────

  @Test
  fun `handleSharedUrl triggers fetch with provided url`() = runTest {
    whenever(fetchTranscriptUseCase(any(), any())).thenReturn(Result.success(sampleTranscript))
    whenever(checkModelDownloadedUseCase()).thenReturn(false)

    viewModel.handleSharedUrl("https://youtu.be/dQw4w9WgXcQ")
    advanceUntilIdle()

    val state = viewModel.state.value
    assertEquals(TranscriptPhase.Generating, state.phase)
    assertEquals("https://youtu.be/dQw4w9WgXcQ", state.url)
    assertNotNull(state.videoInfo)
  }

  // ── DismissError ──────────────────────────────────────────────────────────

  @Test
  fun `DismissError clears errorMessage`() = runTest {
    whenever(fetchTranscriptUseCase(any(), any()))
      .thenReturn(Result.failure(Exception("Network error")))
    viewModel.onEvent(TranscriptEvent.UrlChanged("https://youtu.be/dQw4w9WgXcQ"))
    viewModel.onEvent(TranscriptEvent.Submit)
    advanceUntilIdle()

    viewModel.onEvent(TranscriptEvent.DismissError)

    assertNull(viewModel.state.value.errorMessage)
  }
}
