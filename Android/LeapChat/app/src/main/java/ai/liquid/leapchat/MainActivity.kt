package ai.liquid.leapchat

import ai.liquid.leap.Conversation
import ai.liquid.leap.LeapClient
import ai.liquid.leap.LeapModelLoadingException
import ai.liquid.leap.ModelRunner
import ai.liquid.leap.message.ChatMessage
import ai.liquid.leap.downloader.LeapModelDownloader
import ai.liquid.leap.downloader.LeapModelDownloaderNotificationConfig
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
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.dp
import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.lifecycleScope
import kotlinx.coroutines.Job
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.collect
import kotlinx.coroutines.flow.onCompletion
import kotlinx.coroutines.flow.onEach
import kotlinx.coroutines.flow.takeWhile
import kotlinx.coroutines.launch
import kotlin.collections.plus
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign

class MainActivity : ComponentActivity() {
    // The generation job instance.
    private var job: Job? = null

    // The model runner instance.
    private val modelRunner: MutableLiveData<ModelRunner> by lazy {
        MutableLiveData<ModelRunner>()
    }

    // The model downloader instance (reused to avoid concurrent download issues)
    private var downloader: LeapModelDownloader? = null

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

    private val isToolEnabled: MutableLiveData<Boolean> by lazy {
        MutableLiveData<Boolean>(false)
    }

    private val json = Json { ignoreUnknownKeys = true }

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
                                    .fillMaxWidth(1.0f).testTag("InputBox"),
                                enabled = !isInGeneration.value
                            )
                            Row(
                                horizontalArrangement = Arrangement.End,
                                modifier = Modifier.fillMaxWidth(1.0f)
                            ) {
                                val isToolEnabledState by isToolEnabled.observeAsState(false)
                                Button(onClick = {
                                    isToolEnabled.value = !isToolEnabledState
                                }) {
                                    if (isToolEnabledState) {
                                        Text(getString(R.string.tool_on_button_label))
                                    } else {
                                        Text(getString(R.string.tool_off_button_label))
                                    }
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
                // Reuse downloader instance to avoid concurrent download issues
                if (downloader == null) {
                    downloader = LeapModelDownloader(
                        this@MainActivity,
                        notificationConfig = LeapModelDownloaderNotificationConfig.build {
                            notificationTitleDownloading = "Downloading Chat Model"
                            notificationTitleDownloaded = "Model Ready!"
                        }
                    )
                }
                val downloaderInstance = downloader!!

                // Check if model needs to be downloaded
                val currentStatus = downloaderInstance.queryStatus(MODEL_NAME, QUANTIZATION_SLUG)

                if (currentStatus is LeapModelDownloader.ModelDownloadStatus.NotOnLocal) {
                    // Model needs to be downloaded
                    onStatusChange("Starting download...")

                    // Observe download progress
                    val progressFlow = downloaderInstance.observeDownloadProgress(MODEL_NAME, QUANTIZATION_SLUG)

                    // Start the download
                    downloaderInstance.requestDownloadModel(MODEL_NAME, QUANTIZATION_SLUG)

                    // Collect progress updates until download completes
                    progressFlow
                        .onEach { progress ->
                            if (progress != null) {
                                val downloadedMB = progress.downloadedSizeInBytes / (1024 * 1024)
                                val totalMB = progress.totalSizeInBytes / (1024 * 1024)
                                val percentage = if (progress.totalSizeInBytes > 0) {
                                    (progress.downloadedSizeInBytes * 100.0 / progress.totalSizeInBytes).toInt()
                                } else {
                                    0
                                }
                                onStatusChange("Downloading model: $percentage% ($downloadedMB MB / $totalMB MB)")
                            } else {
                                val downloadStatus = downloaderInstance.queryStatus(MODEL_NAME, QUANTIZATION_SLUG)
                                if (downloadStatus is LeapModelDownloader.ModelDownloadStatus.Downloaded) {
                                    onStatusChange("Download complete!")
                                }
                            }
                        }
                        .takeWhile { progress ->
                            progress != null || downloaderInstance.queryStatus(MODEL_NAME, QUANTIZATION_SLUG) is LeapModelDownloader.ModelDownloadStatus.DownloadInProgress
                        }
                        .collect()
                }

                modelRunner.value = downloaderInstance.loadModel(
                    modelName = MODEL_NAME,
                    quantizationType = QUANTIZATION_SLUG,
                )
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
                        conversationHistoryJSONString = json.encodeToString(conversation.history)
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

        if (isToolEnabled.value == true) {
            conversation.registerFunction(
                LeapFunction(
                    "compute_sum", "Compute sum of a series of numbers", listOf(
                        LeapFunctionParameter(
                            name = "values",
                            type = LeapFunctionParameterType.LeapArr(
                                itemType = LeapFunctionParameterType.LeapStr()
                            ),
                            description = "Numbers to compute sum. Values should be represented in string."
                        )
                    )
                )
            )
        }

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
        return json.decodeFromString<List<ChatMessage>>(jsonStr)
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
        const val MODEL_NAME = "LFM2-350M"
        const val QUANTIZATION_SLUG = "Q8_0"
    }
}

/**
 * The screen to show when the app is loading the model.
 */
@SuppressLint("LocalContextGetResourceValueCall")
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
    Column(
        modifier = Modifier
            .padding(16.dp)
            .fillMaxSize()
            .testTag("ModelLoadingIndicator"),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        Text(
            text = modelLoadingStatusText,
            style = MaterialTheme.typography.titleSmall,
            textAlign = TextAlign.Center
        )
    }
}