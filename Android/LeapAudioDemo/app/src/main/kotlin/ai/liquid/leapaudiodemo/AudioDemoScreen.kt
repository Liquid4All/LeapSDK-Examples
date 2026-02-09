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
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilledIconButton
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.Icon
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

  // Announce recording state changes for accessibility
  LaunchedEffect(state.recordingState) {
    val accessibilityManager = context.getSystemService<AccessibilityManager>()
    if (accessibilityManager?.isEnabled == true) {
      val announcement = when (state.recordingState) {
        is RecordingState.Recording -> context.getString(R.string.status_recording)
        is RecordingState.Idle -> if (state.recordingState == RecordingState.Idle) {
          // Only announce when transitioning from recording to idle
          context.getString(R.string.a11y_recording_stopped)
        } else null
        else -> null
      }
      announcement?.let {
        accessibilityManager.announceForAccessibility(context, it)
      }
    }
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
      // Status bar with live region for accessibility announcements
      if (state.status != null) {
        Surface(
          modifier = Modifier.fillMaxWidth(),
          color = MaterialTheme.colorScheme.secondaryContainer,
          tonalElevation = 2.dp,
        ) {
          Text(
            text = state.status ?: "",
            modifier = Modifier
              .padding(8.dp)
              .semantics {
                // Announce status changes to screen readers
                liveRegion = LiveRegionMode.Polite
              },
            style = MaterialTheme.typography.bodySmall,
          )
        }
      }

      // Load model button or error
      when (state.modelState) {
        is ModelState.NotLoaded,
        is ModelState.Error -> {
          Column(
            modifier = Modifier.fillMaxWidth().padding(16.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(8.dp),
          ) {
            if (state.modelState is ModelState.Error) {
              Text(
                text = state.modelState.message,
                color = MaterialTheme.colorScheme.error,
                style = MaterialTheme.typography.bodyMedium,
                modifier = Modifier.padding(horizontal = 16.dp),
              )
            }

            Button(
              onClick = {
                if (state.modelState is ModelState.Error && state.modelState.canRetry) {
                  onEvent(AudioDemoEvent.RetryLoadModel)
                } else {
                  onEvent(AudioDemoEvent.LoadModel)
                }
              },
              enabled = state.modelState !is ModelState.Loading,
            ) {
              Text(
                if (state.modelState is ModelState.Error && state.modelState.canRetry) {
                  stringResource(R.string.retry)
                } else {
                  stringResource(R.string.load_model)
                }
              )
            }
          }
        }
        is ModelState.Loading -> {
          // Show cancel button during download
          Column(
            modifier = Modifier.fillMaxWidth().padding(16.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(8.dp),
          ) {
            Button(
              onClick = { onEvent(AudioDemoEvent.CancelDownload) }
            ) {
              Text(stringResource(R.string.cancel_download))
            }
          }
        }
        is ModelState.Ready -> {
          // Show delete button when model is loaded
          Column(
            modifier = Modifier.fillMaxWidth().padding(16.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(8.dp),
          ) {
            Button(
              onClick = { onEvent(AudioDemoEvent.DeleteModel) },
              colors = androidx.compose.material3.ButtonDefaults.buttonColors(
                containerColor = MaterialTheme.colorScheme.errorContainer,
                contentColor = MaterialTheme.colorScheme.onErrorContainer,
              ),
            ) {
              Text(stringResource(R.string.delete_model))
            }
          }
        }
        else -> {}
      }

      if (state.modelState is ModelState.Loading) {
        Box(
          modifier = Modifier.fillMaxWidth().padding(16.dp),
          contentAlignment = Alignment.Center,
        ) {
          CircularProgressIndicator(
            modifier = Modifier.semantics {
              contentDescription = state.status ?: statusLoadingModel
            }
          )
        }
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
            .padding(horizontal = 12.dp, vertical = 8.dp)
            .padding(bottom = 8.dp),
        verticalAlignment = Alignment.Bottom,
        horizontalArrangement = Arrangement.spacedBy(8.dp),
      ) {
        OutlinedTextField(
          value = inputText,
          onValueChange = onInputTextChange,
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
