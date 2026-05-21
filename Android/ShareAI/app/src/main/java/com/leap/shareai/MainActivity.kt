package com.leap.shareai

import android.app.ComponentCaller
import android.content.Intent
import android.os.Bundle
import android.util.Log
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import com.leap.shareai.screens.SummaryAppScreen
import com.leap.shareai.ui.theme.ThemeShareAi

private const val DEFAULT_URL =
  "https://www.liquid.ai/blog/liquid-foundation-models-v2-our-second-series-of-generative-ai-models"

class MainActivity : ComponentActivity() {
  override fun onCreate(savedInstanceState: Bundle?) {
    super.onCreate(savedInstanceState)
    enableEdgeToEdge()
    val sharedUrl =
      intent
        .takeIf { it.action == Intent.ACTION_SEND && it.type == "text/plain" }
        ?.getStringExtra(Intent.EXTRA_TEXT)
    Log.d("MainActivity", "onCreate: ${sharedUrl.toString()}")
    setContent { ThemeShareAi { SummaryAppScreen(linkUrl = sharedUrl ?: DEFAULT_URL) } }
  }

  override fun onNewIntent(intent: Intent, caller: ComponentCaller) {
    super.onNewIntent(intent, caller)
    val sharedUrl =
      intent
        .takeIf { it.action == Intent.ACTION_SEND && it.type == "text/plain" }
        ?.getStringExtra(Intent.EXTRA_TEXT)
    Log.d("MainActivity", "onNewIntent: ${sharedUrl.toString()}")
    setContent { ThemeShareAi { SummaryAppScreen(linkUrl = sharedUrl ?: DEFAULT_URL) } }
  }
}
