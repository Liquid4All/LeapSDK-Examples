package ai.liquid.leapaudiodemo

/**
 * Test implementation of StringProvider for unit tests.
 *
 * Allows setting test strings for specific resource IDs without requiring Android Context or
 * Robolectric. Falls back to a default template string if a specific string hasn't been set.
 *
 * Example usage:
 * ```
 * val stringProvider = TestStringProvider()
 * stringProvider.setString(R.string.status_ready, "Ready")
 * stringProvider.setString(R.string.error_load_model, "Failed: %s")
 * ```
 */
class TestStringProvider : StringProvider {
  private val strings = mutableMapOf<Int, String>()

  /**
   * Sets a test string for a specific resource ID.
   *
   * @param resId String resource ID
   * @param value Test string value (can include format specifiers)
   */
  fun setString(resId: Int, value: String) {
    strings[resId] = value
  }

  override fun getString(resId: Int, vararg formatArgs: Any): String {
    val template = strings[resId] ?: "string_$resId"
    return if (formatArgs.isEmpty()) {
      template
    } else {
      template.format(*formatArgs)
    }
  }
}
