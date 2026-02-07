package ai.liquid.leapaudiodemo

import android.media.AudioFormat
import android.media.AudioRecord
import android.media.MediaRecorder
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/**
 * Handles audio recording from the device microphone.
 *
 * Records audio at 16kHz mono PCM float format with a maximum duration
 * of 60 seconds to prevent out-of-memory errors. Uses coroutines for
 * non-blocking audio capture.
 */
class AudioRecorder {
    private var audioRecord: AudioRecord? = null
    private var recordingJob: Job? = null
    private val recordedSamples = mutableListOf<Float>()
    private val coroutineScope = CoroutineScope(Dispatchers.IO)

    companion object {
        private const val SAMPLE_RATE = 16000
        private const val CHANNEL_CONFIG = AudioFormat.CHANNEL_IN_MONO
        private const val AUDIO_FORMAT = AudioFormat.ENCODING_PCM_FLOAT
        private const val MAX_RECORDING_SECONDS = 60
        private val MAX_SAMPLES = SAMPLE_RATE * MAX_RECORDING_SECONDS
    }

    /**
     * Represents captured audio data.
     *
     * @property samples Audio samples as float values in range [-1.0, 1.0]
     * @property sampleRate Sample rate in Hz (typically 16000)
     */
    data class AudioCapture(
        val samples: FloatArray,
        val sampleRate: Int
    )

    /**
     * Starts audio recording from the microphone.
     *
     * Validates that the AudioRecord can be initialized before starting.
     * Records in a background coroutine until [stop] or [cancel] is called,
     * or the maximum recording duration is reached.
     *
     * @return true if recording started successfully, false if initialization failed
     */
    fun start(): Boolean {
        // Clear any previous samples before starting
        recordedSamples.clear()

        val bufferSize = AudioRecord.getMinBufferSize(
            SAMPLE_RATE,
            CHANNEL_CONFIG,
            AUDIO_FORMAT
        )

        if (bufferSize <= 0) {
            // Failed to get a valid buffer size; do not start recording
            return false
        }

        audioRecord = AudioRecord(
            MediaRecorder.AudioSource.MIC,
            SAMPLE_RATE,
            CHANNEL_CONFIG,
            AUDIO_FORMAT,
            bufferSize
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

        recordingJob = coroutineScope.launch {
            val buffer = FloatArray(bufferSize / 4) // 4 bytes per float
            while (isActive && recordedSamples.size < MAX_SAMPLES) {
                val read = audioRecord?.read(buffer, 0, buffer.size, AudioRecord.READ_BLOCKING) ?: 0
                if (read > 0) {
                    val samplesToAdd = minOf(read, MAX_SAMPLES - recordedSamples.size)
                    recordedSamples.addAll(buffer.take(samplesToAdd))
                }
            }
            // If we hit the max limit, auto-stop recording
            if (recordedSamples.size >= MAX_SAMPLES) {
                try {
                    audioRecord?.takeIf { it.recordingState == AudioRecord.RECORDSTATE_RECORDING }?.stop()
                } catch (e: IllegalStateException) {
                    // AudioRecord already stopped or in invalid state
                }
            }
        }

        return true
    }

    /**
     * Stops recording and returns the captured audio.
     *
     * Cancels the recording coroutine, stops and releases the AudioRecord,
     * and returns the recorded samples if any were captured.
     *
     * @return AudioCapture containing the recorded samples, or null if no audio was captured
     */
    suspend fun stop(): AudioCapture? = withContext(Dispatchers.IO) {
        recordingJob?.cancel()
        recordingJob?.join()
        recordingJob = null

        try {
            audioRecord?.takeIf { it.recordingState == AudioRecord.RECORDSTATE_RECORDING }?.stop()
        } catch (e: IllegalStateException) {
            // AudioRecord already stopped or in invalid state
        }
        audioRecord?.release()
        audioRecord = null

        if (recordedSamples.isNotEmpty()) {
            AudioCapture(
                samples = recordedSamples.toFloatArray(),
                sampleRate = SAMPLE_RATE
            )
        } else {
            null
        }
    }

    /**
     * Cancels recording and discards all captured audio.
     *
     * Similar to [stop] but does not return the recorded audio.
     * Use this when the user cancels recording rather than completing it.
     */
    suspend fun cancel() = withContext(Dispatchers.IO) {
        recordingJob?.cancel()
        recordingJob?.join()
        recordingJob = null

        try {
            audioRecord?.takeIf { it.recordingState == AudioRecord.RECORDSTATE_RECORDING }?.stop()
        } catch (e: IllegalStateException) {
            // AudioRecord already stopped or in invalid state
        }
        audioRecord?.release()
        audioRecord = null
        recordedSamples.clear()
    }
}
