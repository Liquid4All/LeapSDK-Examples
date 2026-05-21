package ai.liquid.recipegenerator

import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.activity.viewModels
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.ContextCompat

class MainActivity : ComponentActivity() {
  val viewModel: MainActivityViewModel by viewModels()

  private val requestPermissionLauncher =
    registerForActivityResult(ActivityResultContracts.RequestPermission()) { _ ->
      // Permission outcome doesn't affect generation; the downloader can run without it,
      // it just won't surface progress in the notification tray.
      startGeneration()
    }

  override fun onCreate(savedInstanceState: Bundle?) {
    super.onCreate(savedInstanceState)
    enableEdgeToEdge()

    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
      when {
        ContextCompat.checkSelfPermission(this, android.Manifest.permission.POST_NOTIFICATIONS) ==
          PackageManager.PERMISSION_GRANTED -> startGeneration()
        else -> requestPermissionLauncher.launch(android.Manifest.permission.POST_NOTIFICATIONS)
      }
    } else {
      startGeneration()
    }

    setContent {
      Scaffold(modifier = Modifier.fillMaxSize()) { innerPadding ->
        val recipe = viewModel.recipeState
        if (recipe == null) {
          Column(
            modifier = Modifier.padding(innerPadding).fillMaxSize(),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center,
          ) {
            Text(
              text = statusText(viewModel.status),
              fontStyle = FontStyle.Italic,
              fontSize = 24.sp,
              textAlign = TextAlign.Center,
            )
          }
        } else {
          Column(Modifier.padding(innerPadding)) {
            RecipeView(recipe, modifier = Modifier.padding(8.dp))
          }
        }
      }
    }
  }

  private fun startGeneration() {
    viewModel.generateRecipe(this)
  }
}

/** Resolves a [Status] to a user-facing localized string. */
@Composable
private fun statusText(status: Status): String =
  when (status) {
    is Status.NotReady -> stringResource(R.string.status_not_ready)
    is Status.CheckingModel -> stringResource(R.string.status_checking_model)
    is Status.StartingDownload -> stringResource(R.string.status_starting_download)
    is Status.Downloading ->
      stringResource(
        R.string.status_downloading,
        status.percentage,
        status.downloadedMb,
        status.totalMb,
      )
    is Status.DownloadComplete -> stringResource(R.string.status_download_complete)
    is Status.LoadingModel -> stringResource(R.string.status_loading_model)
    is Status.ModelLoaded -> stringResource(R.string.status_model_loaded)
    is Status.Generating -> stringResource(R.string.status_generating)
    is Status.Success -> stringResource(R.string.status_success)
    is Status.Error ->
      if (status.generatedText.isNullOrEmpty()) {
        stringResource(R.string.status_error, status.message)
      } else {
        stringResource(R.string.status_error_with_text, status.message, status.generatedText)
      }
  }

@Composable
fun RecipeView(recipe: Recipe, modifier: Modifier = Modifier) {
  Column(modifier) {
    Text(recipe.name, fontSize = 24.sp)
    Text(
      stringResource(R.string.recipe_cooking_time, recipe.cookingTime),
      fontSize = 18.sp,
      modifier = Modifier.padding(top = 10.dp),
    )
    Text(
      if (recipe.isVegetarian) stringResource(R.string.recipe_vegetarian_yes)
      else stringResource(R.string.recipe_vegetarian_no),
      fontSize = 18.sp,
      modifier = Modifier.padding(top = 10.dp),
    )
    Text(
      stringResource(R.string.recipe_ingredients_header),
      fontSize = 18.sp,
      modifier = Modifier.padding(top = 10.dp),
    )
    Column { recipe.ingredients.map { item -> Text(" $item") } }
    Text(
      stringResource(R.string.recipe_steps_header),
      fontSize = 18.sp,
      modifier = Modifier.padding(top = 10.dp),
    )
    Column { recipe.steps.map { item -> Text(" $item") } }
  }
}
