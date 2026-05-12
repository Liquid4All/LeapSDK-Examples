package com.tldw.app.ui.transcriptscreen

import android.content.Intent
import androidx.activity.compose.BackHandler
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
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
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.AutoAwesome
import androidx.compose.material.icons.filled.BugReport
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.ContentCopy
import androidx.compose.material.icons.filled.ContentPaste
import androidx.compose.material.icons.filled.Error
import androidx.compose.material.icons.filled.Link
import androidx.compose.material.icons.filled.LinkOff
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Share
import androidx.compose.material.icons.filled.SubtitlesOff
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
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
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.geometry.RoundRect
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil.compose.AsyncImage
import com.tldw.app.Consts
import com.tldw.app.domain.model.GenerationFailure
import com.tldw.app.domain.model.VideoInfo
import com.tldw.app.domain.model.formatWordCount
import com.tldw.app.ui.component.ShapeProgressIndicator
import com.tldw.app.ui.theme.TldwTheme
import dev.jeziellago.compose.markdowntext.MarkdownText

@Composable
fun TranscriptScreen(
    state: TranscriptState,
    onEvent: (TranscriptEvent) -> Unit,
    onNavigateToModelDownload: () -> Unit = {},
    modifier: Modifier = Modifier,
) {
    val snackbarHostState = remember { SnackbarHostState() }

    BackHandler(enabled = state.phase != TranscriptPhase.Idle) {
        onEvent(TranscriptEvent.Cancel)
    }

    LaunchedEffect(state.isModelNotDownloaded) {
        if (state.isModelNotDownloaded) onNavigateToModelDownload()
    }

    LaunchedEffect(state.errorMessage) {
        state.errorMessage?.let {
            snackbarHostState.showSnackbar(it)
            onEvent(TranscriptEvent.DismissError)
        }
    }

    Scaffold(
        snackbarHost = {
            SnackbarHost(snackbarHostState) { data ->
                Snackbar(
                    action = { TextButton(onClick = { data.dismiss() }) { Text("Dismiss") } }
                ) {
                    Text(data.visuals.message)
                }
            }
        },
        containerColor = MaterialTheme.colorScheme.background,
    ) { padding ->
        Box(modifier = modifier.fillMaxSize().padding(padding)) {
            when (state.phase) {
                TranscriptPhase.Idle -> IdleContent(state = state, onEvent = onEvent)
                TranscriptPhase.Fetching -> FetchingContent(state = state, onEvent = onEvent)
                TranscriptPhase.Generating -> GeneratingContent(state = state, onEvent = onEvent)
                TranscriptPhase.Done -> ResultContent(state = state, onEvent = onEvent)
                TranscriptPhase.FailedNoCaptions ->
                    NoCaptionsContent(state = state, onEvent = onEvent)
                TranscriptPhase.FailedGeneration ->
                    GenerationFailedContent(state = state, onEvent = onEvent)
            }
        }
    }
}

// ── Idle (2A + 2E) ────────────────────────────────────────────────────────────

