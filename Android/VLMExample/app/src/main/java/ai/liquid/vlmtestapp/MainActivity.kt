package ai.liquid.vlmtestapp

import ai.liquid.leap.LeapClient
import ai.liquid.leap.ModelRunner
import ai.liquid.leap.message.ChatMessage
import ai.liquid.leap.message.ChatMessageContent
import ai.liquid.leap.message.MessageResponse
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.ui.Modifier
import android.util.Log
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.heightIn
import androidx.compose.material3.Button
import androidx.compose.runtime.getValue
import androidx.compose.runtime.livedata.observeAsState
import androidx.compose.ui.Alignment
import androidx.compose.ui.unit.dp
import androidx.core.content.FileProvider
import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.lifecycleScope
import coil3.compose.AsyncImage
import kotlinx.coroutines.flow.collect
import kotlinx.coroutines.flow.onCompletion
import kotlinx.coroutines.flow.onEach
import kotlinx.coroutines.launch
import java.io.File

class MainActivity : ComponentActivity() {
    private val generateText: MutableLiveData<String> = MutableLiveData<String>()
    private val isPhotoTaken: MutableLiveData<Boolean> = MutableLiveData<Boolean>(false)
    private var photoCount = 0
    private val imageFileLiveData: MutableLiveData<File> = MutableLiveData<File>()
    private val isTakePictureButtonEnabled: MutableLiveData<Boolean> =
        MutableLiveData<Boolean>(true)
    private lateinit var modelRunner: ModelRunner

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val launchCamera =
            registerForActivityResult(ActivityResultContracts.TakePicture()) { result ->
                if (result) {
                    isPhotoTaken.value = true
                    val file = File(
                        this@MainActivity.externalCacheDir,
                        "image_to_process_$photoCount.jpg"
                    )
                    imageFileLiveData.value = file
                    lifecycleScope.launch {
                        generateWithImage(file.inputStream().readBytes())
                        isTakePictureButtonEnabled.value = true
                    }
                } else {
                    isTakePictureButtonEnabled.value = true
                }
            }
        enableEdgeToEdge()
        setContent {
            Scaffold(modifier = Modifier.fillMaxSize()) { innerPadding ->
                val generateTextState by generateText.observeAsState()
                val isPhotoTakenState by isPhotoTaken.observeAsState(false)
                val imageFileCache by imageFileLiveData.observeAsState()
                val isTakePictureButtonEnabledState by isTakePictureButtonEnabled.observeAsState(
                    false
                )
                Box(modifier = Modifier.padding(innerPadding)) {
                    Column(
                        modifier = Modifier
                            .padding(4.dp)
                            .fillMaxSize(), horizontalAlignment = Alignment.CenterHorizontally
                    ) {
                        Button(onClick = {
                            generateText.value = ""
                            photoCount += 1
                            val newImageFile = File(
                                this@MainActivity.externalCacheDir,
                                "image_to_process_$photoCount.jpg"
                            )
                            val imageUri = FileProvider.getUriForFile(
                                this@MainActivity,
                                applicationContext.packageName + ".provider",
                                newImageFile
                            );
                            isTakePictureButtonEnabled.value = false
                            launchCamera.launch(imageUri)
                        }, enabled = isTakePictureButtonEnabledState) {
                            Text("Take a picture")
                        }
                        if (isPhotoTakenState) {
                            AsyncImage(
                                model = imageFileCache,
                                contentDescription = "Translated description of what the image contains",
                                modifier = Modifier.heightIn(100.dp, 200.dp)
                            )
                        }

                        Text(generateTextState ?: "", modifier = Modifier)
                    }
                }

            }
        }
    }

    suspend fun generateWithImage(imageData: ByteArray) {
        if (!this::modelRunner.isInitialized) {
            generateText.value = "Loading the model..."
            val modelFilePath = "/data/local/tmp/liquid/LFM2-VL-1_6B.bundle"
            modelRunner = LeapClient.loadModelAsResult(modelFilePath).getOrThrow()
        }
        generateText.value = "Looking at the image and generating a description..."
        val conversation =
            modelRunner.createConversation("You are a helpful multimodal assistant by Liquid AI.")

        val userMessage =
            ChatMessage(
                role = ChatMessage.Role.USER,
                content =
                    listOf(
                        ChatMessageContent.Text("Describe this image."),
                        ChatMessageContent.Image(imageData),
                    ),
            )

        var isGenerationStarted = false
        conversation.generateResponse(userMessage)
            .onEach {
                if (it is MessageResponse.Chunk) {
                    if (!isGenerationStarted) {
                        isGenerationStarted = true
                        generateText.value = ""
                    }
                    generateText.value = generateText.value + it.text
                } else if (it is MessageResponse.Complete) {
                    val generatedContent = it.fullMessage.content.first() as ChatMessageContent.Text
                    generateText.value = generatedContent.text
                    Log.d("MainActivity", it.toString())
                }
            }.onCompletion {
                conversation.history.forEach {
                    Log.d("MainActivity", it.toJSONObject().toString())
                }
            }.collect()
    }
}
