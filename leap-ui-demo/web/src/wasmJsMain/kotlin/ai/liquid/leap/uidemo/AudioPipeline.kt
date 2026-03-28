@file:OptIn(
  kotlin.js.ExperimentalWasmJsInterop::class,
  kotlin.wasm.unsafe.UnsafeWasmMemoryApi::class,
)

package ai.liquid.leap.uidemo

import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException
import kotlin.coroutines.suspendCoroutine
import kotlin.math.sqrt
import kotlin.wasm.unsafe.withScopedMemoryAllocator
import kotlinx.coroutines.delay

// ─── getUserMedia ─────────────────────────────────────────────────────────────

/** Requests microphone access and returns a Promise<MediaStream>. */
@JsFun("() => navigator.mediaDevices.getUserMedia({ audio: true, video: false })")
private external fun jsGetUserMediaPromise(): JsAny

/** Bridges a Promise to Kotlin callbacks. */
@JsFun(
  """(promise, onSuccess, onError) => {
  promise.then(function(v) { onSuccess(v); }, function(e) { onError(String(e)); });
}"""
)
private external fun jsAwaitPromise(
  promise: JsAny,
  onSuccess: (JsAny) -> Unit,
  onError: (JsAny?) -> Unit,
)

/** Suspends until the user grants microphone permission and returns a MediaStream handle. */
internal suspend fun getUserMedia(): JsAny = suspendCoroutine { cont ->
  jsAwaitPromise(
    jsGetUserMediaPromise(),
    onSuccess = { stream -> cont.resume(stream) },
    onError = { err ->
      cont.resumeWithException(
        IllegalStateException("getUserMedia failed: $err — ensure the page is served over HTTPS")
      )
    },
  )
}

// ─── AudioContext ─────────────────────────────────────────────────────────────

/**
 * Creates a new AudioContext at [sampleRate] Hz.
 *
 * The compressor previously attached here caused pumping artifacts on normal speech because the
 * LFM2 audio decoder already outputs values in the [-1, 1] range. Normalization in
 * [jsFloat32ArrayReduceGain] handles the rare loud chunk (peak > 1.0) directly.
 */
@JsFun(
  """(sampleRate) => {
  return new (window.AudioContext || window.webkitAudioContext)({ sampleRate: sampleRate });
}"""
)
internal external fun jsCreateAudioContext(sampleRate: Int): JsAny

/** Returns the AudioContext's current playback time (seconds). */
@JsFun("(ctx) => ctx.currentTime") internal external fun jsGetContextTime(ctx: JsAny): Double

/** Closes an AudioContext, releasing its hardware resources. */
@JsFun("(ctx) => ctx.close()") internal external fun jsCloseAudioContext(ctx: JsAny)

/**
 * Resumes a suspended AudioContext (required by browser autoplay policy). Browsers automatically
 * suspend an AudioContext created without a user gesture; call this after the user taps.
 *
 * Note: for fire-and-forget use (e.g. playback after user gesture) only. Use [resumeAudioContext]
 * when you need to await completion before connecting nodes.
 */
@JsFun("(ctx) => { if (ctx.state === 'suspended') ctx.resume(); }")
internal external fun jsResumeAudioContext(ctx: JsAny)

/**
 * Resumes a suspended AudioContext via callbacks rather than by passing the resolved Promise value.
 *
 * `ctx.resume()` returns a Promise<void> — it resolves with `undefined`. Using [jsAwaitPromise]
 * (which calls `onSuccess(v)`) would pass `undefined` to `onSuccess: (JsAny) -> Unit`, which is
 * non-nullable and causes a Kotlin/Wasm crash. This dedicated bridge avoids the issue by invoking a
 * zero-argument [onDone] callback.
 */
