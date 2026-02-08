package ai.liquid.leapaudiodemo

import android.content.Context

/**
 * Android implementation of StringProvider that uses Context to access string resources.
 *
 * This is the production implementation used by the app at runtime.
 *
 * @param context Application or Activity context
 */
class AndroidStringProvider(private val context: Context) : StringProvider {
  override fun getString(resId: Int, vararg formatArgs: Any): String {
    return context.getString(resId, *formatArgs)
  }
}
