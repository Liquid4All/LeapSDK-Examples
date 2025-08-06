package ai.liquid.recipegenerator

import ai.liquid.leap.LeapClient
import ai.liquid.leap.ModelRunner
import ai.liquid.leap.GenerationOptions
import ai.liquid.leap.message.MessageResponse
import ai.liquid.leap.structuredoutput.Generatable
import ai.liquid.leap.structuredoutput.GeneratableFactory
import ai.liquid.leap.structuredoutput.Guide
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.ViewModel
import kotlinx.coroutines.flow.collect
import kotlinx.coroutines.flow.onEach
import org.json.JSONObject


@Generatable("A recipe for cooking great dishes")
data class Recipe(
    val name: String,

    @Guide("Ingredients for making the dishes")
    val ingredients: List<String>,

    @Guide("Cooking time in minutes")
    val cookingTime: Int,

    @Guide("Whether the meal is vegetarian")
    val isVegetarian: Boolean,

    @Guide("Steps of cooking")
    val steps: List<String>,

)

class MainActivityViewModel: ViewModel() {
    private var modelRunner: ModelRunner? = null
    var recipeState: Recipe? by mutableStateOf(null)
    var status: String by mutableStateOf("Not ready to generate")

    suspend fun loadModel() {
        status = "Loading the model..."
        modelRunner = LeapClient.loadModel(
            "/data/local/tmp/liquid/LFM2-700M-8da4w_output_8da8w-seq_4096.bundle"
        )
        status = "Model is loaded"
    }

    suspend fun generateRecipe() {
        if (modelRunner == null) {
            loadModel()
        }
        val modelRunner = checkNotNull(modelRunner)
        status = "Generating the recipe..."
        val conversation = modelRunner.createConversation(
            "You know a lot in cooking. Generate a recipe based on user's requirements"
        )
        val options = GenerationOptions()
        options.setResponseFormatType(Recipe::class)

        val buffer = StringBuilder()
        conversation.generateResponse("A recipe for a dinner dish", options)
            .onEach { response ->
                when (response) {
                    is MessageResponse.Chunk -> {
                        buffer.append(response.text)
                    }

                    else -> {
                        // ignore
                    }
                }
            }.collect()
        recipeState = GeneratableFactory.createFromJSONObject(JSONObject(buffer.toString()))
    }
}