@JsFun(
  """(ctx, onDone, onError) => {
  if (ctx.state !== 'suspended') { onDone(); return; }
  ctx.resume().then(function() { onDone(); }, function(e) { onError(String(e)); });
}"""
)
private external fun jsResumeAudioContextAndCall(
  ctx: JsAny,
  onDone: () -> Unit,
  onError: (JsAny?) -> Unit,
)

/**
 * Suspends until the AudioContext transitions out of the 'suspended' state. Must be called before
 * connecting recording nodes — the ScriptProcessorNode's onaudioprocess will not fire on a
 * suspended context.
 */
internal suspend fun resumeAudioContext(ctx: JsAny) =
  suspendCoroutine<Unit> { cont ->
    jsResumeAudioContextAndCall(
      ctx,
      onDone = { cont.resume(Unit) },
      onError = { err ->
        cont.resumeWithException(IllegalStateException("AudioContext resume failed: $err"))
      },
    )
  }

// ─── Recording ────────────────────────────────────────────────────────────────

/** Stops all tracks on a MediaStream. */
@JsFun("(stream) => { if (stream) stream.getTracks().forEach(function(t) { t.stop(); }); }")
private external fun jsStopMediaStream(stream: JsAny)

/**
 * Wires a MediaStream through a ScriptProcessorNode and starts capturing. The [onRms] callback is
 * invoked with the RMS amplitude (0–1) of each audio buffer. Returns an opaque recorder handle.
 *
 * Note: ScriptProcessorNode is deprecated but universally supported and simple to use from
 * Kotlin/Wasm. An AudioWorklet upgrade can be done later.
 */
@JsFun(
  """(ctx, stream, bufferSize, onRms) => {
  var source = ctx.createMediaStreamSource(stream);
  var processor = ctx.createScriptProcessor(bufferSize, 1, 1);
  var chunks = [];
  processor.onaudioprocess = function(e) {
    var input = e.inputBuffer.getChannelData(0);
    // Compute RMS amplitude
    var sum = 0;
    for (var i = 0; i < input.length; i++) sum += input[i] * input[i];
    var rms = Math.sqrt(sum / input.length);
    onRms(Math.min(rms * 10.0, 1.0));  // scale up for visibility
    // Store a copy of this chunk
    var copy = new Float32Array(input.length);
    copy.set(input);
    chunks.push(copy);
  };
  source.connect(processor);
  // To avoid echo, connect through a muted gain node
  var gain = ctx.createGain();
  gain.gain.value = 0;
  processor.connect(gain);
  gain.connect(ctx.destination);
  return { source: source, processor: processor, gain: gain, stream: stream, chunks: chunks };
}"""
)
private external fun jsStartRecordingNode(
  ctx: JsAny,
  stream: JsAny,
  bufferSize: Int,
  onRms: (Float) -> Unit,
): JsAny

/**
 * Stops recording and disconnects all nodes. Stops all media stream tracks. Returns a JS
 * Float32Array containing the concatenated mono PCM samples at the AudioContext's sample rate.
 */
@JsFun(
  """(handle) => {
  // Stop media tracks first so the browser releases the mic indicator immediately.
  handle.stream.getTracks().forEach(function(t) { t.stop(); });
  handle.source.disconnect();
  handle.processor.disconnect();
  handle.gain.disconnect();
  // Concatenate all chunks into one Float32Array
  var total = 0;
  handle.chunks.forEach(function(c) { total += c.length; });
  var out = new Float32Array(total);
  var offset = 0;
  handle.chunks.forEach(function(c) { out.set(c, offset); offset += c.length; });
  return out;
}"""
)
private external fun jsStopRecordingNode(handle: JsAny): JsAny

/**
 * Encodes a JS Float32Array as a 32-bit IEEE float WAV ArrayBuffer entirely in JS.
 *
 * Avoids the WASM scoped-memory round-trip of [encodeWav]: samples are copied from the Float32Array
 * into the WAV data region with a single `Float32Array.set()` (O(1) bulk copy) rather than a
 * per-sample Kotlin bit-manipulation loop. Format 3 = IEEE_FLOAT (32-bit float, mono).
 */
