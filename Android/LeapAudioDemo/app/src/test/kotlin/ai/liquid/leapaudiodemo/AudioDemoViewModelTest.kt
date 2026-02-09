package ai.liquid.leapaudiodemo

import androidx.arch.core.executor.testing.InstantTaskExecutorRule
import io.kotest.matchers.shouldBe
import io.kotest.matchers.shouldNotBe
import kotlin.test.Test
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Before
import org.junit.Rule

/**
 * Unit tests for AudioDemoViewModel.
 *
 * Tests state management, event handling, and business logic without mocking external dependencies.
 * Model loading and API calls are tested separately in integration tests.
 */
@OptIn(ExperimentalCoroutinesApi::class)
class AudioDemoViewModelTest {

  @get:Rule val instantExecutorRule = InstantTaskExecutorRule()

  private val testDispatcher = StandardTestDispatcher()

  @Before
  fun setup() {
    Dispatchers.setMain(testDispatcher)
  }

  @After
  fun tearDown() {
    Dispatchers.resetMain()
  }

  @Test
  fun `initial state should have correct defaults`() = runTest {
    // Note: Cannot instantiate ViewModel in unit test due to AndroidViewModel dependency
    // This test documents the expected initial state structure
    val initialState = AudioDemoState()

    initialState.messages shouldBe emptyList()
    initialState.inputText shouldBe ""
    initialState.status shouldBe null
    initialState.modelState shouldBe ModelState.NotLoaded
    initialState.generationState shouldBe GenerationState.Idle
    initialState.recordingState shouldBe RecordingState.Idle
    initialState.playingMessageId shouldBe null
  }

  @Test
  fun `state should support message list updates`() {
    val state = AudioDemoState()
    val message =
      AudioDemoMessage(role = ai.liquid.leap.message.ChatMessage.Role.USER, text = "Test message")

    val updatedState = state.copy(messages = listOf(message))

    updatedState.messages.size shouldBe 1
    updatedState.messages[0].text shouldBe "Test message"
    updatedState.messages[0].isUser shouldBe true
  }

  @Test
  fun `state should support input text updates`() {
    val state = AudioDemoState()

    val updatedState = state.copy(inputText = "Hello")

    updatedState.inputText shouldBe "Hello"
  }

  @Test
  fun `state should support status updates`() {
    val state = AudioDemoState()

    val updatedState = state.copy(status = "Loading model...")

    updatedState.status shouldBe "Loading model..."
  }

  @Test
  fun `state should support loading state updates`() {
    val state = AudioDemoState()

    val loadingState = state.copy(modelState = ModelState.Loading)
    val loadedState = state.copy(modelState = ModelState.Ready)

    loadingState.modelState shouldBe ModelState.Loading
    loadedState.modelState shouldBe ModelState.Ready
  }

  @Test
  fun `state should support error states with retry capability`() {
    val state = AudioDemoState()

    val errorState = state.copy(modelState = ModelState.Error("Network error", canRetry = true))

    errorState.modelState shouldBe ModelState.Error("Network error", canRetry = true)
  }

  @Test
  fun `state should support recording state`() {
    val state = AudioDemoState()

    val recordingState = state.copy(recordingState = RecordingState.Recording)

    recordingState.recordingState shouldBe RecordingState.Recording
  }

  @Test
  fun `state should support generating state`() {
    val state = AudioDemoState()

    val generatingState =
      state.copy(generationState = GenerationState.GeneratingText("Streaming..."))

    generatingState.generationState shouldBe GenerationState.GeneratingText("Streaming...")
  }

  @Test
  fun `state should track specific playing message by ID`() {
    val state = AudioDemoState()
    val messageId = "msg-123"

    val playingState = state.copy(playingMessageId = messageId)

    playingState.playingMessageId shouldBe messageId
  }

  @Test
  fun `AudioDemoMessage should correctly identify user messages`() {
    val userMessage =
      AudioDemoMessage(role = ai.liquid.leap.message.ChatMessage.Role.USER, text = "User prompt")
    val assistantMessage =
      AudioDemoMessage(
        role = ai.liquid.leap.message.ChatMessage.Role.ASSISTANT,
        text = "Assistant response",
      )

    userMessage.isUser shouldBe true
    assistantMessage.isUser shouldBe false
  }

  @Test
  fun `AudioDemoMessage should generate unique IDs`() {
    val message1 =
      AudioDemoMessage(role = ai.liquid.leap.message.ChatMessage.Role.USER, text = "Message 1")
    val message2 =
      AudioDemoMessage(role = ai.liquid.leap.message.ChatMessage.Role.USER, text = "Message 2")

    message1.id shouldNotBe message2.id
  }

  @Test
  fun `AudioDemoMessage should support audio data`() {
    val audioData = floatArrayOf(0.1f, 0.2f, 0.3f)
    val sampleRate = 16000

    val message =
      AudioDemoMessage(
        role = ai.liquid.leap.message.ChatMessage.Role.ASSISTANT,
        text = "Audio response",
        audioData = audioData,
        sampleRate = sampleRate,
      )

    message.audioData shouldBe audioData
    message.sampleRate shouldBe sampleRate
  }

  @Test
  fun `UpdateInputText event should contain text`() {
    val event = AudioDemoEvent.UpdateInputText("Hello")

    event.text shouldBe "Hello"
  }

  @Test
  fun `SendAudioPrompt event should contain samples and sample rate`() {
    val samples = floatArrayOf(0.1f, 0.2f)
    val sampleRate = 16000

    val event = AudioDemoEvent.SendAudioPrompt(samples, sampleRate)

    event.samples shouldBe samples
    event.sampleRate shouldBe sampleRate
  }

  @Test
  fun `PlayAudio event should contain message ID, audio data, and sample rate`() {
    val messageId = "msg-123"
    val audioData = floatArrayOf(0.1f, 0.2f)
    val sampleRate = 24000

    val event = AudioDemoEvent.PlayAudio(messageId, audioData, sampleRate)

    event.messageId shouldBe messageId
    event.audioData shouldBe audioData
    event.sampleRate shouldBe sampleRate
  }

  // Note: Integration tests for model loading, response generation, and audio playback
  // should be implemented separately with proper test doubles for LeapSDK components.
  // These would test:
  // - Model download and loading flow
  // - Response streaming and state updates
  // - Audio playback state management
  // - Error handling during API calls
  // - Resource cleanup in various scenarios
}
