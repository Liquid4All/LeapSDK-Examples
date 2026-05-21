package ai.liquid.recipegenerator

import ai.liquid.leap.GenerationOptions
import ai.liquid.leap.LeapJson
import ai.liquid.leap.ModelLoadingOptions
import ai.liquid.leap.ModelRunner
import ai.liquid.leap.downloader.LeapModelDownloader
import ai.liquid.leap.downloader.LeapModelDownloaderNotificationConfig
import ai.liquid.leap.message.MessageResponse
import ai.liquid.leap.structuredoutput.Generatable
import ai.liquid.leap.structuredoutput.GeneratableFactory
import ai.liquid.leap.structuredoutput.Guide
import android.content.Context
import android.util.Log
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.collect
import kotlinx.coroutines.flow.onEach
import kotlinx.coroutines.flow.takeWhile
import kotlinx.coroutines.launch
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.jsonObject

@Serializable
@Generatable("A recipe for cooking great dishes")
data class Recipe(
  val name: String,
  @Guide("Ingredients for making the dishes") val ingredients: List<String>,
  @Guide("Cooking time in minutes") val cookingTime: Int,
  @Guide("Whether the meal is vegetarian") val isVegetarian: Boolean,
  @Guide("Steps of cooking") val steps: List<String>,
)

/** UI status for the recipe-generation flow. Resolved to a localized string in the composable. */
sealed interface Status {
  data object NotReady : Status

  data object CheckingModel : Status

  data object StartingDownload : Status

  data class Downloading(val percentage: Int, val downloadedMb: Long, val totalMb: Long) : Status

  data object DownloadComplete : Status

  data object LoadingModel : Status

  data object ModelLoaded : Status

  data object Generating : Status

  data object Success : Status

  data class Error(val message: String, val generatedText: String? = null) : Status
}

class MainActivityViewModel : ViewModel() {
  private var modelRunner: ModelRunner? = null
  var recipeState: Recipe? by mutableStateOf(null)
  var status: Status by mutableStateOf(Status.NotReady)

  private val modelName = "LFM2-700M"
  private val quantType = "Q8_0"
  private var downloader: LeapModelDownloader? = null

  private fun getDownloader(context: Context): LeapModelDownloader {
    if (downloader == null) {
      downloader =
        LeapModelDownloader(
          context,
          notificationConfig =
            LeapModelDownloaderNotificationConfig.build {
              notificationTitleDownloading =
                context.getString(R.string.notification_downloading_model)
              notificationTitleDownloaded = context.getString(R.string.notification_model_ready)
            },
        )
    }
    return downloader!!
  }

  suspend fun loadModel(context: Context) {
    val downloader = getDownloader(context)

    status = Status.CheckingModel
    val currentStatus = downloader.queryStatus(modelName, quantType)

    if (currentStatus is LeapModelDownloader.ModelDownloadStatus.NotOnLocal) {
      status = Status.StartingDownload

      val progressFlow = downloader.observeDownloadProgress(modelName, quantType)

      downloader.requestDownloadModel(modelName, quantType)

      progressFlow
        .onEach { progress ->
          if (progress != null) {
            val downloadedMb = progress.downloadedSizeInBytes / (1024 * 1024)
            val totalMb = progress.totalSizeInBytes / (1024 * 1024)
            val percentage =
              if (progress.totalSizeInBytes > 0) {
                (progress.downloadedSizeInBytes * 100.0 / progress.totalSizeInBytes).toInt()
              } else {
                0
              }
            status = Status.Downloading(percentage, downloadedMb, totalMb)
          } else {
            val downloadStatus = downloader.queryStatus(modelName, quantType)
            if (downloadStatus is LeapModelDownloader.ModelDownloadStatus.Downloaded) {
              status = Status.DownloadComplete
            }
          }
        }
        .takeWhile { progress ->
          progress != null ||
            downloader.queryStatus(modelName, quantType) is
              LeapModelDownloader.ModelDownloadStatus.DownloadInProgress
        }
        .collect()
    }

    status = Status.LoadingModel
    try {
      modelRunner =
        downloader.loadModel(
          modelName = modelName,
          quantizationType = quantType,
          options =
            ModelLoadingOptions(
              cacheOptions =
                ModelLoadingOptions.cacheOptions(
                  path = context.cacheDir.resolve("leap-cache").absolutePath
                )
            ),
        )
      status = Status.ModelLoaded
    } catch (e: Exception) {
      Log.e("RecipeGenerator", "Error loading model", e)
      status = Status.Error(message = e.message ?: "unknown error")
    }
  }

  fun generateRecipe(context: Context) {
    viewModelScope.launch {
      if (modelRunner == null) {
        loadModel(context)
      }
      val runner = checkNotNull(modelRunner)
      status = Status.Generating
      val conversation =
        runner.createConversation(
          "You are a recipe generator bot that reads the user's message and generates JSON output."
        )
      val options = GenerationOptions()
      options.setResponseFormatType<Recipe>()
      Log.d(
        MainActivityViewModel::class.simpleName,
        "Generating recipe with automatic schema injection. Constraint: ${options.jsonSchemaConstraint}",
      )
      val buffer = StringBuilder()
      try {
        conversation
          .generateResponse("A recipe for a dinner dish", options)
          .onEach { response ->
            when (response) {
              is MessageResponse.Chunk -> buffer.append(response.text)
              is MessageResponse.Error -> throw response.throwable
              else -> Unit
            }
          }
          .collect()

        val generatedText = buffer.toString()
        Log.d("RecipeGenerator", "Generated text: $generatedText")

        val recipe =
          GeneratableFactory.createFromJsonObject<Recipe>(
            LeapJson.parseToJsonElement(generatedText).jsonObject
          )
        Log.d("RecipeGenerator", "Successfully parsed recipe: ${recipe.name}")
        recipeState = recipe
        status = Status.Success
      } catch (e: Exception) {
        Log.e("RecipeGenerator", "Error generating recipe", e)
        status =
          Status.Error(message = e.message ?: "unknown error", generatedText = buffer.toString())
      }
    }
  }

  override fun onCleared() {
    super.onCleared()

    // Unload model asynchronously to avoid ANR. Do NOT use runBlocking — it blocks the main
    // thread. viewModelScope is already cancelled at this point, so use a dedicated scope.
    CoroutineScope(Dispatchers.IO).launch {
      try {
        modelRunner?.unload()
      } catch (e: Exception) {
        Log.e("RecipeGenerator", "Error unloading model", e)
      }
    }
  }
}
