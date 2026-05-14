package com.tldw.app.ui.modelscreen

import android.content.Context
import android.content.Intent
import android.net.ConnectivityManager
import android.net.NetworkCapabilities
import android.provider.Settings
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.AirplanemodeActive
import androidx.compose.material.icons.filled.AutoAwesome
import androidx.compose.material.icons.filled.Bolt
import androidx.compose.material.icons.filled.BugReport
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.CloudDownload
import androidx.compose.material.icons.filled.Download
import androidx.compose.material.icons.filled.Error
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.Memory
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.SignalCellularAlt
import androidx.compose.material.icons.filled.Stop
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material.icons.filled.Wifi
import androidx.compose.material.icons.filled.WifiOff
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Snackbar
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.tldw.app.domain.model.DownloadFailure
import com.tldw.app.domain.model.DownloadProgress
import com.tldw.app.ui.theme.TldwTheme

@Composable
fun ModelDownloadScreen(
  state: ModelDownloadState,
  onEvent: (ModelDownloadEvent) -> Unit,
  onModelReady: () -> Unit,
  modifier: Modifier = Modifier,
) {
  val context = LocalContext.current
  val snackbarHostState = remember { SnackbarHostState() }

  LaunchedEffect(state.isModelReady) { if (state.isModelReady) onModelReady() }

  LaunchedEffect(state.errorMessage) {
    state.errorMessage?.let {
      snackbarHostState.showSnackbar(it)
      onEvent(ModelDownloadEvent.DismissError)
    }
  }

  Scaffold(
    snackbarHost = {
      SnackbarHost(snackbarHostState) { data ->
        Snackbar(action = { TextButton(onClick = { data.dismiss() }) { Text("Dismiss") } }) {
          Text(data.visuals.message)
        }
      }
    },
    containerColor = MaterialTheme.colorScheme.background,
  ) { padding ->
    Box(modifier = modifier.fillMaxSize().padding(padding)) {
      when {
        state.isModelReady -> {
          // Do nothing while wait for navigation
        }
        state.isDownloading && !state.isModelReady ->
          DownloadingContent(state = state, onEvent = onEvent)
        state.downloadFailure is DownloadFailure.NoInternet ->
          NoInternetContent(
            onBack = { onEvent(ModelDownloadEvent.DismissError) },
            onOpenSettings = { context.startActivity(Intent(Settings.ACTION_WIRELESS_SETTINGS)) },
          )
        state.downloadFailure is DownloadFailure.Interrupted ->
          DownloadFailedContent(
            failure = state.downloadFailure,
            onClose = { onEvent(ModelDownloadEvent.DismissError) },
            onResume = { onEvent(ModelDownloadEvent.RetryDownload) },
          )
        else -> InitialContent(onEvent = onEvent)
      }
    }
  }
}

// ── Initial ──────────────────────────────────────────────────────────────────

