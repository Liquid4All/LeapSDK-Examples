package ai.liquid.leapaudiodemo

import io.kotest.matchers.shouldBe
import kotlin.test.Test

/**
 * Unit tests for AudioEncoder WAV encoding.
 *
 * Tests verify the correctness of:
 * - WAV header generation (RIFF, WAVE, fmt, data chunks)
 * - PCM sample encoding from float to 16-bit
 * - Byte order (little-endian)
 * - File structure and chunk sizes
 */
class AudioEncoderTest {

  @Test
  fun `should encode empty samples correctly`() {
    val wav = AudioEncoder.floatArrayToWav(floatArrayOf(), 16000)

    // WAV header is 44 bytes
    wav.size shouldBe 44

    // Check RIFF header
    val riff = String(wav.sliceArray(0..3))
    riff shouldBe "RIFF"

    // Check WAVE identifier
    val wave = String(wav.sliceArray(8..11))
    wave shouldBe "WAVE"
  }

  @Test
  fun `should encode single sample correctly`() {
    val sample = floatArrayOf(0.5f)
    val wav = AudioEncoder.floatArrayToWav(sample, 16000)

    // Header (44 bytes) + 1 sample (2 bytes) = 46 bytes
    wav.size shouldBe 46

    // Extract PCM data (last 2 bytes)
    val pcmBytes = wav.sliceArray(44..45)
    val pcm = (pcmBytes[0].toInt() and 0xFF) or ((pcmBytes[1].toInt() and 0xFF) shl 8)

    // 0.5 * 32767 = 16383.5 -> 16383
    pcm.toShort() shouldBe 16383
  }

  @Test
  fun `should encode multiple samples correctly`() {
    val samples = floatArrayOf(0.0f, 0.5f, -0.5f, 1.0f, -1.0f)
    val wav = AudioEncoder.floatArrayToWav(samples, 24000)

    // Header (44) + 5 samples (10 bytes) = 54 bytes
    wav.size shouldBe 54
  }

  @Test
  fun `should have correct RIFF chunk size`() {
    val samples = floatArrayOf(0.1f, 0.2f, 0.3f)
    val wav = AudioEncoder.floatArrayToWav(samples, 16000)

    // RIFF chunk size at bytes 4-7 (little-endian)
    val chunkSize = readIntLE(wav, 4)

    // Should be file size - 8 (RIFF header)
    // File = 44 header + 6 data = 50 bytes
    // Chunk size = 50 - 8 = 42
    chunkSize shouldBe 42
  }

  @Test
  fun `should have correct fmt chunk for mono 16-bit PCM`() {
    val wav = AudioEncoder.floatArrayToWav(floatArrayOf(0.0f), 16000)

    // fmt chunk starts at byte 12
    val fmt = String(wav.sliceArray(12..15))
    fmt shouldBe "fmt "

    // Format chunk size (byte 16-19)
    val fmtSize = readIntLE(wav, 16)
    fmtSize shouldBe 16

    // Audio format (byte 20-21): 1 = PCM
    val audioFormat = readShortLE(wav, 20)
    audioFormat shouldBe 1

    // Number of channels (byte 22-23): 1 = mono
    val channels = readShortLE(wav, 22)
    channels shouldBe 1

    // Sample rate (byte 24-27)
    val sampleRate = readIntLE(wav, 24)
    sampleRate shouldBe 16000

    // Byte rate (byte 28-31): sample rate * channels * bytes per sample
    val byteRate = readIntLE(wav, 28)
    byteRate shouldBe 32000 // 16000 * 1 * 2

    // Block align (byte 32-33): channels * bytes per sample
    val blockAlign = readShortLE(wav, 32)
    blockAlign shouldBe 2 // 1 * 2

    // Bits per sample (byte 34-35)
    val bitsPerSample = readShortLE(wav, 34)
    bitsPerSample shouldBe 16
  }