@Composable
private fun IdleContent(state: TranscriptState, onEvent: (TranscriptEvent) -> Unit) {
    val clipboardManager = LocalClipboardManager.current

    Column(modifier = Modifier.fillMaxSize()) {
        TxAppBar(leading = { TxLogo() })

        Column(
            modifier =
                Modifier.weight(1f)
                    .fillMaxWidth()
                    .padding(horizontal = 20.dp)
                    .verticalScroll(rememberScrollState())
        ) {
            Spacer(Modifier.height(8.dp))

            Text(
                text = "Paste a YouTube link",
                fontSize = 32.sp,
                fontWeight = FontWeight.Medium,
                lineHeight = 36.sp,
                letterSpacing = (-0.8).sp,
                color = MaterialTheme.colorScheme.onSurface,
            )

            if (!state.urlError) {
                Spacer(Modifier.height(10.dp))
                Text(
                    text = "We'll grab the transcript and summarize it on-device in a few seconds.",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    lineHeight = 20.sp,
                )
            }

            Spacer(Modifier.height(24.dp))

            // URL input
            OutlinedTextField(
                value = state.url,
                onValueChange = { onEvent(TranscriptEvent.UrlChanged(it)) },
                modifier = Modifier.fillMaxWidth(),
                placeholder = {
                    Text(
                        text = "youtube.com/watch?v=…",
                        color = MaterialTheme.colorScheme.outline,
                        fontSize = 15.sp,
                    )
                },
                leadingIcon = {
                    Icon(
                        imageVector =
                            if (state.urlError) Icons.Filled.LinkOff else Icons.Filled.Link,
                        contentDescription = null,
                        tint =
                            if (state.urlError) MaterialTheme.colorScheme.error
                            else MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                },
                trailingIcon =
                    if (state.urlError)
                        ({ Icon(Icons.Filled.Error, null, tint = MaterialTheme.colorScheme.error) })
                    else null,
                isError = state.urlError,
                singleLine = true,
                keyboardOptions =
                    KeyboardOptions(keyboardType = KeyboardType.Uri, imeAction = ImeAction.Go),
                keyboardActions = KeyboardActions(onGo = { onEvent(TranscriptEvent.Submit) }),
                shape = RoundedCornerShape(16.dp),
                colors =
                    OutlinedTextFieldDefaults.colors(
                        focusedBorderColor = MaterialTheme.colorScheme.primary,
                        unfocusedBorderColor = MaterialTheme.colorScheme.outlineVariant,
                    ),
            )

            if (state.urlError) {
                Spacer(Modifier.height(8.dp))
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                ) {
                    Icon(
                        Icons.Filled.Error,
                        null,
                        modifier = Modifier.size(14.dp),
                        tint = MaterialTheme.colorScheme.error,
                    )
                    Text(
                        text = "That doesn't look like a YouTube URL.",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.error,
                    )
                }

                Spacer(Modifier.height(20.dp))

                Surface(
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(16.dp),
                    color = MaterialTheme.colorScheme.surfaceContainer,
                ) {
                    Column(modifier = Modifier.padding(16.dp)) {
                        Text(
                            "We accept links like",
                            style = MaterialTheme.typography.labelLarge,
                            fontWeight = FontWeight.SemiBold,
                            color = MaterialTheme.colorScheme.onSurface,
                        )
                        Spacer(Modifier.height(10.dp))
                        listOf("youtube.com/watch?v=…", "youtu.be/…", "m.youtube.com/watch?v=…")
                            .forEach { format ->
                                Text(
                                    text = format,
                                    style = MaterialTheme.typography.labelSmall,
                                    fontFamily = FontFamily.Monospace,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                )
                            }
                    }
                }
            } else {
                Spacer(Modifier.height(10.dp))
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween,
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(6.dp),
                    ) {
                        Icon(
                            Icons.Filled.CheckCircle,
                            null,
                            modifier = Modifier.size(14.dp),
                            tint = MaterialTheme.colorScheme.tertiary,
                        )
                        Text(
                            text = "Model ready · runs on-device",
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                    TextButton(
                        onClick = {
                            val text = clipboardManager.getText()?.text ?: return@TextButton
                            onEvent(TranscriptEvent.UrlChanged(text))
                            onEvent(TranscriptEvent.Submit)
                        }
                    ) {
                        Icon(Icons.Filled.ContentPaste, null, Modifier.size(18.dp))
                        Spacer(Modifier.width(8.dp))
                        Text("Paste")
                    }
                }

                Spacer(Modifier.height(28.dp))

                Text(
                    text = "TRY ONE OF THESE",
                    fontSize = 11.sp,
                    fontWeight = FontWeight.SemiBold,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    letterSpacing = 1.4.sp,
                    modifier = Modifier.padding(horizontal = 4.dp),
                )

                Spacer(Modifier.height(10.dp))

                listOf(
                        Consts.VIDEO_BUILD_GPT,
                        Consts.VIDEO_PRINTING_PRESS,
                        Consts.VIDEO_SLEEP_SUPERPOWER,
                    )
                    .forEach { video ->
                        ExampleVideoRow(
                            video = video,
                            onClick = {
                                onEvent(
                                    TranscriptEvent.UrlChanged(
                                        "https://www.youtube.com/watch?v=${video.videoId}"
                                    )
                                )
                                onEvent(TranscriptEvent.Submit)
                            },
                        )
                        Spacer(Modifier.height(8.dp))
                    }
            }

            Spacer(Modifier.height(16.dp))
        }

        Box(modifier = Modifier.padding(horizontal = 20.dp).padding(bottom = 20.dp)) {
            Button(
                onClick = { onEvent(TranscriptEvent.Submit) },
                enabled = state.url.isNotEmpty() && !state.urlError,
                modifier = Modifier.fillMaxWidth().height(56.dp),
                shape = RoundedCornerShape(28.dp),
                contentPadding = PaddingValues(horizontal = 24.dp),
            ) {
                Icon(Icons.Filled.AutoAwesome, null, Modifier.size(20.dp))
                Spacer(Modifier.width(10.dp))
                Text("Summarize", fontSize = 16.sp, fontWeight = FontWeight.SemiBold)
            }
        }
    }
}

