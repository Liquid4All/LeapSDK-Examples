package ai.liquid.recipegenerator

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.viewModels
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.lifecycleScope
import kotlinx.coroutines.launch

class MainActivity : ComponentActivity() {
    val viewModel: MainActivityViewModel by viewModels()
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        lifecycleScope.launch {
            viewModel.generateRecipe()
        }
        setContent {
            Scaffold(modifier = Modifier.fillMaxSize()) { innerPadding ->
                Column(Modifier.padding(innerPadding)) {
                    val recipe = viewModel.recipeState
                    if (recipe == null) {
                        Text(
                            text=viewModel.status,
                            modifier=Modifier.align(Alignment.CenterHorizontally).padding(8.dp),
                            fontStyle = FontStyle.Italic,
                            fontSize = 24.sp
                        )
                    } else {
                        RecipeView(recipe, modifier = Modifier.padding(8.dp))
                    }
                }
            }
        }
    }
}

@Composable
fun RecipeView(recipe: Recipe, modifier: Modifier = Modifier) {
    Column(modifier) {
        Text(recipe.name, fontSize = 24.sp)
        Text(
            "Cooking time: ${recipe.cookingTime} min",
            fontSize = 18.sp,
            modifier = Modifier.padding(top = 10.dp)
        )
        Text(
            "Vegetarian: ${
                if (recipe.isVegetarian) {
                    "Yes"
                } else {
                    "No"
                }
            }", fontSize = 18.sp,
            modifier = Modifier.padding(top = 10.dp)
        )
        Text("Ingredients", fontSize = 18.sp, modifier = Modifier.padding(top = 10.dp))
        Column {
            recipe.ingredients.map { item ->
                Text(" $item")
            }
        }
        Text("Steps", fontSize = 18.sp, modifier = Modifier.padding(top = 10.dp))
        Column {
            recipe.steps.map { item ->
                Text(" $item")
            }
        }
    }
}