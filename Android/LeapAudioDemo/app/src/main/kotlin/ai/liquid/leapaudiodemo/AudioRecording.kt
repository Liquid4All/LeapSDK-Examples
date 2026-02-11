package ai.liquid.leapaudiodemo

/**
 * Interface for audio recording operations.
 *
 * Abstracts audio recording functionality to enable testing with fake implementations and
 * dependency injection. Records audio from the device microphone at 16kHz mono PCM.
 */
interface AudioRecording {
  /**
   * Starts audio recording from the microphone.
   *
   * Validates that the AudioRecord can be initialized before starting. Records in a background
   * coroutine until [stop] or [cancel] is called, or the maximum recording duration is reached.
   *
   * @return true if recording started successfully, false if initialization failed
   */
  fun start(): Boolean

  /**
   * Stops recording and returns the captured audio.
   *
   * Cancels the recording coroutine, stops and releases the AudioRecord, and returns the recorded
   * samples if any were captured.
   *
   * @return AudioCapture containing the recorded samples, or null if no audio was captured
   */
  suspend fun stop(): AudioCapture?

  /**
   * Cancels recording and discards all captured audio.
   *
   * Similar to [stop] but does not return the recorded audio. Use this when the user cancels
   * recording rather than completing it.
   */
  suspend fun cancel()

  /**
   * Represents captured audio data.
   *
   * @property samples Audio samples as float values in range [-1.0, 1.0]
   * @property sampleRate Sample rate in Hz (typically 16000)
   */
  data class AudioCapture(val samples: FloatArray, val sampleRate: Int)
}
