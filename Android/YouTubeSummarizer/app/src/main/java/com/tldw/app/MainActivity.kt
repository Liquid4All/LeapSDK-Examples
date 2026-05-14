package com.tldw.app

import android.content.Intent
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.viewModels
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Surface
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.tldw.app.data.repository.ModelRepositoryImpl
import com.tldw.app.domain.usecase.CheckModelDownloadedUseCase
import com.tldw.app.ui.modelscreen.ModelDownloadScreenRoute
import com.tldw.app.ui.theme.TldwTheme
import com.tldw.app.ui.transcriptscreen.TranscriptScreenRoute

class MainActivity : ComponentActivity() {

  val mainViewModel: MainViewModel by viewModels {
    val repository = ModelRepositoryImpl(applicationContext)
    MainViewModelFactory(CheckModelDownloadedUseCase(repository))
  }

  override fun onCreate(savedInstanceState: Bundle?) {
    super.onCreate(savedInstanceState)
    enableEdgeToEdge()

    val sharedUrl = resolveSharedUrl(intent)

    setContent {
      TldwTheme {
        Surface(modifier = Modifier.fillMaxSize()) {
          val state by mainViewModel.state.collectAsStateWithLifecycle()

          when {
            state.isCheckingStatus -> {
              Box(contentAlignment = Alignment.Center, modifier = Modifier.fillMaxSize()) {
                CircularProgressIndicator()
              }
            }
            state.isModelDownloaded -> {
              TranscriptScreenRoute(
                sharedUrl = sharedUrl,
                onNavigateToModelDownload = { mainViewModel.checkStatus() },
              )
            }
            else -> {
              ModelDownloadScreenRoute(onModelReady = { mainViewModel.checkStatus() })
            }
          }
        }
      }
    }
  }

  private fun resolveSharedUrl(intent: Intent): String? =
    if (intent.action == Intent.ACTION_SEND && intent.type == "text/plain") {
      intent.getStringExtra(Intent.EXTRA_TEXT)
    } else {
      null
    }
}