@JsFun(
  """(float32Array, sampleRate) => {
  var dataSize = float32Array.length * 4;
  var buffer = new ArrayBuffer(44 + dataSize);
  var view = new DataView(buffer);
  var i = 0;
  function writeStr(s) { for (var k = 0; k < s.length; k++) view.setUint8(i++, s.charCodeAt(k)); }
  function writeI32(v) { view.setInt32(i, v, true); i += 4; }
  function writeI16(v) { view.setInt16(i, v, true); i += 2; }
  writeStr('RIFF'); writeI32(36 + dataSize); writeStr('WAVE'); writeStr('fmt ');
  writeI32(16); writeI16(3); writeI16(1); writeI32(sampleRate);
  writeI32(sampleRate * 4); writeI16(4); writeI16(32);
  writeStr('data'); writeI32(dataSize);
  new Float32Array(buffer, 44).set(float32Array);
  return buffer;
}"""
)
private external fun jsEncodeWav(float32Array: JsAny, sampleRate: Int): JsAny

/** Returns the byte length of a JS ArrayBuffer. */
@JsFun("(buf) => buf.byteLength") private external fun jsArrayBufferByteLength(buf: JsAny): Int

/**
 * Copies a JS ArrayBuffer into WASM linear memory at [byteOffset] using a single `Uint8Array.set()`
 * call, enabling conversion to Kotlin ByteArray via [withScopedMemoryAllocator].
 */
@JsFun(
  """(buf, byteOffset, len) => {
  new Uint8Array(wasmExports.memory.buffer, byteOffset, len).set(new Uint8Array(buf));
}"""
)
private external fun jsArrayBufferCopyToWasmPtr(buf: JsAny, byteOffset: Int, len: Int)

// ─── Playback ─────────────────────────────────────────────────────────────────

/**
 * Schedules a mono audio buffer for playback on [ctx] starting at [startTime] seconds. Reads
 * [length] samples by calling [getSample] with each index. Returns the start time of the next
 * buffer (startTime + duration), allowing the caller to chain buffers gaplessly.
 */
@JsFun(
  """(ctx, sampleRate, startTime, length, getSample) => {
  var buffer = ctx.createBuffer(1, length, sampleRate);
  var data = buffer.getChannelData(0);
  for (var i = 0; i < length; i++) data[i] = getSample(i);
  var source = ctx.createBufferSource();
  source.buffer = buffer;
  source.connect(ctx.destination);
  source.start(startTime);
  return startTime + length / sampleRate;
}"""
)
private external fun jsScheduleAudioBuffer(
  ctx: JsAny,
  sampleRate: Int,
  startTime: Double,
  length: Int,
  getSample: (Int) -> Float,
): Double

/**
 * Schedules a mono audio buffer for playback on [ctx] using a JS Float32Array directly. Uses
 * `channelData.set(float32Array)` (O(1) bulk copy) instead of a per-sample callback, eliminating
 * ~9,600 JS↔WASM boundary crossings per 200ms chunk.
 */
@JsFun(
  """(ctx, sampleRate, startTime, float32Array) => {
  var buffer = ctx.createBuffer(1, float32Array.length, sampleRate);
  buffer.getChannelData(0).set(float32Array);
  var source = ctx.createBufferSource();
  source.buffer = buffer;
  source.connect(ctx.destination);
  source.start(startTime);
  return startTime + float32Array.length / sampleRate;
}"""
)
private external fun jsScheduleAudioBufferDirect(
  ctx: JsAny,
  sampleRate: Int,
  startTime: Double,
  float32Array: JsAny,
): Double

// ─── Global audio queue access (mirrors SDK-internal jsGetGlobalAudioQueue/jsAudioQueueClear) ───

/**
 * Returns (or creates) the page-global audio queue (`self.__leapAudioQueue`). Must match the same
 * global name used by `jsGetGlobalAudioQueue` in `WasmInferenceEngineBindings.kt`.
 */
