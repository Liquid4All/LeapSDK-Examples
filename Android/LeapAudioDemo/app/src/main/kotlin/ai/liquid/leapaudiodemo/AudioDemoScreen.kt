package ai.liquid.leapaudiodemo

import androidx.compose.foundation.background
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
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.launch

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AudioDemoScreen(
    state: AudioDemoState,
    onEvent: (AudioDemoEvent) -> Unit
) {
    val listState = rememberLazyListState()
    val coroutineScope = rememberCoroutineScope()
    val audioRecorder = remember { AudioRecorder() }

    // Derived values to prevent recomposition when other state changes
    val isInputEnabled = remember(state.isModelLoaded, state.isGenerating) {
        state.isModelLoaded && !state.isGenerating
    }
    val shouldShowFAB = remember(state.isModelLoaded, state.isGenerating) {
        state.isModelLoaded && !state.isGenerating
    }

    LaunchedEffect(state.messages.size) {
        if (state.messages.isNotEmpty()) {
            listState.animateScrollToItem(state.messages.size - 1)
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(stringResource(R.string.app_name)) },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.surface
                )
            )
        },
        floatingActionButton = {
            if (shouldShowFAB) {
                FloatingActionButton(
                    onClick = {
                        if (state.isRecording) {
                            // Stop recording and send
                            coroutineScope.launch {
                                val capture = audioRecorder.stop()
                                if (capture != null) {
                                    onEvent(AudioDemoEvent.SendAudioPrompt(capture.samples, capture.sampleRate))
                                }
                                onEvent(AudioDemoEvent.ToggleRecording)
                            }
                        } else {
                            // Start recording
                            val started = audioRecorder.start()
                            if (started) {
                                onEvent(AudioDemoEvent.ToggleRecording)
                            } else {
                                onEvent(AudioDemoEvent.RecordingFailed)
                            }
                        }
                    },
                    containerColor = if (state.isRecording) {
                        MaterialTheme.colorScheme.errorContainer
                    } else {
                        MaterialTheme.colorScheme.primaryContainer
                    },
                    contentColor = if (state.isRecording) {
                        MaterialTheme.colorScheme.onErrorContainer
                    } else {
                        MaterialTheme.colorScheme.onPrimaryContainer
                    }
                ) {
                    Icon(
                        imageVector = if (state.isRecording) Icons.Default.Stop else Icons.Default.Mic,
                        contentDescription = if (state.isRecording) {
                            stringResource(R.string.cd_stop_recording)
                        } else {
                            stringResource(R.string.cd_start_recording)
                        }
                    )
                }
            }
        },
        bottomBar = {
            InputBar(
                inputText = state.inputText,
                onInputTextChange = { onEvent(AudioDemoEvent.UpdateInputText(it)) },
                onSendClick = { onEvent(AudioDemoEvent.SendTextPrompt) },
                isRecording = state.isRecording,
                isEnabled = isInputEnabled
            )
        }
    ) { paddingValues ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(paddingValues)
        ) {
            // Status bar
            if (state.status != null) {
                Surface(
                    modifier = Modifier.fillMaxWidth(),
                    color = MaterialTheme.colorScheme.secondaryContainer,
                    tonalElevation = 2.dp
                ) {
                    Text(
                        text = state.status ?: "",
                        modifier = Modifier.padding(8.dp),
                        style = MaterialTheme.typography.bodySmall
                    )
                }
            }

            // Load model button or error
            if (!state.isModelLoaded && !state.isModelLoading) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(16.dp),
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    if (state.loadError != null) {
                        Text(
                            text = state.loadError,
                            color = MaterialTheme.colorScheme.error,
                            style = MaterialTheme.typography.bodyMedium,
                            modifier = Modifier.padding(horizontal = 16.dp)
                        )
                    }

                    Button(
                        onClick = {
                            if (state.canRetryLoad) {
                                onEvent(AudioDemoEvent.RetryLoadModel)
                            } else {
                                onEvent(AudioDemoEvent.LoadModel)
                            }
                        },
                        enabled = !state.isModelLoading
                    ) {
                        Text(
                            if (state.canRetryLoad) {
                                stringResource(R.string.retry)
                            } else {
                                stringResource(R.string.load_model)
                            }
                        )
                    }
                }
            }

            if (state.isModelLoading) {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(16.dp),
                    contentAlignment = Alignment.Center
                ) {
                    CircularProgressIndicator()
                }
            }

            // Messages list
            LazyColumn(
                state = listState,
                modifier = Modifier
                    .weight(1f)
                    .fillMaxWidth(),
                contentPadding = PaddingValues(16.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                items(
                    items = state.messages,
                    key = { message -> message.id }
                ) { message ->
                    MessageBubble(
                        message = message,
                        isPlayingAudio = state.playingMessageId == message.id,
                        onPlayAudio = { audioData, sampleRate ->
                            onEvent(AudioDemoEvent.PlayAudio(message.id, audioData, sampleRate))
                        },
                        onStopAudio = {
                            onEvent(AudioDemoEvent.StopAudioPlayback)
                        }
                    )
                }

                // Streaming text
                if (state.streamingText.isNotEmpty() || state.isStreamingAudio) {
                    item {
                        MessageBubble(
                            message = AudioDemoMessage(
                                role = ai.liquid.leap.message.ChatMessage.Role.ASSISTANT,
                                text = state.streamingText.ifEmpty { stringResource(R.string.streaming_placeholder) }
                            ),
                            isStreaming = true,
                            isPlayingAudio = false,
                            isStreamingAudio = state.isStreamingAudio,
                            onPlayAudio = { _, _ -> },
                            onStopAudio = {
                                onEvent(AudioDemoEvent.StopGeneration)
                            }
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
    onStopAudio: () -> Unit = {}
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = if (message.isUser) Arrangement.End else Arrangement.Start
    ) {
        Column(
            modifier = Modifier
                .widthIn(max = 300.dp)
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
            Text(
                text = message.text,
                style = MaterialTheme.typography.bodyMedium
            )

            // Show stop button during streaming audio generation
            if (isStreaming && isStreamingAudio) {
                Spacer(modifier = Modifier.height(8.dp))
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clickable { onStopAudio() }
                        .padding(4.dp),
                    horizontalArrangement = Arrangement.Center,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Icon(
                        imageVector = Icons.Default.Stop,
                        contentDescription = stringResource(R.string.cd_stop_audio),
                        tint = MaterialTheme.colorScheme.primary
                    )
                    Spacer(modifier = Modifier.width(4.dp))
                    Text(
                        text = stringResource(R.string.stop_audio),
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.primary
                    )
                }
            }

            // Show play/stop button for completed messages with audio
            if (!isStreaming && message.audioData != null && message.sampleRate > 0) {
                Spacer(modifier = Modifier.height(8.dp))
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clickable {
                            if (isPlayingAudio) {
                                onStopAudio()
                            } else {
                                onPlayAudio(message.audioData, message.sampleRate)
                            }
                        }
                        .padding(4.dp),
                    horizontalArrangement = Arrangement.Center,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Icon(
                        imageVector = if (isPlayingAudio) Icons.Default.Stop else Icons.Default.PlayArrow,
                        contentDescription = if (isPlayingAudio) {
                            stringResource(R.string.cd_stop_audio)
                        } else {
                            stringResource(R.string.cd_play_audio)
                        },
                        tint = MaterialTheme.colorScheme.primary
                    )
                    Spacer(modifier = Modifier.width(4.dp))
                    Text(
                        text = if (isPlayingAudio) {
                            stringResource(R.string.stop_audio)
                        } else {
                            stringResource(R.string.play_audio)
                        },
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.primary
                    )
                }
            }

            if (isStreaming) {
                Spacer(modifier = Modifier.height(4.dp))
                LinearProgressIndicator(
                    modifier = Modifier.fillMaxWidth()
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
    isEnabled: Boolean
) {
    Surface(
        modifier = Modifier.fillMaxWidth(),
        color = MaterialTheme.colorScheme.surface,
        tonalElevation = 3.dp,
        shadowElevation = 8.dp
    ) {
        if (isRecording) {
            // Recording indicator
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 20.dp),
                horizontalArrangement = Arrangement.Center,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Icon(
                    imageVector = Icons.Default.Mic,
                    contentDescription = stringResource(R.string.cd_recording_in_progress),
                    tint = MaterialTheme.colorScheme.error,
                    modifier = Modifier.size(24.dp)
                )
                Spacer(modifier = Modifier.width(12.dp))
                Text(
                    text = stringResource(R.string.recording),
                    color = MaterialTheme.colorScheme.error,
                    style = MaterialTheme.typography.titleMedium
                )
                Spacer(modifier = Modifier.width(12.dp))
                CircularProgressIndicator(
                    modifier = Modifier.size(20.dp),
                    color = MaterialTheme.colorScheme.error,
                    strokeWidth = 2.dp
                )
            }
        } else {
            // Normal input UI
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 12.dp, vertical = 8.dp)
                    .padding(bottom = 8.dp),
                verticalAlignment = Alignment.Bottom,
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                OutlinedTextField(
                    value = inputText,
                    onValueChange = onInputTextChange,
                    modifier = Modifier
                        .weight(1f),
                    placeholder = { Text(stringResource(R.string.type_message)) },
                    enabled = isEnabled && !isRecording,
                    shape = RoundedCornerShape(24.dp),
                    colors = TextFieldDefaults.colors(
                        focusedContainerColor = MaterialTheme.colorScheme.surfaceVariant,
                        unfocusedContainerColor = MaterialTheme.colorScheme.surfaceVariant,
                        disabledContainerColor = MaterialTheme.colorScheme.surfaceVariant,
                        focusedIndicatorColor = Color.Transparent,
                        unfocusedIndicatorColor = Color.Transparent,
                        disabledIndicatorColor = Color.Transparent
                    ),
                    maxLines = 4
                )
                FilledIconButton(
                    onClick = onSendClick,
                    enabled = isEnabled && inputText.isNotBlank() && !isRecording,
                    modifier = Modifier.size(48.dp)
                ) {
                    Icon(
                        Icons.Default.Send,
                        contentDescription = stringResource(R.string.cd_send_message)
                    )
                }
            }
        }
    }
}
