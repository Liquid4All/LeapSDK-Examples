package ai.liquid.leap.uidemo

import ai.liquid.leap.Conversation
import ai.liquid.leap.GenerationOptions
import ai.liquid.leap.message.ChatMessage
import ai.liquid.leap.message.ChatMessageContent
import ai.liquid.leap.message.GenerationStats
import ai.liquid.leap.message.MessageResponse
import ai.liquid.leap.ui.VoiceAudioPlayer
import ai.liquid.leap.ui.VoiceAudioRecorder
import ai.liquid.leap.ui.VoiceConversation
import android.annotation.SuppressLint
import android.media.AudioFormat
import android.media.AudioRecord
import android.media.AudioTrack
import android.media.MediaRecorder
import java.nio.ByteBuffer
import java.nio.ByteOrder
import kotlin.math.sqrt
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.collect
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext

// ─── Recording ────────────────────────────────────────────────────────────────

/**
 * Android implementation of [VoiceAudioRecorder].
 *
 * Records from the device microphone at 16 kHz mono PCM float. Call [start] after RECORD_AUDIO
 * permission is granted. Call [stop] to finish and receive raw float PCM samples, or [cancel] to
 * discard. [amplitude] is updated in real time for orb animation (0–1 RMS, scaled for visibility).
 */
class AndroidAudioRecorder : VoiceAudioRecorder {
  private var audioRecord: AudioRecord? = null
  private var recordingJob: Job? = null
  private val samples = mutableListOf<Float>()
  private val mutex = Mutex()
  private val scope = CoroutineScope(Dispatchers.IO)

  override var amplitude: Float = 0f
    private set

  override val nativeSampleRate: Int = SAMPLE_RATE

  companion object {
    const val SAMPLE_RATE = 16_000
    private const val MAX_RECORDING_SECONDS = 60
    private const val MAX_SAMPLES = SAMPLE_RATE * MAX_RECORDING_SECONDS
    private const val MIN_BUFFER_SIZE = 4096
    private const val BYTES_PER_FLOAT_SAMPLE = 4
    private const val RECORDING_AMPLITUDE_SCALE = 10f
  }

  /**
   * Starts microphone recording. Returns `false` if [AudioRecord] could not be initialized (e.g.,
   * permission denied or hardware unavailable).
   */
  @SuppressLint("MissingPermission")
  override fun start(): Boolean {
    samples.clear()
    val bufSize =
      maxOf(
        AudioRecord.getMinBufferSize(
          SAMPLE_RATE,
          AudioFormat.CHANNEL_IN_MONO,
          AudioFormat.ENCODING_PCM_FLOAT,
        ),
        MIN_BUFFER_SIZE,
      )
    val rec = buildAudioRecord(bufSize) ?: return false
    audioRecord = rec
    startRecordingJob(rec, bufSize)
    return true
  }

  @SuppressLint("MissingPermission")
  private fun buildAudioRecord(bufSize: Int): AudioRecord? {
    if (bufSize <= 0) return null
    val rec =
      AudioRecord(
        MediaRecorder.AudioSource.MIC,
        SAMPLE_RATE,
        AudioFormat.CHANNEL_IN_MONO,
        AudioFormat.ENCODING_PCM_FLOAT,
        bufSize,
      )
    val started =
      rec.state == AudioRecord.STATE_INITIALIZED && runCatching { rec.startRecording() }.isSuccess
    return if (started) rec
    else {
      rec.release()
      null
    }
  }

  private fun startRecordingJob(rec: AudioRecord, bufSize: Int) {
    val buf = FloatArray(bufSize / BYTES_PER_FLOAT_SAMPLE)
    recordingJob =
      scope
        .launch {
          while (isActive) {
            val read = rec.read(buf, 0, buf.size, AudioRecord.READ_BLOCKING)
            if (read <= 0) break

            var sum = 0f
            for (i in 0 until read) sum += buf[i] * buf[i]
            amplitude = (sqrt(sum / read) * RECORDING_AMPLITUDE_SCALE).coerceIn(0f, 1f)

            mutex.withLock {
              val toAdd = minOf(read, MAX_SAMPLES - samples.size)
              for (i in 0 until toAdd) samples.add(buf[i])
              if (samples.size >= MAX_SAMPLES) return@launch
            }
          }
        }
        .also { job ->
          job.invokeOnCompletion {
            try {
              rec.takeIf { it.recordingState == AudioRecord.RECORDSTATE_RECORDING }?.stop()
            } catch (_: IllegalStateException) {}
          }
        }
  }