@JsFun(
  "() => { if (!self.__leapAudioQueue) self.__leapAudioQueue = []; return self.__leapAudioQueue; }"
)
private external fun jsGetAudioQueue(): JsAny

/** Pops and returns the oldest Float32Array from [queue], or null if empty. */
@JsFun("(queue) => queue.length > 0 ? queue.shift() : null")
private external fun jsAudioQueueShift(queue: JsAny): JsAny?

/** Clears all entries from [queue]. */
@JsFun("(queue) => { queue.length = 0; }") private external fun jsAudioQueueClear(queue: JsAny)

/**
 * Concatenates all Float32Arrays in `__leapAudioAccum` into a WAV blob and triggers a download.
 * Call from the browser console via `__leapSaveWav()` or automatically after generation.
 */
@JsFun(
  """(sampleRate) => {
  var accum = self.__leapAudioAccum;
  if (!accum || accum.length === 0) { console.warn('[leap] no audio to save'); return; }
  var total = 0;
  for (var i = 0; i < accum.length; i++) total += accum[i].length;
  var pcm = new Float32Array(total);
  var off = 0;
  for (var i = 0; i < accum.length; i++) { pcm.set(accum[i], off); off += accum[i].length; }
  var dataSize = total * 2;
  var buf = new ArrayBuffer(44 + dataSize);
  var v = new DataView(buf);
  var p = 0;
  function ws(s) { for (var k = 0; k < s.length; k++) v.setUint8(p++, s.charCodeAt(k)); }
  function wi(n) { v.setInt32(p, n, true); p += 4; }
  function wh(n) { v.setInt16(p, n, true); p += 2; }
  ws('RIFF'); wi(36 + dataSize); ws('WAVE'); ws('fmt ');
  wi(16); wh(1); wh(1); wi(sampleRate); wi(sampleRate * 2); wh(2); wh(16);
  ws('data'); wi(dataSize);
  for (var i = 0; i < total; i++) {
    var s = Math.max(-1, Math.min(1, pcm[i]));
    wh(s < 0 ? Math.max(-32768, Math.round(s * 32768)) : Math.min(32767, Math.round(s * 32767)));
  }
  var blob = new Blob([buf], { type: 'audio/wav' });
  var url = URL.createObjectURL(blob);
  var a = document.createElement('a');
  a.href = url; a.download = 'leap_audio_raw.wav'; a.click();
  URL.revokeObjectURL(url);
}"""
)
internal external fun jsSaveAccumAsWav(sampleRate: Int)

/** Computes the RMS amplitude of a Float32Array. Returns 0 if null or empty. */
@JsFun(
  """(arr) => {
  if (!arr || arr.length === 0) return 0;
  var sum = 0;
  for (var i = 0; i < arr.length; i++) sum += arr[i] * arr[i];
  return Math.sqrt(sum / arr.length);
}"""
)
private external fun jsFloat32ArrayRms(arr: JsAny): Float

/**
 * Normalizes a Float32Array to peak ±1.0 if any sample exceeds 1.0, then soft-clips the edges.
 *
 * The LFM2 ISTFT emits raw floating-point audio whose peak amplitude depends on the model's learned
 * output scale — it is NOT guaranteed to be in [-1, 1] or int16 range. Dynamic peak normalization
 * (scale = 1/maxAbs when maxAbs > 1) is the most robust approach: it ensures the signal is always
 * audible without distortion regardless of the actual output scale, while preserving relative
 * amplitude within a chunk.
 */
@JsFun(
  """(arr) => {
  if (!arr || arr.length === 0) return;
  var maxAbs = 0.0;
  for (var i = 0; i < arr.length; i++) {
    var abs = Math.abs(arr[i]);
    if (abs > maxAbs) maxAbs = abs;
  }
  // Normalize to peak ±1.0 only when out of range. Chunks already within [-1, 1] are unchanged.
  var scale = maxAbs > 1.0 ? (1.0 / maxAbs) : 1.0;
  for (var i = 0; i < arr.length; i++) {
    arr[i] = arr[i] * scale;
  }
}"""
)
private external fun jsFloat32ArrayReduceGain(arr: JsAny)

