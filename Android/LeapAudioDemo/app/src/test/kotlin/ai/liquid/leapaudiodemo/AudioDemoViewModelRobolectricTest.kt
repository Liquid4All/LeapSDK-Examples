package ai.liquid.leapaudiodemo

import android.app.Application
import androidx.arch.core.executor.testing.InstantTaskExecutorRule
import androidx.test.core.app.ApplicationProvider
import app.cash.turbine.test
import io.kotest.matchers.collections.shouldHaveSize
import io.kotest.matchers.shouldBe
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Before
import org.junit.Rule
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import kotlin.test.Test

/**
 * Robolectric tests for AudioDemoViewModel.
 *
 * These tests use Robolectric to provide Android framework classes,
 * allowing instantiation and testing of AndroidViewModel without
 * requiring a device or emulator.
 *
 * Tests focus on:
 * - ViewModel initialization
 * - Event handling and state transitions
 * - StateFlow emissions
 * - Input validation
 *
 * Note: Model loading and API interactions are not tested here as they
 * require real LeapSDK components or test doubles.
 */
@OptIn(ExperimentalCoroutinesApi::class)
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [31])
class AudioDemoViewModelRobolectricTest {

    @get:Rule
    val instantExecutorRule = InstantTaskExecutorRule()

    private val testDispatcher = StandardTestDispatcher()
    private lateinit var application: Application
    private lateinit var viewModel: AudioDemoViewModel

    @Before
    fun setup() {
        Dispatchers.setMain(testDispatcher)
        application = ApplicationProvider.getApplicationContext()
        viewModel = AudioDemoViewModel(application)
    }

    @After
    fun tearDown() {
        Dispatchers.resetMain()
    }

    @Test
    fun `viewModel should initialize with default state`() = runTest {
        viewModel.state.test {
            val state = awaitItem()

            state.messages shouldBe emptyList()
            state.inputText shouldBe ""
            state.status shouldBe null
            state.streamingText shouldBe ""
            state.isModelLoading shouldBe false
            state.isGenerating shouldBe false
            state.isRecording shouldBe false
            state.isModelLoaded shouldBe false
            state.loadError shouldBe null
            state.canRetryLoad shouldBe false
            state.playingMessageId shouldBe null
        }
    }

    @Test
    fun `UpdateInputText should update input text in state`() = runTest {
        viewModel.state.test {
            skipItems(1) // Skip initial state

            viewModel.onEvent(AudioDemoEvent.UpdateInputText("Hello"))

            val state = awaitItem()
            state.inputText shouldBe "Hello"
        }
    }

    @Test
    fun `UpdateInputText multiple times should update state correctly`() = runTest {
        viewModel.state.test {
            skipItems(1) // Skip initial state

            viewModel.onEvent(AudioDemoEvent.UpdateInputText("H"))
            awaitItem().inputText shouldBe "H"

            viewModel.onEvent(AudioDemoEvent.UpdateInputText("He"))
            awaitItem().inputText shouldBe "He"

            viewModel.onEvent(AudioDemoEvent.UpdateInputText("Hel"))
            awaitItem().inputText shouldBe "Hel"

            viewModel.onEvent(AudioDemoEvent.UpdateInputText("Hello"))
            awaitItem().inputText shouldBe "Hello"
        }
    }

    @Test
    fun `ToggleRecording should toggle recording state`() = runTest {
        viewModel.state.test {
            skipItems(1) // Skip initial state

            // Start recording - emits 2 states: toggle + status update
            viewModel.onEvent(AudioDemoEvent.ToggleRecording)
            awaitItem() // First update: isRecording = true
            var state = awaitItem() // Second update: status = "Recording..."
            state.isRecording shouldBe true

            // Stop recording - emits 1 state: just the toggle
            viewModel.onEvent(AudioDemoEvent.ToggleRecording)
            state = awaitItem()
            state.isRecording shouldBe false
        }
    }

