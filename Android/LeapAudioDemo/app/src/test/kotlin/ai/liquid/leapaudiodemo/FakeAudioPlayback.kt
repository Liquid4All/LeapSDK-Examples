package ai.liquid.leapaudiodemo

/**
 * Fake implementation of AudioPlayback for testing.
 *
 * Tracks all method calls and provides methods to simulate callbacks (onPlaybackCompleted,
 * onPlaybackInterrupted) for testing ViewModel behavior.
 *
 * This follows the fake pattern recommended in AGENTS.md (no mocks in tests).
 *
 * Example usage:
 * ```
 * val fake = FakeAudioPlayback()
 * val viewModel = AudioDemoViewModel(application, fake)
 *
 * // Trigger some action
 * viewModel.onEvent(AudioDemoEvent.PlayAudio(...))
 *
 * // Verify behavior
 * fake.playCallCount shouldBe 1
 * fake.lastPlaySamples shouldBe expectedSamples
 *
 * // Simulate completion callback
 * fake.simulatePlaybackCompleted()
 * ```
 */
class FakeAudioPlayback(
  private val onPlaybackInterrupted: (() -> Unit)? = null,
  private val onPlaybackCompleted: (() -> Unit)? = null,
  private val onPlaybackError: ((String) -> Unit)? = null,
  private val onStreamingCompleted: (() -> Unit)? = null,
) : AudioPlayback {

  // State tracking
  var isStreaming = false
    private set

  var isPlaying = false
    private set

  // Call counts
  var startStreamingCallCount = 0
    private set

  var writeStreamCallCount = 0
    private set

  var finishStreamingCallCount = 0
    private set

  var stopStreamingCallCount = 0
    private set

  var playCallCount = 0
    private set

  var stopCallCount = 0
    private set

  var resetCallCount = 0
    private set

  var releaseCallCount = 0
    private set

  // Call history for verification
  val streamingChunks = mutableListOf<FloatArray>()
  var lastStartStreamingSampleRate: Int? = null
    private set

  var lastPlaySamples: FloatArray? = null
    private set

  var lastPlaySampleRate: Int? = null
    private set

  override fun startStreaming(sampleRate: Int) {
    startStreamingCallCount++
    lastStartStreamingSampleRate = sampleRate
    isStreaming = true
    streamingChunks.clear()
  }

  override fun writeStream(samples: FloatArray): Boolean {
    writeStreamCallCount++
    streamingChunks.add(samples)
    return true // Fake always succeeds
  }

  override fun finishStreaming() {
    finishStreamingCallCount++
    // Simulate graceful finish - keep isStreaming true until manually stopped
    // In real implementation, playback continues until buffer is drained
  }

  override fun stopStreaming() {
    stopStreamingCallCount++
    isStreaming = false
    streamingChunks.clear()
  }

  override fun play(samples: FloatArray, sampleRate: Int) {
    playCallCount++
    lastPlaySamples = samples
    lastPlaySampleRate = sampleRate
    isPlaying = true
  }

  override fun stop() {
    stopCallCount++
    isPlaying = false
    isStreaming = false
  }

  override fun reset() {
    resetCallCount++
    isPlaying = false
    isStreaming = false
    streamingChunks.clear()
    lastPlaySamples = null
    lastPlaySampleRate = null
    lastStartStreamingSampleRate = null
  }

  override fun release() {
    releaseCallCount++
    reset()
  }

  /**
   * Simulates the playback completed callback.
   *
   * Call this in tests to simulate when audio playback finishes naturally.
   */
  fun simulatePlaybackCompleted() {
    isPlaying = false
    onPlaybackCompleted?.invoke()
  }

  /**
   * Simulates the streaming playback completed callback.
   *
   * Call this in tests to simulate when streaming audio finishes draining the buffer.
   */
  fun simulateStreamingCompleted() {
    isStreaming = false
    onStreamingCompleted?.invoke()
  }

  /**
   * Simulates the playback interrupted callback.
   *
   * Call this in tests to simulate when audio playback is interrupted by audio focus loss.
   */
  fun simulatePlaybackInterrupted() {
    isPlaying = false
    isStreaming = false
    onPlaybackInterrupted?.invoke()
  }

  /**
   * Simulates the playback error callback.
   *
   * Call this in tests to simulate when audio playback fails (e.g., audio focus denied).
   *
   * @param errorMessage The error message to pass to the callback
   */
  fun simulatePlaybackError(errorMessage: String) {
    isPlaying = false
    isStreaming = false
    onPlaybackError?.invoke(errorMessage)
  }

  /**
   * Resets all tracking state for test isolation.
   *
   * Call between tests or test scenarios to start with a clean slate.
   */
  fun resetTracking() {
    startStreamingCallCount = 0
    writeStreamCallCount = 0
    stopStreamingCallCount = 0
    playCallCount = 0
    stopCallCount = 0
    resetCallCount = 0
    releaseCallCount = 0
    isStreaming = false
    isPlaying = false
    streamingChunks.clear()
    lastStartStreamingSampleRate = null
    lastPlaySamples = null
    lastPlaySampleRate = null
  }

  /**
   * Gets the total number of audio samples written via streaming.
   *
   * Useful for verifying that audio was streamed correctly.
   */
  fun getTotalStreamedSampleCount(): Int {
    return streamingChunks.sumOf { it.size }
  }
}