// ─── WebAudioRecorder ─────────────────────────────────────────────────────────

/**
 * Manages microphone capture via the Web Audio API.
 *
 * Usage: call [start] (suspends until getUserMedia resolves), then [stop] to end capture and
 * retrieve all recorded PCM samples.
 */
class WebAudioRecorder {
  private var ctx: JsAny? = null
  private var handle: JsAny? = null
  private var stream: JsAny? = null
  // Set to true when stop()/cancel() is called before getUserMedia() resolves, so that start()
  // can detect the cancellation and immediately release the acquired stream.
  private var startCancelled = false

  /** RMS amplitude of the most recently processed audio buffer (0–1). Updated in real time. */
  var amplitude: Float = 0f
    private set

  /**
   * Requests microphone access and begins capturing audio at 16 kHz.
   *
   * @throws IllegalStateException if the user denies microphone permission.
   */
  suspend fun start() {
    startCancelled = false
    val s = getUserMedia()
    if (startCancelled) {
      // stop()/cancel() was called while we were waiting for getUserMedia() — release the stream
      // that just resolved so the browser mic indicator goes away immediately.
      jsStopMediaStream(s)
      return
    }
    stream = s
    val audioCtx = jsCreateAudioContext(16000)
    resumeAudioContext(audioCtx) // await before connecting nodes so onaudioprocess fires
    ctx = audioCtx
    handle = jsStartRecordingNode(audioCtx, s, 1024) { rms -> amplitude = rms }
  }

  /**
   * Stops recording and returns all captured samples as a raw 32-bit Float PCM [ByteArray]. Call
   * [start] before calling this.
   */
  fun stop(): ByteArray {
    startCancelled = true
    val h = handle ?: return ByteArray(0)
    // Stop media tracks immediately so the browser releases the mic indicator before anything else.
    stream?.let { jsStopMediaStream(it) }
    stream = null
    val float32Arr = jsStopRecordingNode(h)
    handle = null
    amplitude = 0f

    ctx?.let { jsCloseAudioContext(it) }
    ctx = null

    // Encode WAV entirely in JS: header written with DataView, samples bulk-copied via
    // Float32Array.set() (O(1) JS bulk copy) — avoids the intermediate FloatArray allocation
    // and per-sample Kotlin bit-manipulation loop of the previous encodeWav() path.
    val wavBuffer = jsEncodeWav(float32Arr, 16000)
    val wavLen = jsArrayBufferByteLength(wavBuffer)
    if (wavLen == 0) return ByteArray(0)

    val result = ByteArray(wavLen)
    withScopedMemoryAllocator { allocator ->
      val ptr = allocator.allocate(wavLen)
      jsArrayBufferCopyToWasmPtr(wavBuffer, ptr.address.toInt(), wavLen)
      var p = ptr
      for (i in result.indices) {
        result[i] = p.loadByte()
        p += 1u
      }
    }
    return result
  }

  /** Cancels recording without returning samples (e.g., on user cancel). */
  fun cancel() {
    startCancelled = true
    stream?.let { jsStopMediaStream(it) }
    stream = null
    handle?.let { jsStopRecordingNode(it) }
    handle = null
    amplitude = 0f
    ctx?.let { jsCloseAudioContext(it) }
    ctx = null
  }
}

// ─── WebAudioPlayer ───────────────────────────────────────────────────────────

/**
 * Schedules and plays streaming audio chunks via the Web Audio API.
 *
 * Create one instance per generation session. Call [initialize] once, [enqueue] for each
 * [ai.liquid.leap.message.MessageResponse.AudioSample] chunk, then [stop] when done.
 */
