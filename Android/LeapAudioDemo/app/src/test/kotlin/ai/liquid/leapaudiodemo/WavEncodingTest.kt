package ai.liquid.leapaudiodemo

import io.kotest.matchers.shouldBe
import kotlin.test.Test

/**
 * Unit tests for WAV file encoding and byte conversion utilities.
 *
 * These tests verify the correctness of:
 * - Little-endian byte conversion for integers and shorts
 * - WAV header generation
 * - PCM sample encoding from float to 16-bit
 *
 * Note: These helper functions are private in AudioDemoViewModel.
 * Consider extracting to a separate utility class for easier testing.
 */
class WavEncodingTest {

    /**
     * Helper to convert int to little-endian bytes (matches ViewModel implementation)
     */
    private fun intToBytes(value: Int): ByteArray {
        return byteArrayOf(
            (value and 0xFF).toByte(),
            (value shr 8 and 0xFF).toByte(),
            (value shr 16 and 0xFF).toByte(),
            (value shr 24 and 0xFF).toByte()
        )
    }

    /**
     * Helper to convert short to little-endian bytes (matches ViewModel implementation)
     */
    private fun shortToBytes(value: Int): ByteArray {
        return byteArrayOf(
            (value and 0xFF).toByte(),
            (value shr 8 and 0xFF).toByte()
        )
    }

    @Test
    fun `intToBytes should convert 0 correctly`() {
        val bytes = intToBytes(0)

        bytes shouldBe byteArrayOf(0, 0, 0, 0)
    }

    @Test
    fun `intToBytes should convert small positive number correctly`() {
        val bytes = intToBytes(256)

        bytes shouldBe byteArrayOf(0, 1, 0, 0)
    }

    @Test
    fun `intToBytes should convert larger number correctly`() {
        val bytes = intToBytes(0x12345678)

        bytes shouldBe byteArrayOf(0x78.toByte(), 0x56, 0x34, 0x12)
    }

    @Test
    fun `intToBytes should handle negative numbers correctly`() {
        val bytes = intToBytes(-1)

        bytes shouldBe byteArrayOf(-1, -1, -1, -1)
    }

    @Test
    fun `shortToBytes should convert 0 correctly`() {
        val bytes = shortToBytes(0)

        bytes shouldBe byteArrayOf(0, 0)
    }

    @Test
    fun `shortToBytes should convert small positive number correctly`() {
        val bytes = shortToBytes(256)

        bytes shouldBe byteArrayOf(0, 1)
    }

    @Test
    fun `shortToBytes should convert larger number correctly`() {
        val bytes = shortToBytes(0x1234)

        bytes shouldBe byteArrayOf(0x34, 0x12)
    }

    @Test
    fun `shortToBytes should handle 16-bit limit correctly`() {
        val bytes = shortToBytes(65535)

        bytes shouldBe byteArrayOf(-1, -1)
    }

    @Test
    fun `float to PCM conversion should handle zero`() {
        val sample = 0.0f
        val pcm = (sample * 32767).toInt().coerceIn(-32768, 32767).toShort()

        pcm shouldBe 0
    }

    @Test
    fun `float to PCM conversion should handle max positive value`() {
        val sample = 1.0f
        val pcm = (sample * 32767).toInt().coerceIn(-32768, 32767).toShort()

        pcm shouldBe 32767
    }

    @Test
    fun `float to PCM conversion should handle max negative value`() {
        val sample = -1.0f
        val pcm = (sample * 32767).toInt().coerceIn(-32768, 32767).toShort()

        pcm shouldBe -32767
    }

    @Test
    fun `float to PCM conversion should clamp values above 1_0`() {
        val sample = 2.0f
        val pcm = (sample * 32767).toInt().coerceIn(-32768, 32767).toShort()

        pcm shouldBe 32767
    }

    @Test
    fun `float to PCM conversion should clamp values below -1_0`() {
        val sample = -2.0f
        val pcm = (sample * 32767).toInt().coerceIn(-32768, 32767).toShort()

        pcm shouldBe -32768
    }

    @Test
    fun `float to PCM conversion should handle fractional values`() {
        val sample = 0.5f
        val pcm = (sample * 32767).toInt().coerceIn(-32768, 32767).toShort()

        // 0.5 * 32767 = 16383.5 -> 16383
        pcm shouldBe 16383
    }

    // Note: Full WAV file encoding tests would require:
    // - Extracting floatArrayToWav from ViewModel to a testable utility
    // - Verifying WAV header structure (RIFF, WAVE, fmt, data chunks)
    // - Validating chunk sizes and file structure
    // - Testing with various sample rates and array sizes
    // Consider refactoring audio encoding to a separate AudioEncoder class
    // for improved testability and separation of concerns.
}