@Composable
private fun ExampleVideoRow(video: VideoInfo, onClick: () -> Unit) {
    Surface(
        onClick = onClick,
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(14.dp),
        color = MaterialTheme.colorScheme.surfaceContainer,
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 14.dp, vertical = 12.dp),
            horizontalArrangement = Arrangement.spacedBy(12.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            AsyncImage(
                model = video.thumbnailUrl(),
                contentDescription = null,
                modifier =
                    Modifier.size(width = 56.dp, height = 40.dp)
                        .clip(RoundedCornerShape(8.dp))
                        .background(MaterialTheme.colorScheme.surfaceContainerHighest),
                contentScale = androidx.compose.ui.layout.ContentScale.Crop,
            )
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = video.title,
                    style = MaterialTheme.typography.bodyMedium,
                    fontWeight = FontWeight.Medium,
                    color = MaterialTheme.colorScheme.onSurface,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                Text(
                    text = "${video.channelName} · ${video.formattedDuration()}",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
    }
}

// ── Fetching (2B) ─────────────────────────────────────────────────────────────

@Composable
private fun FetchingContent(state: TranscriptState, onEvent: (TranscriptEvent) -> Unit) {
    Column(modifier = Modifier.fillMaxSize()) {
        TxAppBar(
            leading = {
                TxIconButton(Icons.AutoMirrored.Filled.ArrowBack, "Back") {
                    onEvent(TranscriptEvent.Cancel)
                }
            },
            trailing = {
                TxIconButton(Icons.Filled.Close, "Cancel") { onEvent(TranscriptEvent.Cancel) }
            },
        )

        Column(modifier = Modifier.weight(1f).fillMaxWidth().padding(horizontal = 20.dp)) {
            // Video preview card (placeholder during fetching)
            VideoPreviewCard(videoInfo = state.videoInfo, compact = false)

            Spacer(Modifier.height(24.dp))

            // 3-step pipeline
            StepperRow(
                steps =
                    listOf(
                        StepState(
                            label = "Found video",
                            sub = "",
                            state =
                                if (state.videoInfo != null) StepStatus.Done else StepStatus.Pending,
                        ),
                        StepState(
                            label = "Fetching transcript",
                            sub = "Pulling captions…",
                            state = StepStatus.Active,
                        ),
                        StepState(
                            label = "Generating TL;DR",
                            sub = "Runs on your device",
                            state = StepStatus.Pending,
                        ),
                    )
            )

            Spacer(Modifier.weight(1f))

            LinearProgressIndicator(
                modifier = Modifier.fillMaxWidth(),
                color = MaterialTheme.colorScheme.secondary,
                trackColor = MaterialTheme.colorScheme.surfaceContainerHighest,
            )

            Spacer(Modifier.height(20.dp))
        }
    }
}

// ── Generating (2C) ───────────────────────────────────────────────────────────

@Composable
private fun GeneratingContent(state: TranscriptState, onEvent: (TranscriptEvent) -> Unit) {
    val infiniteTransition = rememberInfiniteTransition(label = "spin")
    val rotation =
        infiniteTransition.animateFloat(
            initialValue = 0f,
            targetValue = 360f,
            animationSpec =
                infiniteRepeatable(tween(2000, easing = LinearEasing), RepeatMode.Restart),
            label = "ring_rotation",
        )

    Column(modifier = Modifier.fillMaxSize()) {
        TxAppBar(
            leading = {
                TxIconButton(Icons.AutoMirrored.Filled.ArrowBack, "Back") {
                    onEvent(TranscriptEvent.Cancel)
                }
            },
            trailing = {
                TxIconButton(Icons.Filled.Close, "Cancel") { onEvent(TranscriptEvent.Cancel) }
            },
        )

        Column(modifier = Modifier.weight(1f).fillMaxWidth().padding(horizontal = 20.dp)) {
            if (state.videoInfo != null) {
                VideoPreviewCard(videoInfo = state.videoInfo, compact = true)
                Spacer(Modifier.height(24.dp))
            }

            Column(
                modifier = Modifier.fillMaxWidth(),
                horizontalAlignment = Alignment.CenterHorizontally,
            ) {
                // Spinning ring around auto_awesome icon
                Box(contentAlignment = Alignment.Center) {
                    Box(modifier = Modifier.size(96.dp), contentAlignment = Alignment.Center) {
                        Box(
                            modifier =
                                Modifier.size(96.dp)
                                    .clip(RoundedCornerShape(28.dp))
                                    .background(MaterialTheme.colorScheme.primaryContainer),
                            contentAlignment = Alignment.Center,
                        ) {
                            Icon(
                                Icons.Filled.AutoAwesome,
                                null,
                                modifier = Modifier.size(48.dp),
                                tint = MaterialTheme.colorScheme.onPrimaryContainer,
                            )
                        }

                        ShapeProgressIndicator(
                            modifier = Modifier.size(118.dp).rotate(rotation.value),
                            color = MaterialTheme.colorScheme.secondary,
                            trackColor = MaterialTheme.colorScheme.surfaceContainerHighest,
                            strokeWidth = 2.dp,
                            progress = 1f,
                            shapePath = {
                                Path().apply {
                                    addRoundRect(
                                        RoundRect(
                                            rect = Rect(Offset(130f, 130f), 130f),
                                            cornerRadius = CornerRadius(72f, 72f),
                                        )
                                    )
                                }
                            },
                        )
                    }
                }

                Spacer(Modifier.height(18.dp))

                Text(
                    text = "Thinking on-device",
                    fontSize = 22.sp,
                    fontWeight = FontWeight.Medium,
                    color = MaterialTheme.colorScheme.onSurface,
                    letterSpacing = (-0.3).sp,
                )

                Spacer(Modifier.height(10.dp))

                val wordCountText =
                    if (state.wordCountIn > 0)
                        "Reading ${state.wordCountIn.formatWordCount()} words and finding what matters."
                    else "Finding what matters."

                Text(
                    text = "$wordCountText No data leaves your phone.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    textAlign = TextAlign.Center,
                    lineHeight = 18.sp,
                    modifier = Modifier.widthIn(max = 280.dp),
                )

                Spacer(Modifier.height(24.dp))

                // Skeleton lines
                Surface(
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(16.dp),
                    color = MaterialTheme.colorScheme.surfaceContainer,
                ) {
                    Column(
                        modifier = Modifier.padding(16.dp),
                        verticalArrangement = Arrangement.spacedBy(10.dp),
                    ) {
                        listOf(1f, 0.88f, 0.94f).forEach { widthFraction ->
                            Box(
                                modifier =
                                    Modifier.fillMaxWidth(widthFraction)
                                        .height(10.dp)
                                        .clip(RoundedCornerShape(4.dp))
                                        .background(
                                            MaterialTheme.colorScheme.surfaceContainerHighest
                                        )
                            )
                        }
                    }
                }
            }
        }
    }
}

// ── Result (2D) ───────────────────────────────────────────────────────────────

@Composable
private fun ResultContent(state: TranscriptState, onEvent: (TranscriptEvent) -> Unit) {
    val context = LocalContext.current
    val clipboardManager = LocalClipboardManager.current
    val tldr = state.tldrText ?: ""

    Column(modifier = Modifier.fillMaxSize()) {
        TxAppBar(
            leading = {
                TxIconButton(Icons.AutoMirrored.Filled.ArrowBack, "Back") {
                    onEvent(TranscriptEvent.Cancel)
                }
            }
        )

        Column(
            modifier =
                Modifier.weight(1f)
                    .fillMaxWidth()
                    .padding(horizontal = 20.dp)
                    .verticalScroll(rememberScrollState())
        ) {
            // Video chip
            if (state.videoInfo != null) {
                Surface(
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(14.dp),
                    color = MaterialTheme.colorScheme.surfaceContainer,
                ) {
                    Row(
                        modifier = Modifier.padding(horizontal = 12.dp, vertical = 10.dp),
                        horizontalArrangement = Arrangement.spacedBy(12.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        AsyncImage(
                            model = state.videoInfo.thumbnailUrl(),
                            contentDescription = null,
                            modifier =
                                Modifier.size(width = 52.dp, height = 36.dp)
                                    .clip(RoundedCornerShape(6.dp))
                                    .background(MaterialTheme.colorScheme.surfaceContainerHighest),
                            contentScale = androidx.compose.ui.layout.ContentScale.Crop,
                        )
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                text = state.videoInfo.title,
                                style = MaterialTheme.typography.labelLarge,
                                fontWeight = FontWeight.Medium,
                                color = MaterialTheme.colorScheme.onSurface,
                                maxLines = 2,
                                overflow = TextOverflow.Ellipsis,
                            )
                            Text(
                                text =
                                    "${state.videoInfo.channelName} · ${state.videoInfo.formattedDuration()}",
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }
                    }
                }
                Spacer(Modifier.height(18.dp))
            }

            // TL;DR label
            Text(
                text = "TL;DR",
                fontSize = 11.sp,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.secondary,
                letterSpacing = 2.5.sp,
                fontFamily = FontFamily.Monospace,
            )

            Spacer(Modifier.height(10.dp))

            // TL;DR body text
            MarkdownText(
                markdown = tldr,
                style =
                    androidx.compose.ui.text.TextStyle(
                        fontSize = 22.sp,
                        lineHeight = 30.sp,
                        fontWeight = FontWeight.Normal,
                        color = MaterialTheme.colorScheme.onSurface,
                        letterSpacing = (-0.2).sp,
                    ),
            )

            Spacer(Modifier.height(24.dp))

            // Stats row
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                listOf(
                        Pair(state.videoInfo?.formattedDurationShort() ?: "—", "video"),
                        Pair(state.wordCountIn.formatWordCount(), "words in"),
                        Pair("${state.wordCountOut}", "words out"),
                    )
                    .forEach { (value, label) ->
                        Surface(
                            modifier = Modifier.weight(1f),
                            shape = RoundedCornerShape(14.dp),
                            color = MaterialTheme.colorScheme.surfaceContainer,
                        ) {
                            Column(
                                modifier = Modifier.padding(vertical = 12.dp, horizontal = 10.dp),
                                horizontalAlignment = Alignment.CenterHorizontally,
                            ) {
                                Text(
                                    text = value,
                                    fontSize = 20.sp,
                                    fontWeight = FontWeight.SemiBold,
                                    color = MaterialTheme.colorScheme.onSurface,
                                    letterSpacing = (-0.5).sp,
                                )
                                Text(
                                    text = label.uppercase(),
                                    fontSize = 10.sp,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                    letterSpacing = 0.8.sp,
                                )
                            }
                        }
                    }
            }

            Spacer(Modifier.height(18.dp))

            // Action row
            Row(
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                FilledTonalButton(
                    onClick = {
                        clipboardManager.setText(androidx.compose.ui.text.AnnotatedString(tldr))
                    },
                    modifier = Modifier.height(48.dp),
                    shape = RoundedCornerShape(24.dp),
                ) {
                    Icon(Icons.Filled.ContentCopy, null, Modifier.size(18.dp))
                    Spacer(Modifier.width(8.dp))
                    Text("Copy")
                }
                FilledTonalButton(
                    onClick = {
                        val intent =
                            Intent(Intent.ACTION_SEND).apply {
                                type = "text/plain"
                                putExtra(Intent.EXTRA_TEXT, tldr)
                            }
                        context.startActivity(Intent.createChooser(intent, "Share TL;DR"))
                    },
                    modifier = Modifier.height(48.dp),
                    shape = RoundedCornerShape(24.dp),
                ) {
                    Icon(Icons.Filled.Share, null, Modifier.size(18.dp))
                    Spacer(Modifier.width(8.dp))
                    Text("Share")
                }
                Spacer(Modifier.weight(1f))
                IconButton(
                    onClick = { onEvent(TranscriptEvent.RegenerateTldr) },
                    modifier =
                        Modifier.size(48.dp)
                            .clip(CircleShape)
                            .background(MaterialTheme.colorScheme.surfaceContainerHigh),
                ) {
                    Icon(
                        Icons.Filled.Refresh,
                        "Regenerate",
                        tint = MaterialTheme.colorScheme.onSurface,
                    )
                }
            }

            Spacer(Modifier.height(22.dp))

            // Footer
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(6.dp),
            ) {
                Icon(
                    Icons.Filled.CheckCircle,
                    null,
                    modifier = Modifier.size(12.dp),
                    tint = MaterialTheme.colorScheme.tertiary,
                )
                Text(
                    text = "Generated locally · ${"%.1f".format(state.generationTimeSeconds)}s",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    fontFamily = FontFamily.Monospace,
                )
            }

            Spacer(Modifier.height(20.dp))
        }
    }
}

