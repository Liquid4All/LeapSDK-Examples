package ai.liquid.leapchat

import ai.liquid.leap.Conversation
import ai.liquid.leap.LeapClient
import ai.liquid.leap.LeapModelLoadingException
import ai.liquid.leap.ModelRunner
import ai.liquid.leap.gson.LeapGson
import ai.liquid.leap.gson.registerLeapAdapters
import ai.liquid.leap.message.ChatMessage
import ai.liquid.leap.downloader.LeapModelDownloader
import ai.liquid.leap.downloader.LeapDownloadableModel
import ai.liquid.leap.function.LeapFunction
import ai.liquid.leap.function.LeapFunctionCall
import ai.liquid.leap.function.LeapFunctionParameter
import ai.liquid.leap.function.LeapFunctionParameterType
import ai.liquid.leap.message.ChatMessageContent
import ai.liquid.leap.message.MessageResponse
import ai.liquid.leapchat.models.ChatMessageDisplayItem
import ai.liquid.leapchat.views.ChatHistory
import android.annotation.SuppressLint
import android.os.Bundle
import android.util.Log
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.focusable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.ime
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.union
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.NavigationBarDefaults
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextField
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.livedata.observeAsState
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.lifecycleScope
import com.google.gson.GsonBuilder
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.collect
import kotlinx.coroutines.flow.onCompletion
import kotlinx.coroutines.flow.onEach
import kotlinx.coroutines.launch
import kotlin.collections.plus

class MainActivity : ComponentActivity() {
    // The generation job instance.
    private var job: Job? = null

    // The model runner instance.
    private val modelRunner: MutableLiveData<ModelRunner> by lazy {
        MutableLiveData<ModelRunner>()
    }

    // The conversation instance. It will be cached and shared between the generations. If the
    // activity is destroyed and restored, it will be re-created from the dumped states.
    private var conversation: Conversation? = null

    // The chat message history for displaying
    private val chatMessageHistory: MutableLiveData<List<ChatMessageDisplayItem>> by lazy {
        MutableLiveData<List<ChatMessageDisplayItem>>()
    }

    // Conversation history json string. This value will be updated once the generation is complete
    // and will be used to persistent the states when the activity is destroyed
    private var conversationHistoryJSONString: String? = null

    // Whether the generation is still ongoing
    private val isInGeneration: MutableLiveData<Boolean> by lazy {
        MutableLiveData<Boolean>(false)
    }

