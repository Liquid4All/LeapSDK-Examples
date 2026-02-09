package ai.liquid.leapaudiodemo

import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.Image
import androidx.compose.foundation.clickable
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
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Mic
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Send
import androidx.compose.material.icons.filled.Stop
import androidx.compose.material.icons.outlined.Cancel
import androidx.compose.material.icons.outlined.CloudDownload
import androidx.compose.material.icons.outlined.Delete
import androidx.compose.material.icons.outlined.Folder
import androidx.compose.material.icons.outlined.Stop
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilledIconButton
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextFieldDefaults
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import android.content.Context
import android.os.SystemClock
import android.view.accessibility.AccessibilityEvent
import android.view.accessibility.AccessibilityManager
import androidx.core.content.getSystemService

// Extension function for accessibility announcements
private fun AccessibilityManager.announceForAccessibility(context: Context, text: String) {
  if (!isEnabled) return
  val event = AccessibilityEvent.obtain(AccessibilityEvent.TYPE_ANNOUNCEMENT)
  event.text.add(text)
  event.className = context.javaClass.name
  event.packageName = context.packageName
  sendAccessibilityEvent(event)
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AudioDemoScreen(state: AudioDemoState, onEvent: (AudioDemoEvent) -> Unit) {
  val listState = rememberLazyListState()
  val haptic = LocalHapticFeedback.current
  val context = LocalContext.current

  // String resources for accessibility
  val statusLoadingModel = stringResource(R.string.status_loading_model)

  // Track previous recording state to detect transitions
  var previousRecordingState by remember { mutableStateOf<RecordingState>(state.recordingState) }

  // Announce recording state changes for accessibility
  LaunchedEffect(state.recordingState) {
    val accessibilityManager = context.getSystemService<AccessibilityManager>()
    if (accessibilityManager?.isEnabled == true) {
      val announcement = when {
        state.recordingState is RecordingState.Recording ->
          context.getString(R.string.status_recording)
        state.recordingState is RecordingState.Idle &&
          previousRecordingState is RecordingState.Recording ->
          context.getString(R.string.a11y_recording_stopped)
        else -> null
      }
      announcement?.let {
        accessibilityManager.announceForAccessibility(context, it)
      }
    }
    previousRecordingState = state.recordingState
  }

  // Derived values to prevent recomposition when other state changes
  val isInputEnabled =
    remember(state.modelState, state.generationState) {
      state.modelState is ModelState.Ready && state.generationState is GenerationState.Idle
    }
  val shouldShowFAB =
    remember(state.modelState, state.generationState) {
      state.modelState is ModelState.Ready && state.generationState is GenerationState.Idle
    }

  // Double-tap protection for FAB - track last click time using monotonic clock
  // SystemClock.elapsedRealtime() is monotonic (unaffected by time changes from NTP sync)
  // Note: Using regular var instead of mutableStateOf since this doesn't drive UI recomposition
  var lastFabClickTime = remember { 0L }
  // 500ms debounce prevents accidental double-taps while still feeling responsive
  // Tested values: 300ms felt too quick (accidental triggers), 1000ms felt sluggish
  val fabClickDebounceMs = 500L

  // Auto-scroll when a new message is added (using last message ID is more precise than size)
  // This prevents unnecessary scrolling when messages list is replaced with same size
  LaunchedEffect(state.messages.lastOrNull()?.id) {
    if (state.messages.isNotEmpty()) {
      listState.animateScrollToItem(state.messages.size - 1)
    }
  }

  Scaffold(
    topBar = {
      TopAppBar(
        title = {
          Image(
            painter = painterResource(id = R.drawable.leap_logo),
            contentDescription = stringResource(R.string.app_name),
            modifier = Modifier.height(24.dp)
          )
        },
        colors =
          TopAppBarDefaults.topAppBarColors(containerColor = MaterialTheme.colorScheme.surface),
      )
    },
    floatingActionButton = {
      if (shouldShowFAB) {
        FloatingActionButton(
          onClick = {
            val currentTime = SystemClock.elapsedRealtime()
            if (currentTime - lastFabClickTime >= fabClickDebounceMs) {
              lastFabClickTime = currentTime
              // Provide haptic feedback for recording toggle
              haptic.performHapticFeedback(HapticFeedbackType.LongPress)
              if (state.recordingState is RecordingState.Recording) {
                onEvent(AudioDemoEvent.StopRecording)
              } else {
                onEvent(AudioDemoEvent.StartRecording)
              }
            }
          },
          containerColor =
            if (state.recordingState is RecordingState.Recording) {
              MaterialTheme.colorScheme.errorContainer
            } else {
              MaterialTheme.colorScheme.primaryContainer
            },
          contentColor =
            if (state.recordingState is RecordingState.Recording) {
              MaterialTheme.colorScheme.onErrorContainer
            } else {
              MaterialTheme.colorScheme.onPrimaryContainer
            },
        ) {
          Icon(
            imageVector =
              if (state.recordingState is RecordingState.Recording) Icons.Default.Stop
              else Icons.Default.Mic,
            contentDescription =
              if (state.recordingState is RecordingState.Recording) {
                stringResource(R.string.cd_stop_recording)
              } else {
                stringResource(R.string.cd_start_recording)
              },
          )
        }
      }
    },
    bottomBar = {
      InputBar(
        inputText = state.inputText,
        onInputTextChange = { onEvent(AudioDemoEvent.UpdateInputText(it)) },
        onSendClick = { onEvent(AudioDemoEvent.SendTextPrompt) },
        isRecording = state.recordingState is RecordingState.Recording,
        recordingDurationSeconds = state.recordingDurationSeconds,
        isEnabled = isInputEnabled,
      )
    },
  ) { paddingValues ->
    Column(modifier = Modifier.fillMaxSize().padding(paddingValues)) {
      // Status bar - always visible
      Surface(
        modifier = Modifier.fillMaxWidth(),
        color = MaterialTheme.colorScheme.secondaryContainer,
        tonalElevation = 2.dp,
      ) {
        Row(
          modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 12.dp),
          horizontalArrangement = Arrangement.SpaceBetween,
          verticalAlignment = Alignment.CenterVertically,
        ) {
          // Status text
          val statusText = when (state.modelState) {
            is ModelState.NotLoaded -> stringResource(R.string.load_model)
            else -> state.status ?: stringResource(R.string.load_model)
          }

          Text(
            text = statusText,
            modifier = Modifier
              .semantics {
                // Announce status changes to screen readers
                liveRegion = LiveRegionMode.Polite
              },
            style = MaterialTheme.typography.bodyLarge,
            maxLines = 1,
            overflow = androidx.compose.ui.text.style.TextOverflow.MiddleEllipsis,
          )

          // Show progress indicator when loading model
          if (state.modelState is ModelState.Loading) {
            Spacer(modifier = Modifier.width(12.dp))
            if (state.downloadProgress != null) {
              // Determinate progress
              LinearProgressIndicator(
                progress = { state.downloadProgress },
                modifier = Modifier
                  .weight(1f)
                  .semantics {
                    contentDescription = statusLoadingModel
                  },
                color = MaterialTheme.colorScheme.onSecondaryContainer,
              )
            } else {
              // Indeterminate progress
              LinearProgressIndicator(
                modifier = Modifier
                  .weight(1f)
                  .semantics {
                    contentDescription = statusLoadingModel
                  },
                color = MaterialTheme.colorScheme.onSecondaryContainer,
              )
            }
            Spacer(modifier = Modifier.width(12.dp))
            IconButton(
              onClick = { onEvent(AudioDemoEvent.CancelDownload) },
              modifier = Modifier.size(40.dp),
            ) {
              Icon(
                imageVector = Icons.Outlined.Cancel,
                contentDescription = stringResource(R.string.cancel_download),
                tint = MaterialTheme.colorScheme.onSecondaryContainer,
              )
            }
          }

          // Show load icon when no model is loaded
          if (state.modelState is ModelState.NotLoaded) {
            IconButton(
              onClick = { onEvent(AudioDemoEvent.LoadModel) },
              modifier = Modifier.size(40.dp),
            ) {
              Icon(
                imageVector = if (state.isModelCached) {
                  Icons.Outlined.Folder
                } else {
                  Icons.Outlined.CloudDownload
                },
                contentDescription = if (state.isModelCached) {
                  stringResource(R.string.load_model)
                } else {
                  stringResource(R.string.load_model) // Could be "Download and load model"
                },
                tint = MaterialTheme.colorScheme.onSecondaryContainer,
              )
            }
          }

          // Show stop icon when generating
          if (state.generationState !is GenerationState.Idle) {
            IconButton(
              onClick = { onEvent(AudioDemoEvent.StopGeneration) },
              modifier = Modifier.size(40.dp),
            ) {
              Icon(
                imageVector = Icons.Outlined.Stop,
                contentDescription = stringResource(R.string.cd_stop_generation),
                tint = MaterialTheme.colorScheme.onSecondaryContainer,
              )
            }
          }

          // Show delete icon when model is loaded and not generating
          if (state.modelState is ModelState.Ready && state.generationState is GenerationState.Idle) {
            IconButton(
              onClick = { onEvent(AudioDemoEvent.DeleteModel) },
              modifier = Modifier.size(40.dp),
            ) {
              Icon(
                imageVector = Icons.Outlined.Delete,
                contentDescription = stringResource(R.string.delete_model),
                tint = MaterialTheme.colorScheme.onSecondaryContainer,
              )
            }
          }
        }
      }

      // Show error message if model failed to load
      when (state.modelState) {
        is ModelState.Error -> {
          Column(
            modifier = Modifier.fillMaxWidth().padding(16.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(8.dp),
          ) {
            Text(
              text = state.modelState.message,
              color = MaterialTheme.colorScheme.error,
              style = MaterialTheme.typography.bodyMedium,
              modifier = Modifier.padding(horizontal = 16.dp),
            )

            Button(
              onClick = { onEvent(AudioDemoEvent.RetryLoadModel) },
            ) {
              Text(stringResource(R.string.retry))
            }
          }
        }
        else -> {}
      }


      // Messages list
      LazyColumn(
        state = listState,
        modifier = Modifier.weight(1f).fillMaxWidth(),
        contentPadding = PaddingValues(16.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp),
      ) {
        items(items = state.messages, key = { message -> message.id }) { message ->
          MessageBubble(
            message = message,
            isPlayingAudio = state.playingMessageId == message.id,
            onPlayAudio = { audioData, sampleRate ->
              onEvent(AudioDemoEvent.PlayAudio(message.id, audioData, sampleRate))
            },
            onStopAudio = { onEvent(AudioDemoEvent.StopAudioPlayback) },
          )
        }

        // Streaming text
        if (state.generationState !is GenerationState.Idle) {
          item {
            val streamingText =
              when (state.generationState) {
                is GenerationState.GeneratingText -> state.generationState.streamingText
                is GenerationState.GeneratingWithAudio -> state.generationState.streamingText
                else -> ""
              }
            MessageBubble(
              message =
                AudioDemoMessage(
                  role = ai.liquid.leap.message.ChatMessage.Role.ASSISTANT,
                  text = streamingText.ifEmpty { stringResource(R.string.streaming_placeholder) },
                ),
              isStreaming = true,
              isPlayingAudio = false,
              isStreamingAudio = state.generationState is GenerationState.GeneratingWithAudio,
              onPlayAudio = { _, _ -> },
              onStopAudio = { onEvent(AudioDemoEvent.StopGeneration) },
            )
          }
        }
      }
    }
  }
}

@Composable
fun MessageBubble(
  message: AudioDemoMessage,
  isStreaming: Boolean = false,
  isPlayingAudio: Boolean = false,
  isStreamingAudio: Boolean = false,
  onPlayAudio: (FloatArray, Int) -> Unit,
  onStopAudio: () -> Unit = {},
) {
  // String resources for accessibility
  val statusStreamingAudio = stringResource(R.string.status_streaming_audio)
  val statusAwaitingResponse = stringResource(R.string.status_awaiting_response)

  Row(
    modifier = Modifier.fillMaxWidth(),
    horizontalArrangement = if (message.isUser) Arrangement.End else Arrangement.Start,
  ) {
    Column(
      modifier =
        Modifier.widthIn(max = 300.dp)
          .clip(RoundedCornerShape(12.dp))
          .background(
            if (message.isUser) {
              MaterialTheme.colorScheme.primaryContainer
            } else {
              MaterialTheme.colorScheme.secondaryContainer
            }
          )
          .padding(12.dp)
    ) {
      Text(text = message.text, style = MaterialTheme.typography.bodyMedium)

      // Show stop button during any generation (text or audio)
      if (isStreaming) {
        Spacer(modifier = Modifier.height(8.dp))
        // Ensure minimum 48dp touch target for accessibility (WCAG AA)
        Row(
          modifier = Modifier
            .fillMaxWidth()
            .clickable { onStopAudio() }
            .padding(vertical = 8.dp, horizontal = 4.dp), // Increased vertical padding for 48dp
          horizontalArrangement = Arrangement.Center,
          verticalAlignment = Alignment.CenterVertically,
        ) {
          Icon(
            imageVector = Icons.Default.Stop,
            contentDescription =
              if (isStreamingAudio) {
                stringResource(R.string.cd_stop_audio)
              } else {
                stringResource(R.string.cd_stop_generation)
              },
            tint = MaterialTheme.colorScheme.primary,
          )
          Spacer(modifier = Modifier.width(4.dp))
          Text(
            text =
              if (isStreamingAudio) {
                stringResource(R.string.stop_audio)
              } else {
                stringResource(R.string.stop_generation)
              },
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.primary,
          )
        }
      }

      // Show play/stop button for completed messages with valid audio
      // Validate: non-null, non-empty data, and valid sample rate
      if (!isStreaming &&
          message.audioData != null &&
          message.audioData.isNotEmpty() &&
          message.sampleRate > 0) {
        Spacer(modifier = Modifier.height(8.dp))
        // Ensure minimum 48dp touch target for accessibility (WCAG AA)
        Row(
          modifier =
            Modifier.fillMaxWidth()
              .clickable {
                if (isPlayingAudio) {
                  onStopAudio()
                } else {
                  onPlayAudio(message.audioData, message.sampleRate)
                }
              }
              .padding(vertical = 8.dp, horizontal = 4.dp), // Increased vertical padding for 48dp
          horizontalArrangement = Arrangement.Center,
          verticalAlignment = Alignment.CenterVertically,
        ) {
          Icon(
            imageVector = if (isPlayingAudio) Icons.Default.Stop else Icons.Default.PlayArrow,
            contentDescription =
              if (isPlayingAudio) {
                stringResource(R.string.cd_stop_audio)
              } else {
                stringResource(R.string.cd_play_audio)
              },
            tint = MaterialTheme.colorScheme.primary,
          )
          Spacer(modifier = Modifier.width(4.dp))
          Text(
            text =
              if (isPlayingAudio) {
                stringResource(R.string.stop_audio)
              } else {
                stringResource(R.string.play_audio)
              },
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.primary,
          )
        }
      }

      if (isStreaming) {
        Spacer(modifier = Modifier.height(4.dp))
        LinearProgressIndicator(
          modifier = Modifier.fillMaxWidth().semantics {
            contentDescription = if (isStreamingAudio) {
              statusStreamingAudio
            } else {
              statusAwaitingResponse
            }
          }
        )
      }
    }
  }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun InputBar(
  inputText: String,
  onInputTextChange: (String) -> Unit,
  onSendClick: () -> Unit,
  isRecording: Boolean,
  recordingDurationSeconds: Int,
  isEnabled: Boolean,
) {
  // String resources for accessibility
  val cdRecordingInProgress = stringResource(R.string.cd_recording_in_progress)

  // Reasonable character limit for LLM prompts (prevents UI issues and oversized requests)
  val maxInputLength = 5000
  Surface(
    modifier = Modifier.fillMaxWidth(),
    color = MaterialTheme.colorScheme.surface,
    tonalElevation = 3.dp,
    shadowElevation = 8.dp,
  ) {
    if (isRecording) {
      // Recording indicator with pulsing animation for prominence
      val infiniteTransition = rememberInfiniteTransition(label = "recording_pulse")
      val alpha = infiniteTransition.animateFloat(
        initialValue = 1f,
        targetValue = 0.3f,
        animationSpec = infiniteRepeatable(
          animation = tween(800),
          repeatMode = RepeatMode.Reverse
        ),
        label = "alpha_animation"
      )

      Row(
        modifier = Modifier
          .fillMaxWidth()
          .padding(horizontal = 16.dp, vertical = 20.dp)
          .graphicsLayer { this.alpha = alpha.value },
        horizontalArrangement = Arrangement.Center,
        verticalAlignment = Alignment.CenterVertically,
      ) {
        Icon(
          imageVector = Icons.Default.Mic,
          contentDescription = cdRecordingInProgress,
          tint = MaterialTheme.colorScheme.error,
          modifier = Modifier.size(28.dp), // Slightly larger for prominence
        )
        Spacer(modifier = Modifier.width(12.dp))
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
          Text(
            text = stringResource(R.string.recording),
            color = MaterialTheme.colorScheme.error,
            style = MaterialTheme.typography.titleLarge, // Larger text
          )
          // Show countdown timer
          if (recordingDurationSeconds > 0) {
            Spacer(modifier = Modifier.height(4.dp))
            Text(
              text = stringResource(R.string.status_recording_time, recordingDurationSeconds),
              color = MaterialTheme.colorScheme.error.copy(alpha = 0.7f),
              style = MaterialTheme.typography.bodyMedium,
            )
          }
        }
        Spacer(modifier = Modifier.width(12.dp))
        CircularProgressIndicator(
          modifier = Modifier.size(24.dp).semantics {
            contentDescription = cdRecordingInProgress
          },
          color = MaterialTheme.colorScheme.error,
          strokeWidth = 2.dp,
        )
      }
    } else {
      // Normal input UI
      Row(
        modifier =
          Modifier.fillMaxWidth()
            .padding(16.dp),
        verticalAlignment = Alignment.Top,
        horizontalArrangement = Arrangement.spacedBy(8.dp),
      ) {
        OutlinedTextField(
          value = inputText,
          onValueChange = { newText ->
            // Enforce character limit to prevent UI issues and oversized prompts
            if (newText.length <= maxInputLength) {
              onInputTextChange(newText)
            }
          },
          modifier = Modifier.weight(1f),
          placeholder = { Text(stringResource(R.string.type_message)) },
          enabled = isEnabled && !isRecording,
          shape = RoundedCornerShape(24.dp),
          colors =
            TextFieldDefaults.colors(
              focusedContainerColor = MaterialTheme.colorScheme.surfaceVariant,
              unfocusedContainerColor = MaterialTheme.colorScheme.surfaceVariant,
              disabledContainerColor = MaterialTheme.colorScheme.surfaceVariant,
              focusedIndicatorColor = Color.Transparent,
              unfocusedIndicatorColor = Color.Transparent,
              disabledIndicatorColor = Color.Transparent,
            ),
          maxLines = 4,
          supportingText = {
            // Show character counter when approaching limit (90%)
            if (inputText.length > maxInputLength * 0.9) {
              Text(
                text = "${inputText.length}/$maxInputLength",
                style = MaterialTheme.typography.bodySmall,
              )
            }
          },
        )
        FilledIconButton(
          onClick = onSendClick,
          enabled = isEnabled && inputText.isNotBlank() && !isRecording,
          modifier = Modifier.size(48.dp),
        ) {
          Icon(Icons.Default.Send, contentDescription = stringResource(R.string.cd_send_message))
        }
      }
    }
  }
}