// ── No Captions (2F) ──────────────────────────────────────────────────────────

@Composable
private fun NoCaptionsContent(state: TranscriptState, onEvent: (TranscriptEvent) -> Unit) {
    val context = LocalContext.current

    Column(modifier = Modifier.fillMaxSize()) {
        TxAppBar(
            leading = {
                TxIconButton(Icons.AutoMirrored.Filled.ArrowBack, "Back") {
                    onEvent(TranscriptEvent.Cancel)
                }
            }
        )

        Column(modifier = Modifier.weight(1f).fillMaxWidth().padding(horizontal = 20.dp)) {
            if (state.videoInfo != null) {
                VideoPreviewCard(videoInfo = state.videoInfo, compact = false)
                Spacer(Modifier.height(28.dp))
            }

            Column(
                modifier = Modifier.fillMaxWidth(),
                horizontalAlignment = Alignment.CenterHorizontally,
            ) {
                Box(
                    modifier =
                        Modifier.size(88.dp)
                            .clip(RoundedCornerShape(26.dp))
                            .background(MaterialTheme.colorScheme.errorContainer),
                    contentAlignment = Alignment.Center,
                ) {
                    Icon(
                        Icons.Filled.SubtitlesOff,
                        null,
                        modifier = Modifier.size(44.dp),
                        tint = MaterialTheme.colorScheme.onErrorContainer,
                    )
                }

                Spacer(Modifier.height(18.dp))

                Text(
                    text = "No captions on this video",
                    fontSize = 22.sp,
                    fontWeight = FontWeight.Medium,
                    color = MaterialTheme.colorScheme.onSurface,
                    letterSpacing = (-0.3).sp,
                )

                Spacer(Modifier.height(10.dp))

                Text(
                    text =
                        "The uploader didn't add captions and YouTube hasn't auto-generated any. Without text, TLDW has nothing to read.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    textAlign = TextAlign.Center,
                    lineHeight = 18.sp,
                    modifier = Modifier.widthIn(max = 290.dp),
                )
            }
        }

        Row(
            modifier = Modifier.fillMaxWidth().padding(horizontal = 20.dp).padding(bottom = 20.dp),
            horizontalArrangement = Arrangement.spacedBy(12.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            val videoUrl = state.videoInfo?.videoId?.let { "https://youtube.com/watch?v=$it" }
            FilledTonalButton(
                onClick = {
                    if (videoUrl != null) {
                        context.startActivity(
                            Intent(Intent.ACTION_VIEW, android.net.Uri.parse(videoUrl))
                        )
                    }
                },
                modifier = Modifier.weight(1f).height(48.dp),
                shape = RoundedCornerShape(24.dp),
            ) {
                Text("Open in YouTube")
            }
            Button(
                onClick = { onEvent(TranscriptEvent.Cancel) },
                modifier = Modifier.weight(1f).height(56.dp),
                shape = RoundedCornerShape(28.dp),
            ) {
                Icon(Icons.Filled.Link, null, Modifier.size(20.dp))
                Spacer(Modifier.width(8.dp))
                Text("Try another", fontWeight = FontWeight.SemiBold)
            }
        }
    }
}

// ── Generation Failed (2G) ────────────────────────────────────────────────────

@Composable
private fun GenerationFailedContent(state: TranscriptState, onEvent: (TranscriptEvent) -> Unit) {
    val failure = state.generationFailure ?: GenerationFailure()

    Column(modifier = Modifier.fillMaxSize()) {
        TxAppBar(
            leading = {
                TxIconButton(Icons.AutoMirrored.Filled.ArrowBack, "Back") {
                    onEvent(TranscriptEvent.Cancel)
                }
            },
            trailing = {
                TxIconButton(Icons.Filled.Close, "Close") { onEvent(TranscriptEvent.Cancel) }
            },
        )

        Column(modifier = Modifier.weight(1f).fillMaxWidth().padding(horizontal = 20.dp)) {
            if (state.videoInfo != null) {
                VideoPreviewCard(videoInfo = state.videoInfo, compact = true)
                Spacer(Modifier.height(24.dp))
            }

            Column(
                modifier = Modifier.fillMaxWidth(),
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
                        Icons.Filled.Warning,
                        null,
                        modifier = Modifier.size(48.dp),
                        tint = MaterialTheme.colorScheme.onErrorContainer,
                    )
                }

                Spacer(Modifier.height(18.dp))

                Text(
                    text = "The model gave up",
                    fontSize = 22.sp,
                    fontWeight = FontWeight.Medium,
                    color = MaterialTheme.colorScheme.onSurface,
                    letterSpacing = (-0.3).sp,
                )

                Spacer(Modifier.height(10.dp))

                Text(
                    text =
                        "Generation stalled. This sometimes happens with very long transcripts. Re-running usually works.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    textAlign = TextAlign.Center,
                    lineHeight = 18.sp,
                    modifier = Modifier.widthIn(max = 290.dp),
                )

                Spacer(Modifier.height(18.dp))

                Surface(
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(16.dp),
                    color = MaterialTheme.colorScheme.surfaceContainer,
                ) {
                    Column(
                        modifier = Modifier.padding(16.dp),
                        verticalArrangement = Arrangement.spacedBy(10.dp),
                    ) {
                        ErrorDetailRow("error", failure.errorCode, isValueError = true)
                        if (failure.elapsedSeconds > 0f) {
                            ErrorDetailRow("elapsed", "${"%.1f".format(failure.elapsedSeconds)}s")
                        }
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
                Text("Report")
            }
            Button(
                onClick = { onEvent(TranscriptEvent.RetryGeneration) },
                modifier = Modifier.weight(1f).height(56.dp),
                shape = RoundedCornerShape(28.dp),
            ) {
                Icon(Icons.Filled.Refresh, null, Modifier.size(20.dp))
                Spacer(Modifier.width(10.dp))
                Text("Try again", fontSize = 16.sp, fontWeight = FontWeight.SemiBold)
            }
        }
    }
}

// ── Shared atoms ──────────────────────────────────────────────────────────────

@Composable
private fun VideoPreviewCard(videoInfo: VideoInfo?, compact: Boolean) {
    Surface(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(16.dp),
        color = MaterialTheme.colorScheme.surfaceContainer,
    ) {
        Column {
            Box(modifier = Modifier.fillMaxWidth().height(160.dp)) {
                if (videoInfo != null) {
                    AsyncImage(
                        model = videoInfo.thumbnailUrl(),
                        contentDescription = null,
                        modifier =
                            Modifier.fillMaxWidth()
                                .matchParentSize()
                                .background(MaterialTheme.colorScheme.surfaceContainerHighest),
                        contentScale = androidx.compose.ui.layout.ContentScale.Crop,
                    )
                } else {
                    Box(
                        modifier =
                            Modifier.matchParentSize()
                                .background(MaterialTheme.colorScheme.surfaceContainerHighest),
                        contentAlignment = Alignment.Center,
                    ) {
                        if (!compact) {
                            Box(
                                modifier =
                                    Modifier.size(56.dp)
                                        .clip(CircleShape)
                                        .background(
                                            MaterialTheme.colorScheme.inverseSurface.copy(
                                                alpha = 0.5f
                                            )
                                        ),
                                contentAlignment = Alignment.Center,
                            ) {
                                Icon(
                                    Icons.Filled.PlayArrow,
                                    null,
                                    Modifier.size(28.dp),
                                    tint = MaterialTheme.colorScheme.inverseOnSurface,
                                )
                            }
                        }
                    }
                }
                if (videoInfo != null && videoInfo.durationSeconds > 0) {
                    Box(
                        modifier =
                            Modifier.align(Alignment.BottomEnd)
                                .padding(8.dp)
                                .background(
                                    MaterialTheme.colorScheme.inverseSurface.copy(alpha = 0.7f),
                                    RoundedCornerShape(4.dp),
                                )
                                .padding(horizontal = 6.dp, vertical = 2.dp)
                    ) {
                        Text(
                            text = videoInfo.formattedDuration(),
                            style = MaterialTheme.typography.labelSmall,
                            fontFamily = FontFamily.Monospace,
                            color = MaterialTheme.colorScheme.inverseOnSurface,
                        )
                    }
                }
            }
            if (videoInfo != null) {
                Column(modifier = Modifier.padding(if (compact) 12.dp else 14.dp)) {
                    Text(
                        text = videoInfo.title,
                        style =
                            if (compact) MaterialTheme.typography.labelLarge
                            else MaterialTheme.typography.bodyMedium,
                        fontWeight = FontWeight.Medium,
                        color = MaterialTheme.colorScheme.onSurface,
                        maxLines = if (compact) 1 else 2,
                        overflow = TextOverflow.Ellipsis,
                    )
                    if (
                        !compact && (videoInfo.channelName.isNotEmpty() || videoInfo.viewCount > 0)
                    ) {
                        Text(
                            text =
                                buildString {
                                    if (videoInfo.channelName.isNotEmpty())
                                        append(videoInfo.channelName)
                                    if (videoInfo.viewCount > 0)
                                        append(" · ${videoInfo.formattedViewCount()} views")
                                },
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                }
            }
        }
    }
}

private enum class StepStatus {
    Pending,
    Active,
    Done,
}

private data class StepState(val label: String, val sub: String, val state: StepStatus)

@Composable
private fun StepperRow(steps: List<StepState>) {
    Column(verticalArrangement = Arrangement.spacedBy(16.dp)) {
        steps.forEach { step ->
            Row(
                horizontalArrangement = Arrangement.spacedBy(14.dp),
                verticalAlignment = Alignment.Top,
            ) {
                Box(modifier = Modifier.size(28.dp), contentAlignment = Alignment.Center) {
                    when (step.state) {
                        StepStatus.Done ->
                            Box(
                                modifier =
                                    Modifier.size(24.dp)
                                        .clip(CircleShape)
                                        .background(MaterialTheme.colorScheme.tertiary),
                                contentAlignment = Alignment.Center,
                            ) {
                                Icon(
                                    Icons.Filled.Check,
                                    null,
                                    Modifier.size(16.dp),
                                    tint = MaterialTheme.colorScheme.onTertiary,
                                )
                            }
                        StepStatus.Active ->
                            CircularProgressIndicator(
                                modifier = Modifier.size(24.dp),
                                strokeWidth = 3.dp,
                                color = MaterialTheme.colorScheme.secondary,
                                trackColor = MaterialTheme.colorScheme.surfaceContainerHighest,
                            )
                        StepStatus.Pending ->
                            Box(
                                modifier =
                                    Modifier.size(24.dp)
                                        .clip(CircleShape)
                                        .background(
                                            MaterialTheme.colorScheme.surfaceContainerHighest
                                        )
                            )
                    }
                }
                Column {
                    Text(
                        text = step.label,
                        style = MaterialTheme.typography.bodyMedium,
                        fontWeight =
                            if (step.state == StepStatus.Active) FontWeight.SemiBold
                            else FontWeight.Medium,
                        color =
                          if (step.state == StepStatus.Pending) MaterialTheme.colorScheme.outline
                          else MaterialTheme.colorScheme.onSurface,
                    )
                    if (step.sub.isNotEmpty()) {
                        Text(
                            text = step.sub,
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun ErrorDetailRow(key: String, value: String, isValueError: Boolean = false) {
    Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
        Text(
            text = key,
            style = MaterialTheme.typography.labelSmall,
            fontFamily = FontFamily.Monospace,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Text(
            text = value,
            style = MaterialTheme.typography.labelSmall,
            fontFamily = FontFamily.Monospace,
            color =
                if (isValueError) MaterialTheme.colorScheme.error
                else MaterialTheme.colorScheme.onSurface,
        )
    }
}

@Composable
private fun TxLogo() {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(10.dp),
        modifier = Modifier.padding(horizontal = 4.dp),
    ) {
        Box(
            modifier =
                Modifier.size(26.dp)
                    .clip(RoundedCornerShape(8.dp))
                    .background(MaterialTheme.colorScheme.secondary),
            contentAlignment = Alignment.Center,
        ) {
            Box(
                modifier =
                  Modifier.size(11.dp)
                    .clip(RoundedCornerShape(2.dp))
                    .background(MaterialTheme.colorScheme.onSecondary)
            )
        }
        Text(
            text = "TLDW",
            fontSize = 16.sp,
            fontWeight = FontWeight.Bold,
            letterSpacing = (-0.5).sp,
            color = MaterialTheme.colorScheme.onSurface,
        )
    }
}

@Composable
private fun TxAppBar(
    leading: (@Composable () -> Unit)? = null,
    trailing: (@Composable () -> Unit)? = null,
) {
    Row(
        modifier = Modifier.fillMaxWidth().height(64.dp).padding(horizontal = 4.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(modifier = Modifier, contentAlignment = Alignment.Center) { leading?.invoke() }
        Spacer(Modifier.weight(1f))
        Box(modifier = Modifier.size(48.dp), contentAlignment = Alignment.Center) {
            trailing?.invoke()
        }
    }
}

@Composable
private fun TxIconButton(
    imageVector: ImageVector,
    contentDescription: String,
    onClick: () -> Unit = {},
) {
    IconButton(onClick = onClick, modifier = Modifier.size(40.dp)) {
        Icon(imageVector, contentDescription, tint = MaterialTheme.colorScheme.onSurface)
    }
}

// ── Previews ──────────────────────────────────────────────────────────────────

private val previewVideoInfo =
    VideoInfo(
        videoId = "dQw4w9WgXcQ",
        title = "How to read a paper, fast",
        channelName = "Andrej Karpathy",
        durationSeconds = 2832L,
        viewCount = 412_000L,
    )

@Preview(showBackground = true)
@Composable
private fun PreviewIdle() {
    TldwTheme { TranscriptScreen(state = TranscriptState(), onEvent = {}) }
}

@Preview(showBackground = true)
@Composable
private fun PreviewIdleUrlError() {
    TldwTheme {
        TranscriptScreen(
            state = TranscriptState(url = "https://example.com/blog/post", urlError = true),
            onEvent = {},
        )
    }
}

@Preview(showBackground = true)
@Composable
private fun PreviewFetching() {
    TldwTheme {
        TranscriptScreen(state = TranscriptState(phase = TranscriptPhase.Fetching), onEvent = {})
    }
}

@Preview(showBackground = true)
@Composable
private fun PreviewGenerating() {
    TldwTheme {
        TranscriptScreen(
            state =
                TranscriptState(
                    phase = TranscriptPhase.Generating,
                    videoInfo = previewVideoInfo,
                    wordCountIn = 8420,
                ),
            onEvent = {},
        )
    }
}

@Preview(showBackground = true)
@Composable
private fun PreviewResult() {
    TldwTheme {
        TranscriptScreen(
            state =
                TranscriptState(
                    phase = TranscriptPhase.Done,
                    videoInfo = previewVideoInfo,
                    wordCountIn = 8420,
                    wordCountOut = 52,
                    tldrText =
                        "Skim every paper in three passes — title and abstract first, then figures and headings, finally the math only if it pays off. Most papers deserve five minutes, not five hours.",
                    generationTimeSeconds = 3.2f,
                ),
            onEvent = {},
        )
    }
}

@Preview(showBackground = true)
@Composable
private fun PreviewNoCaptions() {
    TldwTheme {
        TranscriptScreen(
            state =
                TranscriptState(
                    phase = TranscriptPhase.FailedNoCaptions,
                    videoInfo = previewVideoInfo,
                ),
            onEvent = {},
        )
    }
}

@Preview(showBackground = true)
@Composable
private fun PreviewGenerationFailed() {
    TldwTheme {
        TranscriptScreen(
            state =
                TranscriptState(
                    phase = TranscriptPhase.FailedGeneration,
                    videoInfo = previewVideoInfo,
                    generationFailure =
                        GenerationFailure(errorCode = "INFERENCE_TIMEOUT", elapsedSeconds = 42.1f),
                ),
            onEvent = {},
        )
    }
}
