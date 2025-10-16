import AVFoundation
import Foundation

final class AudioRecorder {
  private let engine = AVAudioEngine()
  private let queue = DispatchQueue(label: "ai.leap.audio.recorder")
  private var samples: [Float] = []
  private var capturedSampleRate: Double = 16_000
  private(set) var isRecording = false

  func start() throws {
    guard !isRecording else { return }

    let session = AVAudioSession.sharedInstance()
    try session.setCategory(
      .playAndRecord,
      mode: .default,
      options: [.defaultToSpeaker, .allowBluetooth]
    )
    try session.setActive(true, options: [])

    samples.removeAll(keepingCapacity: true)

    let input = engine.inputNode
    let format = input.outputFormat(forBus: 0)
    capturedSampleRate = format.sampleRate

    input.removeTap(onBus: 0)
    input.installTap(onBus: 0, bufferSize: 1024, format: format) { [weak self] buffer, _ in
      guard
        let self,
        let channelData = buffer.floatChannelData
      else { return }

      let channel = channelData[0]
      let frameCount = Int(buffer.frameLength)

      self.queue.async {
        self.samples.append(contentsOf: UnsafeBufferPointer(start: channel, count: frameCount))
      }
    }

    engine.prepare()
    try engine.start()
    isRecording = true
  }

  func stop() {
    guard isRecording else { return }
    engine.inputNode.removeTap(onBus: 0)
    engine.stop()
    isRecording = false
  }

  func capture() -> (samples: [Float], sampleRate: Int)? {
    var result: [Float] = []
    queue.sync {
      result = samples
      samples.removeAll(keepingCapacity: true)
    }
    guard !result.isEmpty else { return nil }
    return (result, Int(capturedSampleRate))
  }

  func cancel() {
    stop()
    queue.sync {
      samples.removeAll(keepingCapacity: true)
    }
  }
}