  /**
   * Stops recording and returns the captured audio as raw float PCM samples at [nativeSampleRate].
   */
  override suspend fun stop(): FloatArray =
    withContext(Dispatchers.IO) {
      recordingJob?.cancel()
      recordingJob?.join()
      recordingJob = null
      try {
        audioRecord?.takeIf { it.recordingState == AudioRecord.RECORDSTATE_RECORDING }?.stop()
      } catch (_: IllegalStateException) {}
      audioRecord?.release()
      audioRecord = null
      amplitude = 0f

      mutex.withLock {
        val arr = samples.toFloatArray()
        samples.clear()
        arr
      }
    }

  /** Stops recording and discards all captured samples. */
  override suspend fun cancel() =
    withContext(Dispatchers.IO) {
      recordingJob?.cancel()
      recordingJob?.join()
      recordingJob = null
      try {
        audioRecord?.takeIf { it.recordingState == AudioRecord.RECORDSTATE_RECORDING }?.stop()
      } catch (_: IllegalStateException) {}
      audioRecord?.release()
      audioRecord = null
      amplitude = 0f
      mutex.withLock { samples.clear() }
    }
}

// ─── Playback ─────────────────────────────────────────────────────────────────

/**
 * Android implementation of [VoiceAudioPlayer].
 *
 * Streams audio chunks to the device speaker via [AudioTrack]. The [AudioTrack] is initialized
 * automatically on the first [enqueue] call. [amplitude] is updated per enqueued chunk for orb
 * animation.
 */
class AndroidAudioPlayer : VoiceAudioPlayer {
  private var track: AudioTrack? = null
  private var streamingJob: Job? = null
  private var audioChannel: Channel<FloatArray>? = null
  private val scope = CoroutineScope(Dispatchers.IO)

  override var amplitude: Float = 0f
    private set

  companion object {
    private const val AUDIO_TRACK_BUFFER_MULTIPLIER = 8
    private const val PLAYBACK_AMPLITUDE_SCALE = 5f
    private const val UNDERRUN_THRESHOLD_SECONDS = 0.05f
    private const val NANOS_PER_SECOND = 1_000_000_000.0
    private const val RTF_WARMUP_SECONDS = 0.5
    private const val BUFFER_TARGET_MIN_SECONDS = 0.2
    private const val BUFFER_TARGET_MAX_SECONDS = 3.0
    private const val RTF_BUFFER_SCALE = 2.0
    private const val PLAYHEAD_MASK = 0xFFFFFFFFL
    private const val HARDWARE_DRAIN_DELAY_MS = 150L
  }

