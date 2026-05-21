package ai.liquid.koogleapsdk.agents.common

import ai.liquid.koogleapsdk.App
import java.io.File

/**
 * Resolves the per-install directory where Koog/Leap downloads and caches model bundles.
 *
 * Uses [App.context.filesDir] — the app's internal storage — which is writable on Android.
 * Previously this was `/tmp/models`, which only happens to be writable on a JVM host; on Android
 * `/tmp` does not exist, so any agent that loads a model would fail at runtime.
 */
val modelsPath: String
  get() = File(App.context.filesDir, "models").absolutePath
