package ai.liquid.leapaudiodemo

import java.io.ByteArrayOutputStream
import java.nio.ByteBuffer
import java.nio.ByteOrder

/**
 * Utility for encoding audio data to WAV format.
 *
 * Converts float audio samples (range [-1.0, 1.0]) to WAV file format with 16-bit PCM encoding.
 */
object AudioEncoder {
  /**
   * Converts float audio samples to WAV format ByteArray.
   *
   * Creates a complete WAV file with RIFF header, format chunk, and data chunk. Samples are
   * converted from float [-1.0, 1.0] to 16-bit PCM.
   *
   * @param samples Audio samples in range [-1.0, 1.0]
   * @param sampleRate Sample rate in Hz (e.g., 16000, 24000, 48000)
   * @return WAV file as ByteArray (ready to write to file or send over network)
   */
  fun floatArrayToWav(samples: FloatArray, sampleRate: Int): ByteArray {
    val byteRate = sampleRate * 2 // 16-bit = 2 bytes per sample
    val dataSize = samples.size * 2

    val output = ByteArrayOutputStream()

    // WAV header
    output.write("RIFF".toByteArray())
    output.write(intToBytes(36 + dataSize)) // File size - 8
    output.write("WAVE".toByteArray())
    output.write("fmt ".toByteArray())
    output.write(intToBytes(16)) // PCM header size
    output.write(shortToBytes(1)) // PCM format
    output.write(shortToBytes(1)) // Mono
    output.write(intToBytes(sampleRate))
    output.write(intToBytes(byteRate))
    output.write(shortToBytes(2)) // Block align
    output.write(shortToBytes(16)) // Bits per sample
    output.write("data".toByteArray())
    output.write(intToBytes(dataSize))

    // Convert float samples to 16-bit PCM
    val buffer = ByteBuffer.allocate(samples.size * 2).order(ByteOrder.LITTLE_ENDIAN)
    for (sample in samples) {
      val pcm = (sample * 32767).toInt().coerceIn(-32768, 32767).toShort()
      buffer.putShort(pcm)
    }
    output.write(buffer.array())

    return output.toByteArray()
  }

  /**
   * Converts a 32-bit integer to little-endian byte array.
   *
   * @param value Integer value to convert
   * @return 4-byte array in little-endian order
   */
  private fun intToBytes(value: Int): ByteArray {
    return byteArrayOf(
      (value and 0xFF).toByte(),
      (value shr 8 and 0xFF).toByte(),
      (value shr 16 and 0xFF).toByte(),
      (value shr 24 and 0xFF).toByte(),
    )
  }

  /**
   * Converts a 16-bit integer to little-endian byte array.
   *
   * @param value Integer value to convert (will be truncated to 16 bits)
   * @return 2-byte array in little-endian order
   */
  private fun shortToBytes(value: Int): ByteArray {
    return byteArrayOf((value and 0xFF).toByte(), (value shr 8 and 0xFF).toByte())
  }
}
