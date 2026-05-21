package ai.liquid.vlmtestapp

import ai.liquid.leap.ModelLoadingOptions
import ai.liquid.leap.ModelRunner
import ai.liquid.leap.downloader.LeapModelDownloader
import ai.liquid.leap.downloader.LeapModelDownloaderNotificationConfig
import ai.liquid.leap.message.ChatMessage
import ai.liquid.leap.message.ChatMessageContent
import ai.liquid.leap.message.MessageResponse
import android.os.Bundle
import android.util.Log
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Button
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.getValue
import androidx.compose.runtime.livedata.observeAsState
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.core.content.FileProvider
import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.lifecycleScope
import coil3.compose.AsyncImage
import java.io.File
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.collect
import kotlinx.coroutines.flow.onCompletion
import kotlinx.coroutines.flow.onEach
import kotlinx.coroutines.flow.takeWhile
import kotlinx.coroutines.launch

class MainActivity : ComponentActivity() {
  private val generateText: MutableLiveData<String> = MutableLiveData<String>()
  private val isPhotoTaken: MutableLiveData<Boolean> = MutableLiveData<Boolean>(false)
  private var photoCount = 0
  private val imageFileLiveData: MutableLiveData<File> = MutableLiveData<File>()
  private val isTakePictureButtonEnabled: MutableLiveData<Boolean> = MutableLiveData<Boolean>(true)
  private var modelRunner: ModelRunner? = null
  private var downloader: LeapModelDownloader? = null

  companion object {
    private const val TAG = "MainActivity"
    private const val MODEL_NAME = "LFM2.5-VL-1.6B"
    private const val QUANTIZATION_TYPE = "Q8_0"
  }

  override fun onDestroy() {
    super.onDestroy()
    // Unload the model on a fresh scope: lifecycleScope is cancelled by the time
    // onDestroy fires, so any launches there would be skipped.
    val runner = modelRunner
    modelRunner = null
    CoroutineScope(Dispatchers.IO).launch {
      try {
        runner?.unload()
      } catch (e: Exception) {
        Log.e(TAG, "Error unloading model", e)
      }
    }
  }

  override fun onCreate(savedInstanceState: Bundle?) {
    super.onCreate(savedInstanceState)
    val launchCamera =
      registerForActivityResult(ActivityResultContracts.TakePicture()) { result ->
        if (result) {
          isPhotoTaken.value = true
          val file = File(this@MainActivity.externalCacheDir, "image_to_process_$photoCount.jpg")
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
        val isTakePictureButtonEnabledState by isTakePictureButtonEnabled.observeAsState(false)
        Box(modifier = Modifier.padding(innerPadding)) {
          Column(
            modifier = Modifier.padding(4.dp).fillMaxSize(),
            horizontalAlignment = Alignment.CenterHorizontally,
          ) {
            Button(
              onClick = {
                generateText.value = ""
                photoCount += 1
                val newImageFile =
                  File(this@MainActivity.externalCacheDir, "image_to_process_$photoCount.jpg")
                val imageUri =
                  FileProvider.getUriForFile(
                    this@MainActivity,
                    applicationContext.packageName + ".provider",
                    newImageFile,
                  )
                isTakePictureButtonEnabled.value = false
                launchCamera.launch(imageUri)
              },
              enabled = isTakePictureButtonEnabledState,
            ) {
              Text(getString(R.string.take_picture_button))
            }
            if (isPhotoTakenState) {
              AsyncImage(
                model = imageFileCache,
                contentDescription = getString(R.string.image_content_description),
                modifier = Modifier.heightIn(100.dp, 200.dp),
              )
            }

            Text(generateTextState ?: "", modifier = Modifier)
          }
        }
      }
    }
  }