    @Test
    fun `ToggleRecording should update status when recording starts`() = runTest {
        viewModel.state.test {
            skipItems(1) // Skip initial state

            viewModel.onEvent(AudioDemoEvent.ToggleRecording)

            // toggleRecording emits 2 states when starting
            awaitItem() // First: isRecording = true
            val state = awaitItem() // Second: status = "Recording…"
            state.isRecording shouldBe true
            state.status shouldBe application.getString(R.string.status_recording)
        }
    }

    @Test
    fun `RecordingFailed should set error status and stop recording`() = runTest {
        viewModel.state.test {
            skipItems(1) // Skip initial state

            // First start recording
            viewModel.onEvent(AudioDemoEvent.ToggleRecording)
            awaitItem() // Recording toggle
            awaitItem() // Recording status

            // Then simulate recording failure
            viewModel.onEvent(AudioDemoEvent.RecordingFailed)

            val state = awaitItem()
            state.isRecording shouldBe false
            state.status shouldBe "Failed to start recording. Check microphone permissions."
        }
    }

    @Test
    fun `SendTextPrompt with empty input should not add message`() = runTest {
        viewModel.state.test {
            skipItems(1) // Skip initial state

            viewModel.onEvent(AudioDemoEvent.SendTextPrompt)

            // Should not emit a new state since input is empty
            expectNoEvents()
        }
    }

    @Test
    fun `SendTextPrompt with whitespace-only input should not add message`() = runTest {
        viewModel.state.test {
            skipItems(1) // Skip initial state

            viewModel.onEvent(AudioDemoEvent.UpdateInputText("   "))
            awaitItem() // Input text updated

            viewModel.onEvent(AudioDemoEvent.SendTextPrompt)

            // Should not emit a new state since input is whitespace
            expectNoEvents()
        }
    }

    @Test
    fun `SendTextPrompt with valid input should add user message and clear input`() = runTest {
        viewModel.state.test {
            skipItems(1) // Skip initial state

            viewModel.onEvent(AudioDemoEvent.UpdateInputText("Test message"))
            awaitItem() // Input text updated

            viewModel.onEvent(AudioDemoEvent.SendTextPrompt)

            // SendTextPrompt triggers streamResponse which emits additional states
            // 1. Message added + input cleared
            // 2. Status updated ("Model not ready yet")
            var state = awaitItem()
            state.messages shouldHaveSize 1
            state.messages[0].text shouldBe "Test message"
            state.messages[0].isUser shouldBe true
            state.inputText shouldBe ""

            // Consume the error status emission from streamResponse
            state = awaitItem()
            state.status shouldBe "Model not ready yet."
        }
    }

    @Test
    fun `SendAudioPrompt with empty samples should set error status`() = runTest {
        viewModel.state.test {
            skipItems(1) // Skip initial state

            viewModel.onEvent(AudioDemoEvent.SendAudioPrompt(floatArrayOf(), 16000))

            val state = awaitItem()
            state.status shouldBe "Audio capture was empty."
        }
    }

    @Test
    fun `SendAudioPrompt with valid samples should add user message`() = runTest {
        viewModel.state.test {
            skipItems(1) // Skip initial state

            val samples = floatArrayOf(0.1f, 0.2f, 0.3f)
            val sampleRate = 16000

            viewModel.onEvent(AudioDemoEvent.SendAudioPrompt(samples, sampleRate))

            // SendAudioPrompt triggers streamResponse which emits additional states
            // 1. Message added
            // 2. Status updated ("Model not ready yet")
            var state = awaitItem()
            state.messages shouldHaveSize 1
            state.messages[0].isUser shouldBe true
            state.messages[0].audioData shouldBe samples
            state.messages[0].sampleRate shouldBe sampleRate

            // Consume the error status emission from streamResponse
            state = awaitItem()
            state.status shouldBe "Model not ready yet."
        }
    }

    // Note: Testing model loading requires either:
    // 1. Mocking LeapModelDownloader (not preferred per AGENTS.md)
    // 2. Creating fake implementations for testing
    // 3. Integration tests with real SDK components
    //
    // Similarly, testing response generation and audio playback requires
    // either test doubles or integration tests with real components.
}