@Composable
private fun InitialContent(onEvent: (ModelDownloadEvent) -> Unit) {
  val context = LocalContext.current
  var showInfoDialog by remember { mutableStateOf(false) }

  if (showInfoDialog) {
    val version = remember {
      context.packageManager.getPackageInfo(context.packageName, 0).versionName ?: "—"
    }
    AlertDialog(
      onDismissRequest = { showInfoDialog = false },
      title = { Text("TLDW") },
      text = {
        Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
          Text("Version $version", style = MaterialTheme.typography.bodyMedium)
          Spacer(Modifier.height(4.dp))
          Text(
            "TLDW summarizes YouTube videos using an on-device AI model — no data ever leaves your phone.",
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
          )
        }
      },
      confirmButton = { TextButton(onClick = { showInfoDialog = false }) { Text("OK") } },
    )
  }

  Column(modifier = Modifier.fillMaxSize()) {
    TldwAppBar(
      leading = {},
      trailing = { TldwIconButton(Icons.Filled.Info, "About") { showInfoDialog = true } },
    )

    Column(
      modifier = Modifier.weight(1f).fillMaxWidth().padding(horizontal = 20.dp),
      verticalArrangement = Arrangement.Center,
      horizontalAlignment = Alignment.CenterHorizontally,
    ) {
      // Hero icon with accent badge
      Box(contentAlignment = Alignment.BottomEnd) {
        Box(
          modifier =
            Modifier.size(132.dp)
              .clip(RoundedCornerShape(36.dp))
              .background(MaterialTheme.colorScheme.primaryContainer),
          contentAlignment = Alignment.Center,
        ) {
          Icon(
            imageVector = Icons.Filled.AutoAwesome,
            contentDescription = null,
            modifier = Modifier.size(64.dp),
            tint = MaterialTheme.colorScheme.onPrimaryContainer,
          )
        }
        Box(
          modifier =
            Modifier.offset(x = 8.dp, y = 8.dp)
              .size(44.dp)
              .clip(CircleShape)
              .background(MaterialTheme.colorScheme.secondary),
          contentAlignment = Alignment.Center,
        ) {
          Icon(
            imageVector = Icons.Filled.CloudDownload,
            contentDescription = null,
            modifier = Modifier.size(22.dp),
            tint = MaterialTheme.colorScheme.onSecondary,
          )
        }
      }

      Spacer(Modifier.height(28.dp))

      Spacer(Modifier.height(28.dp))

      Text(
        text = "Turn long videos into knowledge bits.",
        fontSize = 28.sp,
        fontWeight = FontWeight.Medium,
        lineHeight = 32.sp,
        letterSpacing = (-0.5).sp,
        color = MaterialTheme.colorScheme.onSurface,
        textAlign = TextAlign.Center,
        modifier = Modifier.widthIn(max = 280.dp),
      )

      Spacer(Modifier.height(14.dp))

      Text(
        text =
          "Download a small language model once. Summarize on-device, forever — no cloud, no key.",
        style = MaterialTheme.typography.bodyMedium,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
        textAlign = TextAlign.Center,
        lineHeight = 20.sp,
        modifier = Modifier.widthIn(max = 280.dp),
      )
    }

    Column(
      modifier = Modifier.padding(horizontal = 20.dp).padding(bottom = 20.dp),
      verticalArrangement = Arrangement.spacedBy(16.dp),
    ) {
      ModelCard()
      DownloadButton(onClick = { onEvent(ModelDownloadEvent.StartDownload) })
    }
  }
}

