package ai.liquid.leapaudiodemo

/**
 * Interface for audio playback operations.
 *
 * Abstracts audio playback functionality to enable testing with fake implementations and dependency
 * injection. Supports both streaming and one-shot playback modes.
 */
interface AudioPlayback {
  /**
   * Starts streaming audio playback mode.
   *
   * Creates a buffered channel for audio samples and begins playback. Call [writeStream] to send
   * audio data and [stopStreaming] when complete.
   *
   * @param sampleRate Sample rate in Hz (e.g., 16000, 24000, 48000)
   */
  fun startStreaming(sampleRate: Int)

  /**
   * Writes audio samples to the streaming buffer.
   *
   * Queues samples for playback. Safe to call repeatedly while streaming. If the buffer is full,
   * samples may be dropped (backpressure).
   *
   * @param samples Float array of audio samples in range [-1.0, 1.0]
   * @return true if samples were successfully queued, false if channel is full and samples were
   *   dropped
   */
  fun writeStream(samples: FloatArray): Boolean

  /**
   * Finishes streaming playback gracefully.
   *
   * Closes the audio queue to prevent new samples from being written, but allows the playback
   * coroutine to finish playing all buffered samples. Use this when generation completes and you
   * want the remaining audio to play out.
   */
  fun finishStreaming()

  /**
   * Stops streaming playback immediately and closes the audio queue.
   *
   * Cancels the streaming coroutine and releases streaming resources. Call this when streaming
   * should be interrupted immediately.
   */
  fun stopStreaming()

  /**
   * Plays audio samples in one-shot mode (MODE_STATIC).
   *
   * Plays the entire audio buffer once. Monitors playback completion asynchronously and invokes
   * onPlaybackCompleted callback when done.
   *
   * @param samples Float array of audio samples in range [-1.0, 1.0]
   * @param sampleRate Sample rate in Hz
   */
  fun play(samples: FloatArray, sampleRate: Int)

  /**
   * Stops current playback (streaming or one-shot).
   *
   * Cancels playback jobs and stops AudioTrack. Audio focus is abandoned.
   */
  fun stop()

  /**
   * Resets the audio player to initial state.
   *
   * Stops playback, cancels jobs, closes channels, and releases AudioTrack. Call before starting
   * new playback session.
   */
  fun reset()

  /**
   * Releases all audio resources.
   *
   * Stops playback, abandons audio focus, and releases AudioTrack. Call in ViewModel.onCleared() or
   * when done with audio player.
   */
  fun release()
}