  @Suppress("CyclomaticComplexMethod")
  @OptIn(kotlinx.coroutines.ExperimentalCoroutinesApi::class)
  private fun startStreaming(sampleRate: Int) {
    streamingJob?.cancel()
    audioChannel?.close()
    track?.apply {
      try {
        stop()
        release()
      } catch (_: Exception) {}
    }

    val minBuf =
      AudioTrack.getMinBufferSize(
        sampleRate,
        AudioFormat.CHANNEL_OUT_MONO,
        AudioFormat.ENCODING_PCM_FLOAT,
      )
    val t =
      AudioTrack.Builder()
        .setAudioFormat(
          AudioFormat.Builder()
            .setEncoding(AudioFormat.ENCODING_PCM_FLOAT)
            .setSampleRate(sampleRate)
            .setChannelMask(AudioFormat.CHANNEL_OUT_MONO)
            .build()
        )
        .setBufferSizeInBytes(minBuf * AUDIO_TRACK_BUFFER_MULTIPLIER)
        .setTransferMode(AudioTrack.MODE_STREAM)
        .build()
    track = t

    val channel = Channel<FloatArray>(capacity = 100)
    audioChannel = channel
    streamingJob =
      scope.launch {
        var totalFramesWritten = 0L
        var isPlaying = false
        val bufferedSamples = mutableListOf<FloatArray>()
        var bufferedFramesCount = 0

        val underrunThresholdFrames = (sampleRate * UNDERRUN_THRESHOLD_SECONDS).toInt() // 50ms
        var firstChunkNanos = 0L
        var totalFramesReceived = 0L

        for (s in channel) {
          if (!isActive) break

          if (totalFramesReceived == 0L) {
            firstChunkNanos = System.nanoTime()
          }
          totalFramesReceived += s.size

          val elapsedTime = (System.nanoTime() - firstChunkNanos) / NANOS_PER_SECOND
          val rtf =
            if (elapsedTime > RTF_WARMUP_SECONDS)
              (totalFramesReceived.toDouble() / sampleRate) / elapsedTime
            else 1.0
          val bufferTargetSeconds =
            if (rtf >= 1.0) BUFFER_TARGET_MIN_SECONDS
            else
              (RTF_BUFFER_SCALE / rtf - RTF_BUFFER_SCALE).coerceIn(
                BUFFER_TARGET_MIN_SECONDS,
                BUFFER_TARGET_MAX_SECONDS,
              )
          val highWaterMarkFrames = (sampleRate * bufferTargetSeconds).toInt()

          if (!isPlaying) {
            bufferedSamples.add(s)
            bufferedFramesCount += s.size

            if (bufferedFramesCount >= highWaterMarkFrames) {
              isPlaying = true
              t.play()
              for (buf in bufferedSamples) {
                val written = t.write(buf, 0, buf.size, AudioTrack.WRITE_BLOCKING)
                if (written > 0) {
                  totalFramesWritten += written
                  updateAmplitude(buf, written)
                }
              }
              bufferedSamples.clear()
              bufferedFramesCount = 0
            }
          } else {
            val written = t.write(s, 0, s.size, AudioTrack.WRITE_BLOCKING)
            if (written > 0) {
              totalFramesWritten += written
              updateAmplitude(s, written)
            }

            val framesPlayed = t.playbackHeadPosition.toLong() and PLAYHEAD_MASK
            val framesInHardwareBuffer = totalFramesWritten - framesPlayed

            if (framesInHardwareBuffer < underrunThresholdFrames && channel.isEmpty) {
              t.pause()
              isPlaying = false
            }
          }
        }
      }
  }

  /**
   * Enqueues [samples] at [sampleRate] Hz for streaming playback. Initializes the [AudioTrack] on
   * the first call. Updates [amplitude] for orb animation.
   */
  override fun enqueue(samples: FloatArray, sampleRate: Int) {
    if (samples.isEmpty()) return
    if (track == null) startStreaming(sampleRate)
    audioChannel?.trySend(samples.copyOf())
  }

  /** Calculate RMS amplitude from samples being written to the hardware. */
  private fun updateAmplitude(samples: FloatArray, count: Int) {
    if (count <= 0) return
    var sum = 0f
    for (i in 0 until count) sum += samples[i] * samples[i]
    amplitude = (sqrt(sum / count) * PLAYBACK_AMPLITUDE_SCALE).coerceIn(0f, 1f)
  }

  /**
   * Signals end of stream, waits for all enqueued audio to be written to the [AudioTrack], and
   * allows the hardware buffer to drain before returning.
   *
   * After calling this, no more [enqueue] calls should be made. Call [stop] afterwards to release
   * hardware resources.
   */
  override suspend fun waitForPlayback() {
    audioChannel?.close() // signal no more data — streaming job will exit its for-loop
    streamingJob?.join() // wait for all writes to the AudioTrack to complete
    delay(HARDWARE_DRAIN_DELAY_MS) // allow hardware buffer to finish rendering
  }