class WebAudioPlayer {
  private var ctx: JsAny? = null
  private var nextTime = 0.0
  private val bufferedSamples = mutableListOf<FloatArray>()
  private val bufferedJsSamples = mutableListOf<JsAny?>()
  private var bufferedFramesCount = 0
  private var isPlaying = false
  private var lastSampleRate = 24000

  /** RMS amplitude of the most recently enqueued chunk (0–1). */
  var amplitude: Float = 0f
    private set

  /**
   * Initializes the playback AudioContext at [sampleRate] Hz and awaits its resume. Must be called
   * from a coroutine triggered by a user gesture to satisfy browser autoplay policy.
   *
   * Also clears the global audio queue of any stale Float32Arrays from a previous session.
   */
  suspend fun initialize(sampleRate: Int = 24000) {
    val audioCtx = jsCreateAudioContext(sampleRate)
    resumeAudioContext(audioCtx) // await — ensures buffers can be scheduled immediately
    ctx = audioCtx
    jsAudioQueueClear(jsGetAudioQueue()) // discard stale Float32Arrays from previous session
    nextTime = 0.0
    bufferedSamples.clear()
    bufferedJsSamples.clear()
    bufferedFramesCount = 0
    isPlaying = false
  }

  /**
   * Enqueues [samples] at [sampleRate] Hz for gapless playback.
   *
   * For each call, a Float32Array with the same samples must have been pushed to the global audio
   * queue by [WorkerBackedModelRunner] immediately before invoking this function. When the player
   * is in the live-scheduling phase, [jsScheduleAudioBufferDirect] is used (O(1) bulk copy via
   * `channelData.set()`) instead of a per-sample callback, eliminating ~9,600 JS↔WASM boundary
   * crossings per 200ms audio chunk.
   *
   * Uses an RTF-adaptive high-water mark for both initial fills and re-buffers. At RTF < 1, audio
   * arrives slower than it plays; to avoid repeated short stutters the buffer target is sized so
   * each play session lasts as long as possible before the next deliberate pause. Re-buffering is
   * triggered proactively when the look-ahead drops below one chunk duration, giving the player
   * enough warning to switch modes before silence occurs.
   */
  fun enqueue(samples: FloatArray, sampleRate: Int) {
    val c = ctx ?: return
    if (samples.isEmpty()) return

    val queue = jsGetAudioQueue()
    val jsSamples = jsAudioQueueShift(queue)

    // Buffer several chunks before starting playback to avoid stuttery gaps.
    // Each chunk is ~1920 samples at 24kHz (~80ms). Buffering 5 chunks (~400ms) provides
    // enough look-ahead for smooth playback even when inference is slower than real-time.
    val highWaterMarkFrames = samples.size * 5
    lastSampleRate = sampleRate

    if (jsSamples != null) {
      jsFloat32ArrayReduceGain(jsSamples)
    } else {
      var maxAbs = 0f
      for (element in samples) {
        val abs = kotlin.math.abs(element)
        if (abs > maxAbs) maxAbs = abs
      }
      val scale = if (maxAbs > 1f) 1f / maxAbs else 1f
      if (scale < 1f) {
        for (i in samples.indices) {
          samples[i] = samples[i] * scale
        }
      }
    }

    // Compute RMS for orb animation — prefer JS Float32Array (samples may be zero-initialized
    // when the SDK defers the WASM copy for performance).
    val rms =
      if (jsSamples != null) jsFloat32ArrayRms(jsSamples)
      else {
        var sumSq = 0f
        for (s in samples) sumSq += s * s
        sqrt(sumSq / samples.size)
      }

    if (!isPlaying) {
      // Buffering phase: accumulate until high-water mark, then flush.
      bufferedSamples.add(samples)
      bufferedJsSamples.add(jsSamples)
      bufferedFramesCount += samples.size

      if (bufferedFramesCount >= highWaterMarkFrames) {
        isPlaying = true
        // Schedule buffered audio to start at the later of: 50ms from now (gives the scheduler
        // a small margin) or the end of any previously scheduled audio (prevents overlap when
        // re-buffer is triggered proactively while old audio is still playing).
        nextTime = maxOf(jsGetContextTime(c) + 0.05, nextTime)
        bufferedSamples.forEachIndexed { i, buf ->
          val js = bufferedJsSamples.getOrNull(i)
          nextTime =
            if (js != null) {
              jsScheduleAudioBufferDirect(c, sampleRate, nextTime, js)
            } else {
              jsScheduleAudioBuffer(c, sampleRate, nextTime, buf.size) { idx -> buf[idx] }
            }
        }
        // Seed amplitude from last buffered chunk so the orb kicks in at playback start.
        bufferedSamples.lastOrNull()?.let { last ->
          var s2 = 0f
          for (s in last) s2 += s * s
          amplitude = (sqrt(s2 / last.size) * 10f).coerceIn(0f, 1f)
        }
        bufferedSamples.clear()
        bufferedJsSamples.clear()
        bufferedFramesCount = 0
      }
    } else {
      // Live-scheduling: check if we've underrun (previous audio finished playing).
      // If so, switch back to buffering mode to accumulate a fresh buffer before resuming.
      val currentTime = jsGetContextTime(c)
      if (nextTime < currentTime) {
        // Underrun — re-buffer to avoid repeated stutters
        isPlaying = false
        bufferedSamples.add(samples)
        bufferedJsSamples.add(jsSamples)
        bufferedFramesCount += samples.size
        amplitude = 0f
      } else {
        amplitude = (rms * 10f).coerceIn(0f, 1f)
        nextTime =
          if (jsSamples != null) {
            jsScheduleAudioBufferDirect(c, sampleRate, nextTime, jsSamples)
          } else {
            jsScheduleAudioBuffer(c, sampleRate, nextTime, samples.size) { i -> samples[i] }
          }
      }
    }
  }

