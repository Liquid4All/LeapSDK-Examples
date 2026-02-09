package ai.liquid.leapaudiodemo

import android.content.Context
import android.media.AudioAttributes
import android.media.AudioFocusRequest
import android.media.AudioFormat
import android.media.AudioManager
import android.media.AudioTrack
import android.util.Log
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withTimeout

/**
 * Handles audio playback using Android AudioTrack.
 *
 * Supports both one-shot playback and streaming playback with buffering. Uses a bounded Channel
 * (capacity 100) to prevent out-of-memory if audio generation outpaces playback.
 *
 * Manages audio focus to properly interact with other apps and system audio.
 *
 * @param context Application context for accessing AudioManager
 * @param onPlaybackInterrupted Callback invoked when playback is interrupted by focus loss
 * @param onPlaybackCompleted Callback invoked when one-shot playback completes naturally
 * @param onPlaybackError Callback invoked when playback fails with an error message
 */
class AudioPlayer(
  context: Context,
  private val onPlaybackInterrupted: (() -> Unit)? = null,
  private val onPlaybackCompleted: (() -> Unit)? = null,
  private val onPlaybackError: ((String) -> Unit)? = null,
  private val onStreamingCompleted: (() -> Unit)? = null,
) : AudioPlayback {
  private var audioTrack: AudioTrack? = null
  private var streamingJob: Job? = null
  private var playbackJob: Job? = null
  private var audioQueue: Channel<FloatArray>? = null
  private val coroutineScope = CoroutineScope(Dispatchers.IO)
  private val cleanupMutex = Mutex()

  companion object {
    private const val TAG = "AudioPlayer"
    // Audio streaming buffer capacity
    // At 24kHz with ~1000-1200 samples/chunk (40-50ms/chunk):
    // - 100 buffers = ~4-5 seconds of buffering
    // - 200 buffers = ~8-10 seconds of buffering
    // Increased to 200 to handle fast audio generation without dropping samples
    private const val AUDIO_QUEUE_CAPACITY = 200
  }

  private val audioManager = context.getSystemService(Context.AUDIO_SERVICE) as AudioManager
  private var audioFocusRequest: AudioFocusRequest? = null

  /**
   * Audio focus change listener.
   *
   * Handles audio focus changes from the system (phone calls, notifications, other apps).
   * For AI-generated audio, we don't auto-resume after interruption because:
   * 1. The user may have moved on to other tasks
   * 2. Resuming mid-sentence would be confusing
   * 3. User can manually replay if desired
   */
  private val focusChangeListener =
    AudioManager.OnAudioFocusChangeListener { focusChange ->
      when (focusChange) {
        AudioManager.AUDIOFOCUS_LOSS -> {
          // Permanent loss (e.g., another app took focus) - stop and abandon
          stop()
          abandonAudioFocus()
          onPlaybackInterrupted?.invoke()
        }
        AudioManager.AUDIOFOCUS_LOSS_TRANSIENT -> {
          // Temporary loss (e.g., notification) - stop but don't abandon
          // We don't resume because AI audio resumption mid-sentence is confusing
          stop()
          onPlaybackInterrupted?.invoke()
        }
      }
    }

  /**
   * Requests audio focus from the system.
   *
   * @return true if focus was granted, false otherwise
   */
  private fun requestAudioFocus(): Boolean {
    val focusRequest =
      AudioFocusRequest.Builder(AudioManager.AUDIOFOCUS_GAIN)
        .setAudioAttributes(
          AudioAttributes.Builder()
            .setUsage(AudioAttributes.USAGE_MEDIA)
            .setContentType(AudioAttributes.CONTENT_TYPE_SPEECH)
            .build()
        )
        .setOnAudioFocusChangeListener(focusChangeListener)
        .build()

    audioFocusRequest = focusRequest
    val result = audioManager.requestAudioFocus(focusRequest)
    return result == AudioManager.AUDIOFOCUS_REQUEST_GRANTED
  }

  /** Abandons audio focus. */
  private fun abandonAudioFocus() {
    audioFocusRequest?.let {
      audioManager.abandonAudioFocusRequest(it)
      audioFocusRequest = null
    }
  }

  /**
   * Starts streaming audio playback mode.
   *
   * Creates a bounded channel for buffering audio samples and starts a coroutine to play samples as
   * they arrive. Call [writeStream] to send audio data, and [stopStreaming] when complete.
   *
   * @param sampleRate Sample rate in Hz for playback (typically 24000)
   */
  override fun startStreaming(sampleRate: Int) {
    // Clean up any existing playback synchronously before creating new resources
    // Cancel jobs first (non-blocking)
    playbackJob?.cancel()
    playbackJob = null
    streamingJob?.cancel()
    streamingJob = null

    // Close and release resources (safe to do without mutex since jobs are cancelled)
    audioQueue?.close()
    audioQueue = null
    audioTrack?.apply {
      try {
        stop()
        release()
      } catch (e: Exception) {
        Log.e(TAG, "Error releasing AudioTrack in startStreaming", e)
      }
    }
    audioTrack = null
    abandonAudioFocus()

    // Request audio focus BEFORE allocating resources
    if (!requestAudioFocus()) {
      // Focus request failed - don't allocate resources
      Log.e(TAG, "Failed to request audio focus")
      onPlaybackError?.invoke("Cannot play audio. Another app is using audio.")
      return
    }

    // Create bounded channel to prevent OOM if generation outpaces playback
    // This prevents unbounded memory growth while ensuring smooth playback
    audioQueue = Channel(capacity = AUDIO_QUEUE_CAPACITY)

    val minBufferSize =
      AudioTrack.getMinBufferSize(
        sampleRate,
        AudioFormat.CHANNEL_OUT_MONO,
        AudioFormat.ENCODING_PCM_FLOAT,
      )

    // Use larger buffer for smoother playback
    // 8x multiplier provides good balance: large enough to prevent underruns,
    // small enough to keep latency acceptable (~160ms at 24kHz)
    val bufferSize = minBufferSize * 8

    audioTrack =
      AudioTrack.Builder()
        .setAudioAttributes(
          AudioAttributes.Builder()
            .setUsage(AudioAttributes.USAGE_MEDIA)
            .setContentType(AudioAttributes.CONTENT_TYPE_SPEECH)
            .build()
        )
        .setAudioFormat(
          AudioFormat.Builder()
            .setEncoding(AudioFormat.ENCODING_PCM_FLOAT)
            .setSampleRate(sampleRate)
            .setChannelMask(AudioFormat.CHANNEL_OUT_MONO)
            .build()
        )
        .setBufferSizeInBytes(bufferSize)
        .setTransferMode(AudioTrack.MODE_STREAM)
        .build()

    // Start playback thread
    val queue = audioQueue
    val track = audioTrack // Capture local reference to avoid race conditions
    streamingJob =
      coroutineScope.launch {
        Log.d(TAG, "Starting streaming playback job")

        Log.d(TAG, "Audio focus granted, starting playback")
        track?.play()

        // Process audio queue
        if (queue != null && track != null) {
          Log.d(TAG, "Starting to consume audio queue")
          try {
            for (samples in queue) {
              // Check if coroutine is still active before writing
              if (!isActive) {
                Log.d(TAG, "Coroutine cancelled, stopping playback")
                break
              }
              track.write(samples, 0, samples.size, AudioTrack.WRITE_BLOCKING)
            }
            Log.d(TAG, "Audio queue consumption completed")
            // Notify that streaming playback has completed
            onStreamingCompleted?.invoke()
          } catch (e: Exception) {
            // AudioTrack might have been released - exit gracefully
            Log.e(TAG, "Error during audio playback", e)
          }
        } else {
          Log.e(TAG, "Queue or track is null: queue=$queue, track=$track")
        }
      }
  }

  /**
   * Writes audio samples to the streaming buffer.
   *
   * Call this repeatedly to stream audio data. Samples are queued in a channel and played by the
   * streaming coroutine. Uses trySend to avoid accumulating suspended coroutines when the channel
   * is full.
   *
   * @param samples Float array of audio samples in range [-1.0, 1.0]
   * @return true if samples were successfully queued, false if channel is full
   */
  override fun writeStream(samples: FloatArray): Boolean {
    val queue = audioQueue
    return if (queue != null && streamingJob != null) {
      // Use trySend to avoid accumulating suspended coroutines
      // If the channel is full, we drop this chunk (backpressure)
      queue.trySend(samples).isSuccess
    } else {
      false
    }
  }

  /**
   * Finishes streaming playback gracefully.
   *
   * Closes the audio channel so no more samples can be written, but allows the playback coroutine
   * to finish playing all buffered samples. The playback will stop naturally when the buffer is
   * empty.
   */
  override fun finishStreaming() {
    // Just close the channel - the playback coroutine will finish when it's drained
    audioQueue?.close()
  }

  /**
   * Stops streaming playback immediately.
   *
   * Cancels the streaming coroutine, closes the audio channel, and releases all resources including
   * AudioTrack and audio focus.
   */
  override fun stopStreaming() {
    coroutineScope.launch {
      try {
        // Timeout prevents indefinite hang if mutex is stuck
        withTimeout(5000) {
          cleanupMutex.withLock {
            val job = streamingJob
            val queue = audioQueue
            val track = audioTrack

            // Cancel the streaming job first to stop writes
            job?.cancel()

            // Wait for job to finish
            job?.join()

            // Close channel and release resources
            queue?.close()
            track?.stop()
            track?.release()

            // Clear references and abandon audio focus
            streamingJob = null
            audioQueue = null
            audioTrack = null
            abandonAudioFocus()
          }
        }
      } catch (e: kotlinx.coroutines.TimeoutCancellationException) {
        Log.e(TAG, "Timeout while stopping streaming - forcing cleanup", e)
        // Force cleanup without mutex to prevent permanent stuck state
        streamingJob?.cancel()
        audioQueue?.close()
        try {
          audioTrack?.stop()
          audioTrack?.release()
        } catch (ex: Exception) {
          Log.e(TAG, "Error during force cleanup", ex)
        }
        streamingJob = null
        audioQueue = null
        audioTrack = null
        abandonAudioFocus()
      }
    }
  }

  /**
   * Plays audio samples immediately (one-shot playback).
   *
   * For pre-recorded audio that doesn't need streaming. Starts playback asynchronously and monitors
   * completion.
   *
   * @param samples Float array of audio samples
   * @param sampleRate Sample rate in Hz
   */
  override fun play(samples: FloatArray, sampleRate: Int) {
    // Clean up any existing playback synchronously before creating new resources
    playbackJob?.cancel()
    playbackJob = null
    streamingJob?.cancel()
    streamingJob = null
    audioQueue?.close()
    audioQueue = null
    audioTrack?.apply {
      try {
        stop()
        release()
      } catch (e: Exception) {
        Log.e(TAG, "Error releasing AudioTrack in play", e)
      }
    }
    audioTrack = null
    abandonAudioFocus()

    val minBufferSize =
      AudioTrack.getMinBufferSize(
        sampleRate,
        AudioFormat.CHANNEL_OUT_MONO,
        AudioFormat.ENCODING_PCM_FLOAT,
      )

    audioTrack =
      AudioTrack.Builder()
        .setAudioAttributes(
          AudioAttributes.Builder()
            .setUsage(AudioAttributes.USAGE_MEDIA)
            .setContentType(AudioAttributes.CONTENT_TYPE_SPEECH)
            .build()
        )
        .setAudioFormat(
          AudioFormat.Builder()
            .setEncoding(AudioFormat.ENCODING_PCM_FLOAT)
            .setSampleRate(sampleRate)
            .setChannelMask(AudioFormat.CHANNEL_OUT_MONO)
            .build()
        )
        .setBufferSizeInBytes(maxOf(minBufferSize, samples.size * 4))
        .setTransferMode(AudioTrack.MODE_STATIC)
        .build()

    // Request audio focus before playing
    if (!requestAudioFocus()) {
      // Focus request failed - clean up and notify user
      reset()
      onPlaybackError?.invoke("Cannot play audio. Another app is using audio.")
      return
    }

    val track = audioTrack
    if (track != null) {
      track.write(samples, 0, samples.size, AudioTrack.WRITE_BLOCKING)

      // Set up notification marker at the end of playback for completion callback
      // This is more efficient than polling with delay()
      track.notificationMarkerPosition = samples.size
      track.setPlaybackPositionUpdateListener(object : AudioTrack.OnPlaybackPositionUpdateListener {
        override fun onMarkerReached(track: AudioTrack?) {
          // Playback completed naturally
          onPlaybackCompleted?.invoke()
        }

        override fun onPeriodicNotification(track: AudioTrack?) {
          // Not used
        }
      })

      track.play()
    }
  }

  /**
   * Stops audio playback immediately.
   *
   * Stops and releases the AudioTrack. Can be called during streaming or one-shot playback.
   */
  override fun stop() {
    coroutineScope.launch {
      try {
        // Timeout prevents indefinite hang if mutex is stuck
        withTimeout(5000) {
          cleanupMutex.withLock {
            playbackJob?.cancel()
            playbackJob = null
            streamingJob?.cancel()
            streamingJob?.join()
            streamingJob = null
            audioQueue?.close()
            audioQueue = null
            audioTrack?.apply {
              stop()
              release()
            }
            audioTrack = null
            abandonAudioFocus()
          }
        }
      } catch (e: kotlinx.coroutines.TimeoutCancellationException) {
        Log.e(TAG, "Timeout while stopping - forcing cleanup", e)
        // Force cleanup without mutex to prevent permanent stuck state
        playbackJob?.cancel()
        streamingJob?.cancel()
        audioQueue?.close()
        try {
          audioTrack?.stop()
          audioTrack?.release()
        } catch (ex: Exception) {
          Log.e(TAG, "Error during force cleanup", ex)
        }
        playbackJob = null
        streamingJob = null
        audioQueue = null
        audioTrack = null
        abandonAudioFocus()
      }
    }
  }

  /**
   * Resets the audio player state.
   *
   * Cancels streaming, closes channels, and releases AudioTrack resources. Called internally before
   * starting new playback.
   */
  override fun reset() {
    coroutineScope.launch {
      try {
        // Timeout prevents indefinite hang if mutex is stuck
        withTimeout(5000) {
          cleanupMutex.withLock {
            playbackJob?.cancel()
            playbackJob = null
            streamingJob?.cancel()
            streamingJob?.join()
            streamingJob = null
            audioQueue?.close()
            audioQueue = null
            audioTrack?.apply {
              stop()
              release()
            }
            audioTrack = null
            abandonAudioFocus()
          }
        }
      } catch (e: kotlinx.coroutines.TimeoutCancellationException) {
        Log.e(TAG, "Timeout while resetting - forcing cleanup", e)
        // Force cleanup without mutex to prevent permanent stuck state
        playbackJob?.cancel()
        streamingJob?.cancel()
        audioQueue?.close()
        try {
          audioTrack?.stop()
          audioTrack?.release()
        } catch (ex: Exception) {
          Log.e(TAG, "Error during force cleanup", ex)
        }
        playbackJob = null
        streamingJob = null
        audioQueue = null
        audioTrack = null
        abandonAudioFocus()
      }
    }
  }

  /**
   * Releases all audio player resources.
   *
   * Should be called when the player is no longer needed, typically in ViewModel onCleared().
   */
  override fun release() {
    reset()
  }
}