  /** Stops playback immediately and releases the [AudioTrack]. */
  override fun stop() {
    streamingJob?.cancel()
    audioChannel?.close()
    audioChannel = null
    track?.apply {
      try {
        pause()
        flush()
        release()
      } catch (_: Exception) {}
    }
    track = null
    amplitude = 0f
  }
}

// ─── WAV encoding ─────────────────────────────────────────────────────────────

private const val WAV_HEADER_SIZE_BYTES = 44
private const val WAV_RIFF_DATA_OFFSET = 36
private const val WAV_FMT_CHUNK_SIZE = 16
private const val WAV_BITS_PER_SAMPLE = 16
private const val PCM16_MAX_VALUE = 32767

/**
 * Encodes [samples] (float PCM, range −1..1) as a 16-bit mono WAV [ByteArray] at [sampleRate] Hz.
 */
@Suppress("MagicNumber")
internal fun encodePcm16Wav(samples: FloatArray, sampleRate: Int): ByteArray {
  val dataSize = samples.size * 2
  val buf = ByteBuffer.allocate(WAV_HEADER_SIZE_BYTES + dataSize).order(ByteOrder.LITTLE_ENDIAN)
  buf.put("RIFF".toByteArray(Charsets.US_ASCII))
  buf.putInt(WAV_RIFF_DATA_OFFSET + dataSize)
  buf.put("WAVE".toByteArray(Charsets.US_ASCII))
  buf.put("fmt ".toByteArray(Charsets.US_ASCII))
  buf.putInt(WAV_FMT_CHUNK_SIZE)
  buf.putShort(1.toShort()) // PCM format
  buf.putShort(1.toShort()) // mono
  buf.putInt(sampleRate)
  buf.putInt(sampleRate * 2) // byte rate
  buf.putShort(2.toShort()) // block align
  buf.putShort(WAV_BITS_PER_SAMPLE.toShort())
  buf.put("data".toByteArray(Charsets.US_ASCII))
  buf.putInt(dataSize)
  for (s in samples) buf.putShort(
    (s.coerceIn(-1f, 1f) * PCM16_MAX_VALUE.toFloat()).toInt().toShort()
  )
  return buf.array()
}

// ─── VoiceConversation adapter ────────────────────────────────────────────────

/**
 * Android implementation of [VoiceConversation] backed by a `leap-sdk` [Conversation].
 *
 * Encodes the raw float PCM [FloatArray] from [VoiceAudioRecorder.stop] as a 16-bit mono WAV
 * [ByteArray] (via [encodePcm16Wav]) before passing it to the SDK. On each
 * [MessageResponse.AudioSample] event, calls [onAudioChunk] immediately so the player can start
 * streaming before generation finishes. Returns [GenerationStats] from the
 * [MessageResponse.Complete] event, or `null` if the model does not report stats.
 *
 * @param conv Active [Conversation] to use for this turn.
 * @param systemPrompt System prompt used when creating a fresh conversation via [reset].
 */
class LeapVoiceConversation(private val conv: Conversation, private val systemPrompt: String) :
  VoiceConversation {

  override suspend fun generateResponse(
    audioSamples: FloatArray,
    sampleRate: Int,
    onAudioChunk: (samples: FloatArray, sampleRate: Int) -> Unit,
  ): GenerationStats? {
    val wavBytes = encodePcm16Wav(audioSamples, sampleRate)
    var stats: GenerationStats? = null
    conv
      .generateResponse(
        message =
          ChatMessage(
            role = ChatMessage.Role.USER,
            content = listOf(ChatMessageContent.Audio(wavBytes)),
          ),
        generationOptions = GenerationOptions(),
      )
      .collect { response ->
        when (response) {
          is MessageResponse.AudioSample -> onAudioChunk(response.samples, response.sampleRate)
          is MessageResponse.Complete -> stats = response.stats
          else -> Unit
        }
      }
    return stats
  }

  override fun reset(): VoiceConversation =
    LeapVoiceConversation(
      conv = conv.modelRunner.createConversation(systemPrompt = systemPrompt),
      systemPrompt = systemPrompt,
    )
}
