package ai.liquid.leapaudiodemo

import android.content.Context
import android.util.Log

/**
 * Android implementation of StringProvider that uses Context to access string resources.
 *
 * This is the production implementation used by the app at runtime.
 *
 * @param context Application or Activity context
 */
class AndroidStringProvider(private val context: Context) : StringProvider {
  override fun getString(resId: Int, vararg formatArgs: Any): String {
    return try {
      context.getString(resId, *formatArgs)
    } catch (e: android.content.res.Resources.NotFoundException) {
      Log.e(TAG, "String resource not found: $resId", e)
      "String resource not found"
    } catch (e: Exception) {
      Log.e(TAG, "Error formatting string: $resId", e)
      "Error loading text"
    }
  }

  companion object {
    private const val TAG = "AndroidStringProvider"
  }
}
