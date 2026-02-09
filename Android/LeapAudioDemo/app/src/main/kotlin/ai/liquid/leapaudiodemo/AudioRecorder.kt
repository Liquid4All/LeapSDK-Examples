package ai.liquid.leapaudiodemo

import android.media.AudioFormat
import android.media.AudioRecord
import android.media.MediaRecorder
import android.util.Log
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext

/**
 * Handles audio recording from the device microphone.
 *
 * Records audio at 16kHz mono PCM float format with a maximum duration of 60 seconds to prevent
 * out-of-memory errors. Uses coroutines for non-blocking audio capture.
 */
class AudioRecorder : AudioRecording {
  private var audioRecord: AudioRecord? = null
  private var recordingJob: Job? = null
  private val recordedSamples = mutableListOf<Float>()
  private val samplesMutex = Mutex()
  private val coroutineScope = CoroutineScope(Dispatchers.IO)

  companion object {
    private const val TAG = "AudioRecorder"
    private const val SAMPLE_RATE = 16000
    private const val CHANNEL_CONFIG = AudioFormat.CHANNEL_IN_MONO
    private const val AUDIO_FORMAT = AudioFormat.ENCODING_PCM_FLOAT
    private const val MAX_RECORDING_SECONDS = 60
    private val MAX_SAMPLES = SAMPLE_RATE * MAX_RECORDING_SECONDS
  }

  /**
   * Starts audio recording from the microphone.
   *
   * Validates that the AudioRecord can be initialized before starting. Records in a background
   * coroutine until [stop] or [cancel] is called, or the maximum recording duration is reached.
   *
   * @return true if recording started successfully, false if initialization failed
   */
  override fun start(): Boolean {
    // Clear any previous samples before starting (no mutex needed - called before job starts)
    recordedSamples.clear()

    val bufferSize = AudioRecord.getMinBufferSize(SAMPLE_RATE, CHANNEL_CONFIG, AUDIO_FORMAT)

    if (bufferSize <= 0) {
      // Failed to get a valid buffer size; do not start recording
      return false
    }

    audioRecord =
      AudioRecord(
        MediaRecorder.AudioSource.MIC,
        SAMPLE_RATE,
        CHANNEL_CONFIG,
        AUDIO_FORMAT,
        bufferSize,
      )

    if (audioRecord?.state != AudioRecord.STATE_INITIALIZED) {
      // AudioRecord failed to initialize; clean up and do not start recording
      audioRecord?.release()
      audioRecord = null
      recordedSamples.clear()
      return false
    }

    try {
      audioRecord?.startRecording()
    } catch (e: SecurityException) {
      // Missing RECORD_AUDIO permission
      audioRecord?.release()
      audioRecord = null
      recordedSamples.clear()
      return false
    } catch (e: IllegalStateException) {
      // AudioRecord in invalid state
      audioRecord?.release()
      audioRecord = null
      recordedSamples.clear()
      return false
    }

    recordingJob =
      coroutineScope.launch {
        val buffer = FloatArray(bufferSize / 4) // 4 bytes per float
        while (isActive) {
          val read = audioRecord?.read(buffer, 0, buffer.size, AudioRecord.READ_BLOCKING) ?: 0
          when {
            read > 0 -> {
              samplesMutex.withLock {
                val samplesToAdd = minOf(read, MAX_SAMPLES - recordedSamples.size)
                if (samplesToAdd > 0) {
                  // Use addAll() to avoid O(n) buffer expansion from individual add() calls
                  recordedSamples.addAll(buffer.slice(0 until samplesToAdd))
                }
                // Check if we hit the max limit
                if (recordedSamples.size >= MAX_SAMPLES) {
                  // Break out of while loop - will auto-stop below
                  return@launch
                }
              }
            }
            read < 0 -> {
              // AudioRecord.read() returned error code
              // Negative values indicate errors: ERROR_INVALID_OPERATION (-3),
              // ERROR_BAD_VALUE (-2), ERROR_DEAD_OBJECT (-6), ERROR (-1)
              Log.e(TAG, "AudioRecord read error: $read")
              return@launch
            }
            // read == 0: No samples available, continue loop
          }
        }
      }.also { job ->
        // Auto-stop recording when job completes
        job.invokeOnCompletion {
          try {
            audioRecord?.takeIf { it.recordingState == AudioRecord.RECORDSTATE_RECORDING }?.stop()
          } catch (e: IllegalStateException) {
            // AudioRecord already stopped or in invalid state
            Log.w(TAG, "AudioRecord already stopped during auto-stop", e)
          }
        }
      }

    return true
  }

  /**
   * Stops recording and returns the captured audio.
   *
   * Cancels the recording coroutine, stops and releases the AudioRecord, and returns the recorded
   * samples if any were captured.
   *
   * @return AudioCapture containing the recorded samples, or null if no audio was captured
   */
  override suspend fun stop(): AudioRecording.AudioCapture? =
    withContext(Dispatchers.IO) {
      recordingJob?.cancel()
      recordingJob?.join()
      recordingJob = null

      try {
        audioRecord?.takeIf { it.recordingState == AudioRecord.RECORDSTATE_RECORDING }?.stop()
      } catch (e: IllegalStateException) {
        // AudioRecord already stopped or in invalid state
        Log.w(TAG, "AudioRecord already stopped during stop()", e)
      }
      audioRecord?.release()
      audioRecord = null

      samplesMutex.withLock {
        if (recordedSamples.isNotEmpty()) {
          AudioRecording.AudioCapture(
            samples = recordedSamples.toFloatArray(),
            sampleRate = SAMPLE_RATE,
          )
        } else {
          null
        }
      }
    }

  /**
   * Cancels recording and discards all captured audio.
   *
   * Similar to [stop] but does not return the recorded audio. Use this when the user cancels
   * recording rather than completing it.
   */
  override suspend fun cancel() =
    withContext(Dispatchers.IO) {
      recordingJob?.cancel()
      recordingJob?.join()
      recordingJob = null

      try {
        audioRecord?.takeIf { it.recordingState == AudioRecord.RECORDSTATE_RECORDING }?.stop()
      } catch (e: IllegalStateException) {
        // AudioRecord already stopped or in invalid state
        Log.w(TAG, "AudioRecord already stopped during cancel()", e)
      }
      audioRecord?.release()
      audioRecord = null

      samplesMutex.withLock {
        recordedSamples.clear()
      }
    }
}
