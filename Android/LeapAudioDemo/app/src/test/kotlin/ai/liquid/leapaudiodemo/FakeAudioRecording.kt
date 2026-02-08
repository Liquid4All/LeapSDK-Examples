package ai.liquid.leapaudiodemo

/**
 * Fake implementation of AudioRecording for testing.
 *
 * Allows tests to control recording behavior without requiring microphone permissions or actual
 * AudioRecord functionality.
 */
class FakeAudioRecording : AudioRecording {
  var isRecording = false
    private set

  var startCallCount = 0
    private set

  var stopCallCount = 0
    private set

  var cancelCallCount = 0
    private set

  // Configure what start() should return
  var startShouldSucceed = true

  // Configure what stop() should return
  var captureToReturn: AudioRecording.AudioCapture? = null

  override fun start(): Boolean {
    startCallCount++
    if (startShouldSucceed) {
      isRecording = true
    }
    return startShouldSucceed
  }

  override suspend fun stop(): AudioRecording.AudioCapture? {
    stopCallCount++
    isRecording = false
    return captureToReturn
  }

  override suspend fun cancel() {
    cancelCallCount++
    isRecording = false
  }

  fun resetTracking() {
    startCallCount = 0
    stopCallCount = 0
    cancelCallCount = 0
    isRecording = false
    startShouldSucceed = true
    captureToReturn = null
  }
}
