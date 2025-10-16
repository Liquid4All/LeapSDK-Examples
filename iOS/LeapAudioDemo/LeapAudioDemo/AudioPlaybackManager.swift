import AVFoundation
import Foundation

final class AudioPlaybackManager {
  private let engine = AVAudioEngine()
  private let player = AVAudioPlayerNode()
  private let queue = DispatchQueue(label: "ai.leap.audio.playback")
  private var format: AVAudioFormat?
  private var sessionConfigured = false

  init() {
    engine.attach(player)
    engine.connect(player, to: engine.mainMixerNode, format: nil)
  }

  func prepareSession() {
    queue.async {
      self.configureSessionIfNeeded()
    }
  }

  func enqueue(samples: [Float], sampleRate: Int) {
    guard !samples.isEmpty else { return }
    queue.async {
      self.configureSessionIfNeeded(sampleRate: Double(sampleRate))
      guard
        let audioFormat = self.ensureFormat(sampleRate: Double(sampleRate)),
        let buffer = AVAudioPCMBuffer(
          pcmFormat: audioFormat,
          frameCapacity: AVAudioFrameCount(samples.count))
      else {
        return
      }

      buffer.frameLength = buffer.frameCapacity
      if let channelData = buffer.floatChannelData {
        samples.withUnsafeBufferPointer { pointer in
          channelData[0].assign(from: pointer.baseAddress!, count: pointer.count)
        }
      }

      self.player.scheduleBuffer(buffer, completionHandler: nil)
      if !self.player.isPlaying {
        self.player.play()
      }
    }
  }

  func play(wavData: Data) {
    queue.async {
      let tmpURL = URL(fileURLWithPath: NSTemporaryDirectory())
        .appendingPathComponent(UUID().uuidString)
        .appendingPathExtension("wav")
      var shouldRemoveImmediately = true
      do {
        try wavData.write(to: tmpURL)
        let file = try AVAudioFile(forReading: tmpURL)
        self.configureSessionIfNeeded(sampleRate: file.fileFormat.sampleRate)
        self.ensureFormat(sampleRate: file.fileFormat.sampleRate)
        self.player.scheduleFile(
          file, at: nil,
          completionHandler: {
            try? FileManager.default.removeItem(at: tmpURL)
          })
        if !self.player.isPlaying {
          self.player.play()
        }
        shouldRemoveImmediately = false
      } catch {
        try? FileManager.default.removeItem(at: tmpURL)
        print("AudioPlaybackManager error: \(error)")
      }

      if shouldRemoveImmediately {
        try? FileManager.default.removeItem(at: tmpURL)
      }
    }
  }

  func reset() {
    queue.async {
      self.player.stop()
      self.player.reset()
      self.format = nil
    }
  }

  private func ensureFormat(sampleRate: Double) -> AVAudioFormat? {
    if let format, format.sampleRate == sampleRate {
      return format
    }

    guard let newFormat = AVAudioFormat(standardFormatWithSampleRate: sampleRate, channels: 1)
    else {
      return nil
    }

    engine.disconnectNodeOutput(player)
    engine.connect(player, to: engine.mainMixerNode, format: newFormat)
    if !engine.isRunning {
      do {
        try engine.start()
      } catch {
        print("AudioPlaybackManager engine start error: \(error)")
      }
    }
    format = newFormat
    return newFormat
  }

  private func configureSessionIfNeeded(sampleRate: Double? = nil) {
    if !sessionConfigured {
      do {
        let session = AVAudioSession.sharedInstance()
        try session.setCategory(
          .playAndRecord,
          mode: .default,
          options: [.defaultToSpeaker, .allowBluetooth]
        )
        try session.setActive(true, options: [])
      } catch {
        print("AudioPlaybackManager session error: \(error)")
      }
      sessionConfigured = true
    }

    if let sampleRate {
      _ = ensureFormat(sampleRate: sampleRate)
    } else if let format {
      _ = ensureFormat(sampleRate: format.sampleRate)
    }

    if !engine.isRunning {
      do {
        try engine.start()
      } catch {
        print("AudioPlaybackManager engine restart error: \(error)")
      }
    }
  }
}
