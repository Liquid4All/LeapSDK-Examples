package ai.liquid.leapaudiodemo

import android.content.Context
import android.media.AudioAttributes
import android.media.AudioFocusRequest
import android.media.AudioFormat
import android.media.AudioManager
import android.media.AudioTrack
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch

/**
 * Handles audio playback using Android AudioTrack.
 *
 * Supports both one-shot playback and streaming playback with buffering.
 * Uses a bounded Channel (capacity 100) to prevent out-of-memory if
 * audio generation outpaces playback.
 *
 * Manages audio focus to properly interact with other apps and system audio.
 *
 * @param context Application context for accessing AudioManager
 * @param onPlaybackInterrupted Callback invoked when playback is interrupted by focus loss
 * @param onPlaybackCompleted Callback invoked when one-shot playback completes naturally
 */
class AudioPlayer(
    context: Context,
    private val onPlaybackInterrupted: (() -> Unit)? = null,
    private val onPlaybackCompleted: (() -> Unit)? = null
) {
    private var audioTrack: AudioTrack? = null
    private var streamingJob: Job? = null
    private var playbackJob: Job? = null
    private var audioQueue: Channel<FloatArray>? = null
    private val coroutineScope = CoroutineScope(Dispatchers.IO)

    private val audioManager = context.getSystemService(Context.AUDIO_SERVICE) as AudioManager
    private var audioFocusRequest: AudioFocusRequest? = null

    private val focusChangeListener = AudioManager.OnAudioFocusChangeListener { focusChange ->
        when (focusChange) {
            AudioManager.AUDIOFOCUS_LOSS -> {
                // Permanent loss - stop playback and abandon focus
                stop()
                abandonAudioFocus()
                onPlaybackInterrupted?.invoke()
            }
            AudioManager.AUDIOFOCUS_LOSS_TRANSIENT -> {
                // Temporary loss - stop playback (don't resume AI audio)
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
        val focusRequest = AudioFocusRequest.Builder(AudioManager.AUDIOFOCUS_GAIN)
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

    /**
     * Abandons audio focus.
     */
    private fun abandonAudioFocus() {
        audioFocusRequest?.let {
            audioManager.abandonAudioFocusRequest(it)
            audioFocusRequest = null
        }
    }

    /**
     * Starts streaming audio playback mode.
     *
     * Creates a bounded channel for buffering audio samples and starts
     * a coroutine to play samples as they arrive. Call [writeStream] to
     * send audio data, and [stopStreaming] when complete.
     *
     * @param sampleRate Sample rate in Hz for playback (typically 24000)
     */
    fun startStreaming(sampleRate: Int) {
        reset()

        // Create bounded channel to prevent OOM if generation outpaces playback
        // Capacity of 100 buffers provides ~4-5 seconds of buffering at typical
        // chunk sizes (1000-1200 samples/chunk at 24kHz = 40-50ms/chunk)
        // This prevents unbounded memory growth while ensuring smooth playback
        audioQueue = Channel(capacity = 100)

        val minBufferSize = AudioTrack.getMinBufferSize(
            sampleRate,
            AudioFormat.CHANNEL_OUT_MONO,
            AudioFormat.ENCODING_PCM_FLOAT
        )

        // Use larger buffer for smoother playback
        val bufferSize = minBufferSize * 8

        audioTrack = AudioTrack.Builder()
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
        streamingJob = coroutineScope.launch {
            // Request audio focus before starting playback
            if (!requestAudioFocus()) {
                // Focus request failed - clean up and notify
                reset()
                onPlaybackInterrupted?.invoke()
                return@launch
            }

            track?.play()

            // Process audio queue
            if (queue != null && track != null) {
                try {
                    for (samples in queue) {
                        // Check if coroutine is still active before writing
                        if (!isActive) break
                        track.write(samples, 0, samples.size, AudioTrack.WRITE_BLOCKING)
                    }
                } catch (e: Exception) {
                    // AudioTrack might have been released - exit gracefully
                }
            }
        }
    }

    /**
     * Writes audio samples to the streaming buffer.
     *
     * Call this repeatedly to stream audio data. Samples are queued
     * in a channel and played by the streaming coroutine. Uses trySend
     * to avoid accumulating suspended coroutines when the channel is full.
     *
     * @param samples Float array of audio samples in range [-1.0, 1.0]
     */
    fun writeStream(samples: FloatArray) {
        val queue = audioQueue
        if (queue != null && streamingJob != null) {
            // Use trySend to avoid accumulating suspended coroutines
            // If the channel is full, we drop this chunk (backpressure)
            queue.trySend(samples)
        }
    }

    /**
     * Stops streaming playback.
     *
     * Cancels the streaming coroutine, closes the audio channel,
     * and releases all resources including AudioTrack and audio focus.
     */
    fun stopStreaming() {
        val job = streamingJob
        val queue = audioQueue
        val track = audioTrack

        // Cancel the streaming job first to stop writes
        job?.cancel()

        // Then clean up in a coroutine
        coroutineScope.launch {
            queue?.close()
            job?.join()
            track?.stop()
            track?.release()

            // Clear references and abandon audio focus
            streamingJob = null
            audioQueue = null
            audioTrack = null
            abandonAudioFocus()
        }
    }

    /**
     * Plays audio samples immediately (one-shot playback).
     *
     * For pre-recorded audio that doesn't need streaming.
     * Starts playback asynchronously and monitors completion.
     *
     * @param samples Float array of audio samples
     * @param sampleRate Sample rate in Hz
     */
    fun play(samples: FloatArray, sampleRate: Int) {
        reset()

        val minBufferSize = AudioTrack.getMinBufferSize(
            sampleRate,
            AudioFormat.CHANNEL_OUT_MONO,
            AudioFormat.ENCODING_PCM_FLOAT
        )

        audioTrack = AudioTrack.Builder()
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
            // Focus request failed - clean up and notify
            reset()
            onPlaybackInterrupted?.invoke()
            return
        }

        val track = audioTrack
        if (track != null) {
            track.write(samples, 0, samples.size, AudioTrack.WRITE_BLOCKING)
            track.play()

            // Monitor playback completion
            playbackJob = coroutineScope.launch {
                try {
                    // Poll playback state until complete
                    while (track.playState == AudioTrack.PLAYSTATE_PLAYING) {
                        delay(100)
                    }
                    // Playback completed naturally
                    onPlaybackCompleted?.invoke()
                } catch (e: Exception) {
                    // Playback was stopped/interrupted
                }
            }
        }
    }

    /**
     * Stops audio playback immediately.
     *
     * Stops and releases the AudioTrack. Can be called during
     * streaming or one-shot playback.
     */
    fun stop() {
        playbackJob?.cancel()
        playbackJob = null
        audioTrack?.apply {
            stop()
            release()
        }
        audioTrack = null
        stopStreaming()
        abandonAudioFocus()
    }

    /**
     * Resets the audio player state.
     *
     * Cancels streaming, closes channels, and releases AudioTrack resources.
     * Called internally before starting new playback.
     */
    fun reset() {
        playbackJob?.cancel()
        playbackJob = null
        streamingJob?.cancel()
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

    /**
     * Releases all audio player resources.
     *
     * Should be called when the player is no longer needed,
     * typically in ViewModel onCleared().
     */
    fun release() {
        reset()
    }
}