  @Test
  fun `should have correct data chunk`() {
    val samples = floatArrayOf(0.1f, 0.2f)
    val wav = AudioEncoder.floatArrayToWav(samples, 24000)

    // data chunk identifier starts at byte 36
    val data = String(wav.sliceArray(36..39))
    data shouldBe "data"

    // data chunk size (byte 40-43)
    val dataSize = readIntLE(wav, 40)
    dataSize shouldBe 4 // 2 samples * 2 bytes
  }

  @Test
  fun `should clamp values above 1_0`() {
    val samples = floatArrayOf(2.0f)
    val wav = AudioEncoder.floatArrayToWav(samples, 16000)

    val pcmBytes = wav.sliceArray(44..45)
    val pcm = (pcmBytes[0].toInt() and 0xFF) or ((pcmBytes[1].toInt() and 0xFF) shl 8)

    // Should be clamped to 32767
    pcm.toShort() shouldBe 32767
  }

  @Test
  fun `should clamp values below -1_0`() {
    val samples = floatArrayOf(-2.0f)
    val wav = AudioEncoder.floatArrayToWav(samples, 16000)

    val pcmBytes = wav.sliceArray(44..45)
    val pcm = (pcmBytes[0].toInt() and 0xFF) or ((pcmBytes[1].toInt() and 0xFF) shl 8)

    // Should be clamped to -32768
    pcm.toShort() shouldBe -32768
  }

  @Test
  fun `should encode zero correctly`() {
    val samples = floatArrayOf(0.0f)
    val wav = AudioEncoder.floatArrayToWav(samples, 16000)

    val pcmBytes = wav.sliceArray(44..45)
    val pcm = (pcmBytes[0].toInt() and 0xFF) or ((pcmBytes[1].toInt() and 0xFF) shl 8)

    pcm.toShort() shouldBe 0
  }

  @Test
  fun `should encode max positive value correctly`() {
    val samples = floatArrayOf(1.0f)
    val wav = AudioEncoder.floatArrayToWav(samples, 16000)

    val pcmBytes = wav.sliceArray(44..45)
    val pcm = (pcmBytes[0].toInt() and 0xFF) or ((pcmBytes[1].toInt() and 0xFF) shl 8)

    pcm.toShort() shouldBe 32767
  }

  @Test
  fun `should encode max negative value correctly`() {
    val samples = floatArrayOf(-1.0f)
    val wav = AudioEncoder.floatArrayToWav(samples, 16000)

    val pcmBytes = wav.sliceArray(44..45)
    val pcm = (pcmBytes[0].toInt() and 0xFF) or ((pcmBytes[1].toInt() and 0xFF) shl 8)

    // -1.0 * 32767 = -32767 (not -32768)
    pcm.toShort() shouldBe -32767
  }

  @Test
  fun `should support different sample rates`() {
    val samples = floatArrayOf(0.5f)

    val wav16k = AudioEncoder.floatArrayToWav(samples, 16000)
    val wav24k = AudioEncoder.floatArrayToWav(samples, 24000)
    val wav48k = AudioEncoder.floatArrayToWav(samples, 48000)

    readIntLE(wav16k, 24) shouldBe 16000
    readIntLE(wav24k, 24) shouldBe 24000
    readIntLE(wav48k, 24) shouldBe 48000
  }

  // Helper functions to read little-endian integers from byte array

  private fun readIntLE(bytes: ByteArray, offset: Int): Int {
    return (bytes[offset].toInt() and 0xFF) or
      ((bytes[offset + 1].toInt() and 0xFF) shl 8) or
      ((bytes[offset + 2].toInt() and 0xFF) shl 16) or
      ((bytes[offset + 3].toInt() and 0xFF) shl 24)
  }

  private fun readShortLE(bytes: ByteArray, offset: Int): Int {
    return (bytes[offset].toInt() and 0xFF) or ((bytes[offset + 1].toInt() and 0xFF) shl 8)
  }
}