  /**
   * Flushes any buffered-but-not-yet-scheduled audio immediately. Called automatically by [drain].
   * Needed when generation produces fewer samples than the high-water mark — without this, the
   * buffered audio would be silently discarded when [stop] closes the AudioContext.
   */
  private fun flush() {
    val c = ctx ?: return
    if (isPlaying || bufferedSamples.isEmpty()) return
    isPlaying = true
    nextTime = maxOf(jsGetContextTime(c) + 0.05, nextTime)
    bufferedSamples.forEachIndexed { i, buf ->
      val js = bufferedJsSamples.getOrNull(i)
      nextTime =
        if (js != null) {
          jsScheduleAudioBufferDirect(c, lastSampleRate, nextTime, js)
        } else {
          jsScheduleAudioBuffer(c, lastSampleRate, nextTime, buf.size) { idx -> buf[idx] }
        }
    }
    bufferedSamples.lastOrNull()?.let { last ->
      var sum = 0f
      for (s in last) sum += s * s
      amplitude = (sqrt(sum / last.size) * 10f).coerceIn(0f, 1f)
    }
    bufferedSamples.clear()
    bufferedJsSamples.clear()
    bufferedFramesCount = 0
  }

  /**
   * Suspends until all previously enqueued audio buffers have finished playing. Call this before
   * [stop] to avoid cutting off audio that has been scheduled but not yet played.
   */
  suspend fun drain() {
    flush() // schedule any audio that hasn't crossed the high-water mark yet
    val c = ctx ?: return
    val endTime = nextTime
    if (endTime <= 0.0) return
    while (jsGetContextTime(c) < endTime) {
      delay(50)
    }
  }

  /** Stops playback immediately and releases the AudioContext. */
  fun stop() {
    ctx?.let { jsCloseAudioContext(it) }
    ctx = null
    amplitude = 0f
    nextTime = 0.0
    jsAudioQueueClear(jsGetAudioQueue()) // discard any unplayed Float32Arrays
  }
}
