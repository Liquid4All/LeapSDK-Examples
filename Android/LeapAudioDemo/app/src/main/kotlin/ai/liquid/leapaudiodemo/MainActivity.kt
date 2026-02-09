package ai.liquid.leapaudiodemo

import android.Manifest
import android.os.Build
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.activity.viewModels
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Surface
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp

class MainActivity : ComponentActivity() {
  private val viewModel: AudioDemoViewModel by viewModels()

  override fun onCreate(savedInstanceState: Bundle?) {
    super.onCreate(savedInstanceState)

    setContent { AudioDemoApp(viewModel = viewModel) }
  }
}

@Composable
fun AudioDemoApp(viewModel: AudioDemoViewModel) {
  val state by viewModel.state.collectAsState()
  val snackbarHostState = remember { SnackbarHostState() }
  var permissionDeniedMessage by remember { mutableStateOf<String?>(null) }
  var permissionsGranted by remember { mutableStateOf(false) }

  // Get string resources
  val permissionMicrophoneRequired = stringResource(R.string.permission_microphone_required)
  val permissionSomeDenied = stringResource(R.string.permission_some_denied)

  // Permission launcher
  val permissionLauncher =
    rememberLauncherForActivityResult(
      contract = ActivityResultContracts.RequestMultiplePermissions()
    ) { permissions ->
      val deniedPermissions = permissions.filterValues { !it }.keys
      if (deniedPermissions.isNotEmpty()) {
        permissionDeniedMessage =
          when {
            Manifest.permission.RECORD_AUDIO in deniedPermissions -> permissionMicrophoneRequired
            else -> permissionSomeDenied
          }
        permissionsGranted = false
        // Cancel model loading if permissions denied (without retrying)
        if (state.modelState is ModelState.Loading) {
          viewModel.onEvent(AudioDemoEvent.CancelModelLoad)
        }
      } else {
        permissionsGranted = true
      }
    }

  // Collect side effects
  LaunchedEffect(Unit) {
    viewModel.sideEffect.collect { sideEffect ->
      when (sideEffect) {
        is AudioDemoSideEffect.ShowSnackbar -> {
          snackbarHostState.showSnackbar(
            message = sideEffect.message,
            duration = androidx.compose.material3.SnackbarDuration.Short,
          )
        }
      }
    }
  }

  // Show snackbar for permission denial
  LaunchedEffect(permissionDeniedMessage) {
    permissionDeniedMessage?.let { message ->
      snackbarHostState.showSnackbar(
        message = message,
        duration = androidx.compose.material3.SnackbarDuration.Long,
      )
      permissionDeniedMessage = null
    }
  }

  // Request permissions when user tries to load the model
  // This provides better UX than requesting immediately on app launch
  LaunchedEffect(state.modelState, permissionsGranted) {
    if (state.modelState is ModelState.Loading && !permissionsGranted) {
      val permissionsToRequest = buildList {
        add(Manifest.permission.RECORD_AUDIO)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
          add(Manifest.permission.POST_NOTIFICATIONS)
        }
      }
      permissionLauncher.launch(permissionsToRequest.toTypedArray())
    }
  }

  MaterialTheme(colorScheme = darkColorScheme()) {
    Surface(modifier = Modifier.fillMaxSize(), color = MaterialTheme.colorScheme.background) {
      // Snackbar container box
      androidx.compose.foundation.layout.Box(modifier = Modifier.fillMaxSize()) {
        AudioDemoScreen(state = state, onEvent = viewModel::onEvent)
        // Snackbar host at the bottom
        SnackbarHost(
          hostState = snackbarHostState,
          modifier = Modifier.align(androidx.compose.ui.Alignment.BottomCenter).padding(16.dp),
        )
      }
    }
  }
}
