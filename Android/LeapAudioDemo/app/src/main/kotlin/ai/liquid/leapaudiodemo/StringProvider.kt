package ai.liquid.leapaudiodemo

/**
 * Interface for providing string resources.
 *
 * Abstracts string resource access to enable testing without requiring Android Context or
 * Robolectric. Implementations can provide real strings from Android resources or test strings for
 * unit tests.
 */
interface StringProvider {
  /**
   * Gets a string resource with optional format arguments.
   *
   * @param resId String resource ID (e.g., R.string.example)
   * @param formatArgs Optional format arguments for string formatting
   * @return The formatted string
   */
  fun getString(resId: Int, vararg formatArgs: Any): String
}
