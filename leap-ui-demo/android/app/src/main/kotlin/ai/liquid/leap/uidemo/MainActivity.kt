package ai.liquid.leap.uidemo

import ai.liquid.leap.ui.StatusType
import ai.liquid.leap.ui.VoiceAssistantWidget
import android.Manifest
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.viewmodel.compose.viewModel

/**
 * Full-screen voice assistant demo.
 *
 * Delegates all audio recording, model inference, and state management to [VoiceAssistantViewModel]
 * using an MVI pattern. Press-and-hold to record audio, release to send to the model, and stream
 * the audio response back. The orb animation is driven by real microphone / playback amplitude.
 *
 * Requires `RECORD_AUDIO` and `INTERNET` permissions (declared in `AndroidManifest.xml`).
 */
class MainActivity : ComponentActivity() {

  companion object {
    private val COLOR_LOADING = Color(0xFFAAAAAA)
    private val COLOR_READY = Color(0xFF66FF66)
    private val COLOR_ERROR = Color(0xFFFF6666)
    private val COLOR_STATS = Color(0xFF8C8C8C)
    private const val STATS_FONT_SIZE_SP = 11
    private const val STATUS_FONT_SIZE_SP = 12
    private const val STATUS_BOTTOM_PADDING_DP = 16
  }

  override fun onCreate(savedInstanceState: Bundle?) {
    super.onCreate(savedInstanceState)
    enableEdgeToEdge()
    setContent {
      MaterialTheme(
        colorScheme = darkColorScheme(background = Color.Black, surface = Color.Black)
      ) {
        val vm = viewModel<VoiceAssistantViewModel>()
        val state by vm.state.collectAsState()

        val permissionLauncher =
          rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) {}
        LaunchedEffect(Unit) { permissionLauncher.launch(Manifest.permission.RECORD_AUDIO) }

        val statusColor =
          when (state.statusType) {
            StatusType.READY -> COLOR_READY
            StatusType.ERROR -> COLOR_ERROR
            StatusType.LOADING -> COLOR_LOADING
          }

        Box(modifier = Modifier.fillMaxSize().background(Color.Black)) {
          VoiceAssistantWidget(
            state = state.widgetState,
            onIntent = vm::processIntent,
            modifier = Modifier.fillMaxSize().background(Color.Black),
          )

          Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(4.dp),
            modifier =
              Modifier.align(Alignment.BottomCenter).padding(bottom = STATUS_BOTTOM_PADDING_DP.dp),
          ) {
            state.statsText?.let { stats ->
              Text(text = stats, color = COLOR_STATS, fontSize = STATS_FONT_SIZE_SP.sp)
            }
            if (state.statusText.isNotEmpty()) {
              Text(text = state.statusText, color = statusColor, fontSize = STATUS_FONT_SIZE_SP.sp)
            }
          }
        }
      }
    }
  }
}