    private val gson = GsonBuilder().registerLeapAdapters().create()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        if (savedInstanceState != null) {
            loadState(savedInstanceState)
        }
        enableEdgeToEdge()
        setContent { MainContent() }
    }

    override fun onDestroy() {
        super.onDestroy()
        lifecycleScope.launch { modelRunner.value?.unload() }
    }

    override fun onSaveInstanceState(outState: Bundle) {
        super.onSaveInstanceState(outState)
        if (conversationHistoryJSONString != null) {
            outState.putString("history-json", conversationHistoryJSONString)
        }
    }

    /**
     * The composable of the main activity content
     */
    @Composable
    fun MainContent() {
        val modelRunnerInstance by modelRunner.observeAsState()
        val chatMessageHistory: List<ChatMessageDisplayItem> by chatMessageHistory.observeAsState(
            listOf()
        )
        var userInputFieldText by remember { mutableStateOf("") }
        val chatHistoryFocusRequester = remember { FocusRequester() }
        val isInGeneration = this.isInGeneration.observeAsState(false)
        Scaffold(
            modifier = Modifier
                .fillMaxSize()
                .windowInsetsPadding(
                    NavigationBarDefaults.windowInsets.union(
                        WindowInsets.ime
                    )
                ),
            bottomBar = {
                Box {
                    if (modelRunnerInstance == null) {
                        ModelLoadingIndicator(modelRunnerInstance) { onError, onStatusChange ->
                            loadModel(onError, onStatusChange)
                        }
                    } else {
                        Column {
                            TextField(
                                value = userInputFieldText,
                                onValueChange = { userInputFieldText = it },
                                modifier = Modifier
                                    .padding(4.dp)
                                    .fillMaxWidth(1.0f),
                                enabled = !isInGeneration.value
                            )
                            Row(
                                horizontalArrangement = Arrangement.End,
                                modifier = Modifier.fillMaxWidth(1.0f)
                            ) {
                                Button(
                                    onClick = {
                                        this@MainActivity.isInGeneration.value = true
                                        sendText(userInputFieldText)
                                        userInputFieldText = ""
                                        chatHistoryFocusRequester.requestFocus()
                                    },
                                    enabled = !isInGeneration.value
                                ) {
                                    Text(getString(R.string.send_message_button_label))
                                }
                                Button(
                                    onClick = {
                                        job?.cancel()
                                    },
                                    enabled = isInGeneration.value
                                ) {
                                    Text(getString(R.string.stop_generation_button_label))
                                }
                                Button(
                                    onClick = {
                                        conversationHistoryJSONString = null
                                        this@MainActivity.chatMessageHistory.value = listOf()
                                    },
                                    enabled = !isInGeneration.value && (conversationHistoryJSONString != null)
                                ) {
                                    Text(getString(R.string.clean_history_button_label))
                                }
                            }
                        }
                    }
                }
            },
        ) { innerPadding ->
            Box(
                Modifier
                    .padding(innerPadding)
                    .focusRequester(chatHistoryFocusRequester)
                    .focusable(true)
            ) {
                ChatHistory(chatMessageHistory)
            }
        }
    }

    /**
     * Load the model file.
     */
    private fun loadModel(onError: (Throwable) -> Unit, onStatusChange: (String) -> Unit) {
        lifecycleScope.launch {
            try {
                val modelToUse = LeapDownloadableModel.resolve(MODEL_SLUG, QUANTIZATION_SLUG)
                if (modelToUse == null) {
                    throw RuntimeException("Model $QUANTIZATION_SLUG not found in Leap Model Library!")
                }
                val modelDownloader = LeapModelDownloader(this@MainActivity)
                modelDownloader.requestDownloadModel(modelToUse)

                var isModelAvailable = false
                while (!isModelAvailable) {
                    val status = modelDownloader.queryStatus(modelToUse)
                    when (status) {
                        LeapModelDownloader.ModelDownloadStatus.NotOnLocal -> {
                            onStatusChange("Model is not downloaded. Waiting for downloading...")
                        }

                        is LeapModelDownloader.ModelDownloadStatus.DownloadInProgress -> {
                            if (status.totalSizeInBytes > 0) {
                                val progress =
                                    status.downloadedSizeInBytes.toDouble() / status.totalSizeInBytes
                                onStatusChange(
                                    "Downloading the model: ${
                                        String.format(
                                            "%.2f",
                                            progress * 100.0
                                        )
                                    }%"
                                )
                            } else {
                                onStatusChange(
                                    "Downloading the model..."
                                )
                            }

                        }

                        is LeapModelDownloader.ModelDownloadStatus.Downloaded -> {
                            isModelAvailable = true
                        }
                    }
                    delay(500)
                }
                val modelFile = modelDownloader.getModelFile(modelToUse)
                onStatusChange("Loading the model: ${modelFile.path}")

                modelRunner.value = LeapClient.loadModel(modelFile.path)
            } catch (e: LeapModelLoadingException) {
                onError(e)
            }
        }
    }

    /**
     * Send a text as user message and start generation.
     */
    private fun sendText(input: String) {
        appendUserMessage(input)
        val modelRunner = checkNotNull(modelRunner.value)
        val conversation = getOrRestoreConversation(modelRunner)
        sendMessage(conversation, ChatMessage(role = ChatMessage.Role.USER, textContent = input))
    }

    private fun sendToolText(toolText: String) {
        appendToolMessage(toolText)
        val modelRunner = checkNotNull(modelRunner.value)
        val conversation = getOrRestoreConversation(modelRunner)
        sendMessage(conversation, ChatMessage(role = ChatMessage.Role.TOOL, textContent = toolText))
    }

    private fun sendMessage(conversation: Conversation, message: ChatMessage) {
        job =
            lifecycleScope.launch {
                val generateTextBuffer = StringBuilder()
                val generatedReasoningBuffer = StringBuilder()
                val functionCallsToInvoke = mutableListOf<LeapFunctionCall>()
                // Generate the response
                conversation.generateResponse(message).onEach {
                    when (it) {
                        is MessageResponse.Chunk -> {
                            generateTextBuffer.append(it.text)
                        }

                        is MessageResponse.ReasoningChunk -> {
                            generatedReasoningBuffer.append(it.reasoning)
                        }

                        is MessageResponse.FunctionCalls -> {
                            it.functionCalls.forEach { call ->
                                generatedReasoningBuffer.append("Calling tool: ${call.name}")
                                functionCallsToInvoke.add(call)
                            }
                        }

                        else -> {}
                    }
                    updateLastAssistantMessage(
                        generateTextBuffer.toString(),
                        generatedReasoningBuffer.toString()
                    )
                }
                    .onCompletion {
                        this@MainActivity.isInGeneration.value = false
                        conversationHistoryJSONString = gson.toJson(conversation.history)
                    }
                    .catch { exception ->
                        Log.e(
                            this@MainActivity::class.java.simpleName,
                            "Error in generation: $exception",
                        )
                    }
                    .collect()
                if (functionCallsToInvoke.isNotEmpty()) {
                    processFunctionCalls(functionCallsToInvoke)
                }
            }
    }

    private fun processFunctionCalls(functionCalls: List<LeapFunctionCall>) {
        for (call in functionCalls) {
            when (call.name) {
                "compute_sum" -> {
                    val numbers = call.arguments["values"] as? List<String> ?: listOf()
                    var sum = 0.0
                    for (v in numbers) {
                        sum += v.toDoubleOrNull() ?: 0.0
                    }
                    sendToolText("Sum = $sum")
                }

                else -> {
                    sendToolText("Tool: ${call.name} is not available")
                }
            }
        }
    }

    /**
     * In case `conversation` is not available due to the activity destroy, restore it from the
     * conversation history.
     */
    private fun getOrRestoreConversation(modelRunner: ModelRunner): Conversation {
        val conversationInstance = conversation
        val conversation = if (conversationInstance == null) {
            val conversationHistory = getConversationHistory()
            if (conversationHistory == null) {
                modelRunner.createConversation(getString(R.string.chat_system_prompt))
            } else {
                modelRunner.createConversationFromHistory(conversationHistory)
            }
        } else {
            conversationInstance
        }


        conversation.registerFunction(
            LeapFunction(
                "compute_sum", "Compute sum of a series of numbers", listOf(
                    LeapFunctionParameter(
                        name = "values",
                        type = LeapFunctionParameterType.Array(
                            itemType = LeapFunctionParameterType.String()
                        ),
                        description = "Numbers to compute sum. Values should be represented in string."
                    )
                )
            )
        )

        return conversation
    }

    /**
     * Retrieve the conversation history from the serialized JSON string.
     */
    private fun getConversationHistory(): List<ChatMessage>? {
        val jsonStr = conversationHistoryJSONString
        if (jsonStr == null) {
            return null
        }
        return gson.fromJson(jsonStr, LeapGson.messageListTypeToken)
    }

    /**
     * Load displayed chat history from the instance state bundle.
     */
    private fun loadState(state: Bundle) {
        conversationHistoryJSONString = state.getString("history-json")

        if (conversationHistoryJSONString != null) {
            getConversationHistory()?.map {
                val textContent = it.content.joinToString { (it as ChatMessageContent.Text).text }
                ChatMessageDisplayItem(
                    role = it.role,
                    text = textContent,
                    reasoning = it.reasoningContent
                )
            }?.let {
                chatMessageHistory.value = it
            }
        }
    }

    /**
     * Append a user message to the history
     */
    private fun appendUserMessage(content: String) {
        val chatMessageHistoryValue = chatMessageHistory.value
        val newMessage = ChatMessageDisplayItem(ChatMessage.Role.USER, text = content)
        if (chatMessageHistoryValue.isNullOrEmpty()) {
            chatMessageHistory.value = listOf(newMessage)
        } else {
            chatMessageHistory.value = chatMessageHistoryValue + listOf(newMessage)
        }
    }

    /**
     * Append a user message to the history
     */
    private fun appendToolMessage(content: String) {
        val chatMessageHistoryValue = chatMessageHistory.value
        val newMessage = ChatMessageDisplayItem(ChatMessage.Role.TOOL, text = content)
        if (chatMessageHistoryValue.isNullOrEmpty()) {
            chatMessageHistory.value = listOf(newMessage)
        } else {
            chatMessageHistory.value = chatMessageHistoryValue + listOf(newMessage)
        }
    }

    /**
     * If the last message is not an assistant message, insert a new one. Otherwise, update that
     * assistant message.
     */
    private fun updateLastAssistantMessage(content: String, reasoning: String?) {
        val chatMessageHistoryValue = chatMessageHistory.value
        val newChatMessageHistory = (chatMessageHistoryValue ?: listOf()).toMutableList()
        if (newChatMessageHistory.lastOrNull()?.role == ChatMessage.Role.ASSISTANT) {
            newChatMessageHistory.removeAt(newChatMessageHistory.lastIndex)
        }
        newChatMessageHistory.add(
            ChatMessageDisplayItem(
                role = ChatMessage.Role.ASSISTANT,
                text = content,
                reasoning = reasoning,
            ),
        )
        chatMessageHistory.value = newChatMessageHistory
    }

    companion object {
        const val MODEL_SLUG = "lfm2-1.2b"
        const val QUANTIZATION_SLUG = "lfm2-1.2b-20250710-8da4w"
    }
}

/**
 * The screen to show when the app is loading the model.
 */
@Composable
fun ModelLoadingIndicator(
    modelRunnerState: ModelRunner?,
    loadModelAction: (onError: (e: Throwable) -> Unit, onStatusChange: (String) -> Unit) -> Unit,
) {
    val context = LocalContext.current
    var modelLoadingStatusText by remember { mutableStateOf(context.getString(R.string.loading_model_content)) }
    LaunchedEffect(modelRunnerState) {
        if (modelRunnerState == null) {
            loadModelAction({ error ->
                modelLoadingStatusText =
                    context.getString(R.string.loading_model_fail_content, error.message)
            }, { status ->
                modelLoadingStatusText = status
            })
        }
    }
    Box(Modifier
        .padding(4.dp)
        .fillMaxSize(1.0f), contentAlignment = Alignment.Center) {
        Text(modelLoadingStatusText, style = MaterialTheme.typography.titleSmall)
    }
}