@Composable
private fun ModelCard() {
  Surface(
    modifier = Modifier.fillMaxWidth(),
    shape = RoundedCornerShape(16.dp),
    color = MaterialTheme.colorScheme.surfaceContainer,
  ) {
    Row(
      modifier = Modifier.padding(16.dp),
      horizontalArrangement = Arrangement.spacedBy(12.dp),
      verticalAlignment = Alignment.CenterVertically,
    ) {
      Box(
        modifier =
          Modifier.size(40.dp)
            .clip(RoundedCornerShape(12.dp))
            .background(MaterialTheme.colorScheme.surfaceContainerHighest),
        contentAlignment = Alignment.Center,
      ) {
        Icon(
          imageVector = Icons.Filled.Memory,
          contentDescription = null,
          modifier = Modifier.size(20.dp),
          tint = MaterialTheme.colorScheme.onSurfaceVariant,
        )
      }
      Column(modifier = Modifier.weight(1f)) {
        Text(
          text = "Summarizer model",
          style = MaterialTheme.typography.labelLarge,
          fontWeight = FontWeight.SemiBold,
          color = MaterialTheme.colorScheme.onSurface,
        )
        Text(
          text = "One-time download · ~1 min on Wi-Fi",
          style = MaterialTheme.typography.labelSmall,
          color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
      }
      Surface(
        shape = RoundedCornerShape(50),
        color = MaterialTheme.colorScheme.surfaceContainerHighest,
      ) {
        Text(
          text = "361 MB",
          modifier = Modifier.padding(horizontal = 10.dp, vertical = 4.dp),
          style = MaterialTheme.typography.labelSmall,
          fontFamily = FontFamily.Monospace,
          color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
      }
    }
  }
}

@Composable
private fun DownloadButton(onClick: () -> Unit) {
  Button(
    onClick = onClick,
    modifier = Modifier.fillMaxWidth().height(56.dp),
    shape = RoundedCornerShape(28.dp),
    contentPadding = PaddingValues(horizontal = 24.dp),
  ) {
    Icon(Icons.Filled.Download, contentDescription = null, modifier = Modifier.size(20.dp))
    Spacer(Modifier.width(10.dp))
    Text(
      text = "Download model",
      fontSize = 16.sp,
      fontWeight = FontWeight.SemiBold,
      letterSpacing = 0.1.sp,
    )
  }
}

// ── Downloading ───────────────────────────────────────────────────────────────

@Composable
private fun DownloadingContent(state: ModelDownloadState, onEvent: (ModelDownloadEvent) -> Unit) {
  val progress = state.downloadProgress
  val percentage = progress?.percentage ?: 0
  val downloadedMb = (progress?.downloadedBytes ?: 0L) / (1024L * 1024L)
  val totalMb = (progress?.totalBytes ?: 0L) / (1024L * 1024L)

  Column(modifier = Modifier.fillMaxSize()) {
    TldwAppBar(
      leading = {
        TldwIconButton(Icons.Filled.Close, "Cancel") { onEvent(ModelDownloadEvent.PauseDownload) }
      }
    )

    Column(
      modifier = Modifier.weight(1f).fillMaxWidth().padding(horizontal = 20.dp),
      verticalArrangement = Arrangement.Center,
      horizontalAlignment = Alignment.CenterHorizontally,
    ) {
      // Circular progress ring with percentage overlay
      Box(modifier = Modifier.size(180.dp), contentAlignment = Alignment.Center) {
        CircularProgressIndicator(
          progress = { percentage / 100f },
          modifier = Modifier.fillMaxSize(),
          strokeWidth = 14.dp,
          color = MaterialTheme.colorScheme.secondary,
          trackColor = MaterialTheme.colorScheme.surfaceContainerHighest,
        )
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
          Row(verticalAlignment = Alignment.Bottom) {
            Text(
              text = "$percentage",
              fontSize = 44.sp,
              fontWeight = FontWeight.SemiBold,
              color = MaterialTheme.colorScheme.onSurface,
              letterSpacing = (-1.5).sp,
              lineHeight = 44.sp,
            )
            Text(
              text = "%",
              fontSize = 22.sp,
              fontWeight = FontWeight.Medium,
              color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
          }
          if (totalMb > 0) {
            Text(
              text = "$downloadedMb / $totalMb MB",
              style = MaterialTheme.typography.labelSmall,
              color = MaterialTheme.colorScheme.onSurfaceVariant,
              fontFamily = FontFamily.Monospace,
              letterSpacing = 0.4.sp,
            )
          }
        }
      }

      Spacer(Modifier.height(32.dp))

      Text(
        text = "Downloading model",
        fontSize = 24.sp,
        fontWeight = FontWeight.Medium,
        color = MaterialTheme.colorScheme.onSurface,
        letterSpacing = (-0.3).sp,
      )

      Spacer(Modifier.height(10.dp))

      Text(
        text = "You can keep using your phone. We'll let you know when it's ready.",
        style = MaterialTheme.typography.bodyMedium,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
        textAlign = TextAlign.Center,
        lineHeight = 20.sp,
        modifier = Modifier.widthIn(max = 260.dp),
      )

      Spacer(Modifier.height(32.dp))

      // Progress chip
      Surface(shape = RoundedCornerShape(50), color = MaterialTheme.colorScheme.surfaceContainer) {
        Row(
          modifier = Modifier.padding(horizontal = 14.dp, vertical = 8.dp),
          verticalAlignment = Alignment.CenterVertically,
          horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
          Icon(
            imageVector = Icons.Filled.Bolt,
            contentDescription = null,
            modifier = Modifier.size(14.dp),
            tint = MaterialTheme.colorScheme.secondary,
          )
          Text(
            text = if (totalMb > 0) "$downloadedMb / $totalMb MB" else "Starting…",
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            fontFamily = FontFamily.Monospace,
            letterSpacing = 0.4.sp,
          )
        }
      }
    }

    Box(modifier = Modifier.padding(horizontal = 20.dp).padding(bottom = 20.dp)) {
      FilledTonalButton(
        onClick = { onEvent(ModelDownloadEvent.PauseDownload) },
        modifier = Modifier.fillMaxWidth().height(48.dp),
        shape = RoundedCornerShape(24.dp),
      ) {
        Icon(Icons.Filled.Stop, contentDescription = null, modifier = Modifier.size(18.dp))
        Spacer(Modifier.width(8.dp))
        Text("Stop download", fontWeight = FontWeight.Medium)
      }
    }
  }
}

// ── Download Failed ───────────────────────────────────────────────────────────

@Composable
private fun DownloadFailedContent(
  failure: DownloadFailure.Interrupted,
  onClose: () -> Unit,
  onResume: () -> Unit,
) {
  val savedMb = failure.savedBytes / (1024L * 1024L)

  Column(modifier = Modifier.fillMaxSize()) {
    TldwAppBar(leading = { TldwIconButton(Icons.Filled.Close, "Close", onClose) })

    Column(
      modifier = Modifier.weight(1f).fillMaxWidth().padding(horizontal = 20.dp),
      verticalArrangement = Arrangement.Center,
      horizontalAlignment = Alignment.CenterHorizontally,
    ) {
      Box(
        modifier =
          Modifier.size(96.dp)
            .clip(RoundedCornerShape(28.dp))
            .background(MaterialTheme.colorScheme.errorContainer),
        contentAlignment = Alignment.Center,
      ) {
        Icon(
          imageVector = Icons.Filled.Error,
          contentDescription = null,
          modifier = Modifier.size(48.dp),
          tint = MaterialTheme.colorScheme.onErrorContainer,
        )
      }

      Spacer(Modifier.height(24.dp))

      Text(
        text = "Download interrupted",
        fontSize = 24.sp,
        fontWeight = FontWeight.Medium,
        color = MaterialTheme.colorScheme.onSurface,
        letterSpacing = (-0.3).sp,
      )

      Spacer(Modifier.height(12.dp))

      Text(
        text =
          if (savedMb > 0)
            "The connection dropped at $savedMb MB. We saved what we have — try again to resume."
          else "The connection dropped. Try again to resume.",
        style = MaterialTheme.typography.bodyMedium,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
        textAlign = TextAlign.Center,
        lineHeight = 21.sp,
        modifier = Modifier.widthIn(max = 290.dp),
      )

      Spacer(Modifier.height(24.dp))

      Surface(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(16.dp),
        color = MaterialTheme.colorScheme.surfaceContainer,
      ) {
        Row(
          modifier = Modifier.padding(16.dp),
          horizontalArrangement = Arrangement.spacedBy(12.dp),
          verticalAlignment = Alignment.CenterVertically,
        ) {
          Icon(
            imageVector = Icons.Filled.Error,
            contentDescription = null,
            modifier = Modifier.size(20.dp),
            tint = MaterialTheme.colorScheme.onSurfaceVariant,
          )
          Column {
            Text(
              text = failure.errorCode,
              style = MaterialTheme.typography.labelSmall,
              fontFamily = FontFamily.Monospace,
              color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Text(
              text = "Server stopped responding",
              style = MaterialTheme.typography.bodySmall,
              color = MaterialTheme.colorScheme.onSurface,
            )
          }
        }
      }
    }

    Row(
      modifier = Modifier.fillMaxWidth().padding(horizontal = 20.dp).padding(bottom = 20.dp),
      horizontalArrangement = Arrangement.spacedBy(12.dp),
      verticalAlignment = Alignment.CenterVertically,
    ) {
      FilledTonalButton(
        onClick = {},
        modifier = Modifier.height(48.dp),
        shape = RoundedCornerShape(24.dp),
      ) {
        Icon(Icons.Filled.BugReport, null, Modifier.size(18.dp))
        Spacer(Modifier.width(8.dp))
        Text("Details")
      }
      Button(
        onClick = onResume,
        modifier = Modifier.weight(1f).height(56.dp),
        shape = RoundedCornerShape(28.dp),
      ) {
        Icon(Icons.Filled.Refresh, null, Modifier.size(20.dp))
        Spacer(Modifier.width(10.dp))
        Text("Resume", fontSize = 16.sp, fontWeight = FontWeight.SemiBold)
      }
    }
  }
}

// ── No Internet ───────────────────────────────────────────────────────────────

@Composable
private fun NoInternetContent(onBack: () -> Unit, onOpenSettings: () -> Unit) {
  val context = LocalContext.current
  val cm = context.getSystemService(Context.CONNECTIVITY_SERVICE) as ConnectivityManager
  val capabilities = remember { cm.getNetworkCapabilities(cm.getActiveNetwork()) }
  val wifiConnected = remember {
    capabilities?.hasTransport(NetworkCapabilities.TRANSPORT_WIFI) == true
  }
  val mobileConnected = remember {
    capabilities?.hasTransport(NetworkCapabilities.TRANSPORT_CELLULAR) == true
  }
  val airplaneModeOn = remember {
    Settings.Global.getInt(context.contentResolver, Settings.Global.AIRPLANE_MODE_ON, 0) != 0
  }

  Column(modifier = Modifier.fillMaxSize()) {
    TldwAppBar(leading = { TldwIconButton(Icons.AutoMirrored.Filled.ArrowBack, "Back", onBack) })

    Column(
      modifier = Modifier.weight(1f).fillMaxWidth().padding(horizontal = 20.dp),
      verticalArrangement = Arrangement.Center,
      horizontalAlignment = Alignment.CenterHorizontally,
    ) {
      Box(
        modifier =
          Modifier.size(110.dp)
            .clip(RoundedCornerShape(32.dp))
            .background(MaterialTheme.colorScheme.surfaceContainer),
        contentAlignment = Alignment.Center,
      ) {
        Icon(
          imageVector = Icons.Filled.WifiOff,
          contentDescription = null,
          modifier = Modifier.size(56.dp),
          tint = MaterialTheme.colorScheme.onSurfaceVariant,
        )
      }

      Spacer(Modifier.height(24.dp))

      Text(
        text = "You're offline",
        fontSize = 26.sp,
        fontWeight = FontWeight.Medium,
        color = MaterialTheme.colorScheme.onSurface,
        letterSpacing = (-0.3).sp,
      )

      Spacer(Modifier.height(12.dp))

      Text(
        text =
          "The model needs Wi-Fi or mobile data to download. Once it's on your device, TLDW works fully offline.",
        style = MaterialTheme.typography.bodyMedium,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
        textAlign = TextAlign.Center,
        lineHeight = 21.sp,
        modifier = Modifier.widthIn(max = 290.dp),
      )

      Spacer(Modifier.height(24.dp))

      Surface(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(16.dp),
        color = MaterialTheme.colorScheme.surfaceContainer,
      ) {
        Column {
          ConnectivityRow(
            icon = Icons.Filled.Wifi,
            label = "Wi-Fi",
            status = if (wifiConnected) "Connected" else "Not connected",
            isActiveIssue = !wifiConnected,
            showDivider = true,
          )
          ConnectivityRow(
            icon = Icons.Filled.SignalCellularAlt,
            label = "Mobile data",
            status = if (mobileConnected) "On" else "Off",
            isActiveIssue = !mobileConnected,
            showDivider = true,
          )
          ConnectivityRow(
            icon = Icons.Filled.AirplanemodeActive,
            label = "Airplane mode",
            status = if (airplaneModeOn) "On" else "Off",
            isActiveIssue = airplaneModeOn,
            showDivider = false,
          )
        }
      }
    }

    Box(modifier = Modifier.padding(horizontal = 20.dp).padding(bottom = 20.dp)) {
      Button(
        onClick = onOpenSettings,
        modifier = Modifier.fillMaxWidth().height(56.dp),
        shape = RoundedCornerShape(28.dp),
        contentPadding = PaddingValues(horizontal = 24.dp),
      ) {
        Icon(Icons.Filled.Settings, null, Modifier.size(20.dp))
        Spacer(Modifier.width(10.dp))
        Text("Open network settings", fontSize = 16.sp, fontWeight = FontWeight.SemiBold)
      }
    }
  }
}

@Composable
private fun ConnectivityRow(
  icon: ImageVector,
  label: String,
  status: String,
  isActiveIssue: Boolean,
  showDivider: Boolean,
) {
  val iconTint =
    if (isActiveIssue) MaterialTheme.colorScheme.error
    else MaterialTheme.colorScheme.onSurfaceVariant
  val trailingTint =
    if (isActiveIssue) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.outline

  Column {
    Row(
      modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 12.dp),
      horizontalArrangement = Arrangement.spacedBy(14.dp),
      verticalAlignment = Alignment.CenterVertically,
    ) {
      Icon(icon, null, Modifier.size(22.dp), tint = iconTint)
      Column(modifier = Modifier.weight(1f)) {
        Text(
          text = label,
          style = MaterialTheme.typography.bodyMedium,
          fontWeight = FontWeight.Medium,
          color = MaterialTheme.colorScheme.onSurface,
        )
        Text(
          text = status,
          style = MaterialTheme.typography.labelSmall,
          color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
      }
      Icon(
        imageVector = if (isActiveIssue) Icons.Filled.Warning else Icons.Filled.Close,
        contentDescription = null,
        modifier = Modifier.size(18.dp),
        tint = trailingTint,
      )
    }
    if (showDivider) {
      HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
    }
  }
}

// ── Shared atoms ──────────────────────────────────────────────────────────────

@Composable
private fun TldwAppBar(
  leading: (@Composable () -> Unit)? = null,
  trailing: (@Composable () -> Unit)? = null,
) {
  Row(
    modifier = Modifier.fillMaxWidth().height(64.dp).padding(horizontal = 4.dp),
    verticalAlignment = Alignment.CenterVertically,
  ) {
    Box(modifier = Modifier.size(48.dp), contentAlignment = Alignment.Center) { leading?.invoke() }
    Spacer(Modifier.weight(1f))
    Box(modifier = Modifier.size(48.dp), contentAlignment = Alignment.Center) { trailing?.invoke() }
  }
}

@Composable
private fun TldwIconButton(
  imageVector: ImageVector,
  contentDescription: String,
  onClick: () -> Unit = {},
) {
  IconButton(onClick = onClick, modifier = Modifier.size(40.dp)) {
    Icon(
      imageVector = imageVector,
      contentDescription = contentDescription,
      tint = MaterialTheme.colorScheme.onSurface,
    )
  }
}

// ── Previews ──────────────────────────────────────────────────────────────────

@Preview(showBackground = true)
@Composable
private fun PreviewInitial() {
  TldwTheme { ModelDownloadScreen(state = ModelDownloadState(), onEvent = {}, onModelReady = {}) }
}

@Preview(showBackground = true)
@Composable
private fun PreviewDownloading() {
  TldwTheme {
    ModelDownloadScreen(
      state =
        ModelDownloadState(
          isDownloading = true,
          downloadProgress = DownloadProgress(504L * 1024 * 1024, 1200L * 1024 * 1024, 42),
        ),
      onEvent = {},
      onModelReady = {},
    )
  }
}

@Preview(showBackground = true)
@Composable
private fun PreviewDownloadFailed() {
  TldwTheme {
    ModelDownloadScreen(
      state =
        ModelDownloadState(
          downloadFailure =
            DownloadFailure.Interrupted(
              errorCode = "NETWORK_TIMEOUT",
              savedBytes = 504L * 1024 * 1024,
            )
        ),
      onEvent = {},
      onModelReady = {},
    )
  }
}

@Preview(showBackground = true)
@Composable
private fun PreviewNoInternet() {
  TldwTheme {
    ModelDownloadScreen(
      state = ModelDownloadState(downloadFailure = DownloadFailure.NoInternet),
      onEvent = {},
      onModelReady = {},
    )
  }
}
