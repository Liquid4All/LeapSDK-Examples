package com.leap.shareai.viewmodels

import ai.liquid.leap.Conversation
import ai.liquid.leap.LeapClient
import ai.liquid.leap.LeapModelLoadingException
import ai.liquid.leap.ModelRunner
import ai.liquid.leap.message.ChatMessage
import ai.liquid.leap.message.MessageResponse
import android.util.Log
import androidx.compose.runtime.State
import androidx.compose.runtime.mutableStateOf
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.leap.shareai.model.ChatMessageDisplayItem
import com.leap.shareai.webscraping.WebPageState
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.onCompletion
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch


class AIChatViewModel : ViewModel() {

    companion object {
        private const val TAG = "LeapViewModel"
        private const val MODEL_PATH = "/data/local/tmp/liquid/LFM2-350M-8da4w_output_8da8w-seq_4096.bundle"
        private const val PROMPT = "Make a summary less than 500 words of the following text. Use Markdown If needed:\n\n"
    }

    private val _state = mutableStateOf<LeapState>(LeapState.Idle)
    val state: State<LeapState> = _state

    private var modelRunner: ModelRunner? = null
    private var conversation: Conversation? = null
    private var generationJob: Job? = null

    private val _responseChunks = MutableSharedFlow<String>()
    val responseChunks: SharedFlow<String> = _responseChunks

    private val _reasoningChunks = MutableSharedFlow<String>()
    val reasoningChunks: SharedFlow<String> = _reasoningChunks

    private val _isLoading = MutableSharedFlow<Boolean>()
    val isLoading: SharedFlow<Boolean> = _isLoading

    private val _messages = MutableStateFlow<List<ChatMessageDisplayItem>>(emptyList())
    val messages: StateFlow<List<ChatMessageDisplayItem>> = _messages

    // Temporary holders for current model response
    private var currentResponseText = StringBuilder()
    private var currentReasoningText = StringBuilder()

    fun loadModel() {
        _state.value = LeapState.Loading
        viewModelScope.launch {
            _isLoading.emit(true)
            try {
                modelRunner = LeapClient.loadModel(MODEL_PATH)
                conversation = modelRunner?.createConversation()
                Log.d(TAG, "Model loaded and conversation created")
            } catch (e: LeapModelLoadingException) {
                _state.value = LeapState.Error("Failed to load model: ${e.message}")
                Log.e(TAG, "Failed to load model: ${e.message}")
            } finally {
                _isLoading.emit(false)
            }
        }
    }

    fun sendMessage(userInput: String) {
        // Add user's message to the list
        Log.d(TAG, "Sending user input: $userInput")
        val massage = PROMPT + userInput.trim()
        val userMessage = ChatMessageDisplayItem(ChatMessage.Role.USER, massage)
        _messages.update { it + userMessage }

        generateResponse(massage)
    }


    private fun generateResponse(input: String) {

        val conv = conversation ?: run {
            Log.e(TAG, "Conversation not initialized")
            return
        }

        generationJob?.cancel() // cancel previous if any

        generationJob = viewModelScope.launch {
            _state.value = LeapState.Thinking
            conv.generateResponse(input)
                .onCompletion {
                    Log.d(TAG, "Generation complete")
                }
                .catch { e ->
                    Log.e(TAG, "Error generating response: ${e.message}")
                    _state.value = LeapState.Error("Error: ${e.message}")
                }
                .collect { response ->
                    when (response) {
                        is MessageResponse.Chunk -> {
                            _state.value = LeapState.Generating
                            currentResponseText.append(response.text)
                            _responseChunks.emit(response.text)
                        }

                        is MessageResponse.ReasoningChunk -> {
                            _state.value = LeapState.Thinking
                            currentReasoningText.append(response.reasoning)
                            _reasoningChunks.emit(response.reasoning)
                        }

                        is MessageResponse.Complete -> {
                            _state.value = LeapState.Success(currentResponseText.toString())
                            val aiMessage = ChatMessageDisplayItem(
                                role = ChatMessage.Role.ASSISTANT,
                                text = currentResponseText.toString(),
                                reasoning = currentReasoningText.takeIf { it.isNotBlank() }
                                    ?.toString()
                            )
                            _messages.update { it + aiMessage }
                            Log.d(TAG, "AI message completed and added to list")
                        }

                        else -> Unit
                    }
                }
        }
    }

    fun cancelGeneration() {
        generationJob?.cancel()
        Log.d(TAG, "Generation canceled")
    }
}

sealed class LeapState {
    object Idle: LeapState()
    object Thinking : LeapState()
    object Generating : LeapState()
    object Loading : LeapState()
    data class Error(val message: String) : LeapState()
    data class Success(val response: String) : LeapState()
}