  suspend fun generateWithImage(imageData: ByteArray) {
    if (modelRunner == null) {
      generateText.value = getString(R.string.status_loading_model)

      // Reuse downloader instance to avoid concurrent download issues
      if (downloader == null) {
        downloader =
          LeapModelDownloader(
            this@MainActivity,
            notificationConfig =
              LeapModelDownloaderNotificationConfig.build {
                notificationTitleDownloading = getString(R.string.notification_downloading_model)
                notificationTitleDownloaded = getString(R.string.notification_model_ready)
              },
          )
      }
      val downloaderInstance = downloader!!

      // Check if model needs to be downloaded
      val currentStatus = downloaderInstance.queryStatus(MODEL_NAME, QUANTIZATION_TYPE)

      if (currentStatus is LeapModelDownloader.ModelDownloadStatus.NotOnLocal) {
        // Model needs to be downloaded
        generateText.value = getString(R.string.status_starting_download)

        // Observe download progress
        val progressFlow = downloaderInstance.observeDownloadProgress(MODEL_NAME, QUANTIZATION_TYPE)

        // Start the download
        downloaderInstance.requestDownloadModel(MODEL_NAME, QUANTIZATION_TYPE)

        // Collect progress updates until download completes
        progressFlow
          .onEach { progress ->
            if (progress != null) {
              val downloadedMB = (progress.downloadedSizeInBytes / (1024 * 1024)).toInt()
              val totalMB = (progress.totalSizeInBytes / (1024 * 1024)).toInt()
              val percentage =
                if (progress.totalSizeInBytes > 0) {
                  (progress.downloadedSizeInBytes * 100.0 / progress.totalSizeInBytes).toInt()
                } else {
                  0
                }
              generateText.value =
                getString(R.string.status_downloading_progress, percentage, downloadedMB, totalMB)
            } else {
              val downloadStatus = downloaderInstance.queryStatus(MODEL_NAME, QUANTIZATION_TYPE)
              if (downloadStatus is LeapModelDownloader.ModelDownloadStatus.Downloaded) {
                generateText.value = getString(R.string.status_download_complete)
              }
            }
          }
          .takeWhile { progress ->
            progress != null ||
              downloaderInstance.queryStatus(MODEL_NAME, QUANTIZATION_TYPE) is
                LeapModelDownloader.ModelDownloadStatus.DownloadInProgress
          }
          .collect()
      }

      modelRunner =
        downloaderInstance.loadModel(
          modelName = MODEL_NAME,
          quantizationType = QUANTIZATION_TYPE,
          options =
            ModelLoadingOptions(
              cacheOptions =
                ModelLoadingOptions.cacheOptions(path = cacheDir.resolve("leap-cache").absolutePath)
            ),
        )
    }
    generateText.value = getString(R.string.status_generating_description)
    val runner = modelRunner!!
    val conversation = runner.createConversation(getString(R.string.system_prompt_vlm))

    val userMessage =
      ChatMessage(
        role = ChatMessage.Role.USER,
        content =
          listOf(
            ChatMessageContent.Text(getString(R.string.user_prompt_describe_image)),
            ChatMessageContent.Image(imageData),
          ),
      )

    var isGenerationStarted = false
    conversation
      .generateResponse(userMessage)
      .onEach { response ->
        when (response) {
          is MessageResponse.Chunk -> {
            if (!isGenerationStarted) {
              isGenerationStarted = true
              generateText.value = ""
            }
            generateText.value = generateText.value + response.text
          }
          is MessageResponse.Complete -> {
            val generatedContent = response.fullMessage.content.first() as ChatMessageContent.Text
            generateText.value = generatedContent.text
            Log.d("MainActivity", response.toString())
          }
          is MessageResponse.Error -> throw response.throwable
          else -> Unit
        }
      }
      .onCompletion { conversation.history.forEach { Log.d("MainActivity", it.toString()) } }
      .catch { e ->
        Log.e("MainActivity", "Generation error", e)
        generateText.value = getString(R.string.error_generation_failed, e.message ?: "unknown")
      }
      .collect()
  }
}
