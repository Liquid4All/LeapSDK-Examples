package ai.liquid.leapchat

import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertTextContains
import androidx.compose.ui.test.hasTestTag
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.isDisplayed
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onAllNodesWithTag
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performTextInput
import androidx.test.ext.junit.runners.AndroidJUnit4
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class MainActivityE2EAssetTests {

    @get:Rule
    val composeTestRule = createAndroidComposeRule<MainActivity>()


    @OptIn(ExperimentalTestApi::class)
    @Test
    fun testEndToEndChat() {
        val modelLoadingIndicatorMatcher = hasTestTag("ModelLoadingIndicator")
        val inputBoxMatcher = hasTestTag("InputBox")
        val sendButtonMatcher = hasText("Send")

        // Wait for the model to be downloaded and loaded
        composeTestRule.onNode(modelLoadingIndicatorMatcher).assertIsDisplayed()
        composeTestRule.waitUntilDoesNotExist(
            modelLoadingIndicatorMatcher,
            timeoutMillis = MODEL_LOADING_TIMEOUT
        )
        composeTestRule.waitUntilAtLeastOneExists(sendButtonMatcher, timeoutMillis = 5000L)

        // Send an input to the model
        composeTestRule.onNode(inputBoxMatcher)
            .performTextInput("How many 'r' are there in the word 'strawberry'?")
        composeTestRule.onNode(sendButtonMatcher).performClick()
        composeTestRule.waitUntilAtLeastOneExists(
            hasTestTag("AssistantMessageView"),
            timeoutMillis = 5000L
        )
        composeTestRule.waitUntil(timeoutMillis = 5000L) {
            composeTestRule.onNode(hasTestTag("AssistantMessageViewText").and(hasText("strawberry", substring = true)))
                .isDisplayed()
        }


        // Continue the chat with a second prompt
        composeTestRule.onNode(inputBoxMatcher).performTextInput("What about letter 'a'?")
        composeTestRule.onNode(sendButtonMatcher).performClick()
        composeTestRule.waitUntil(timeoutMillis = 5000L) {
            composeTestRule.onAllNodesWithTag("AssistantMessageView")
                .fetchSemanticsNodes().size == 2
        }
    }

    companion object {
        const val MODEL_LOADING_TIMEOUT = 5L * 60L * 1000L
    }
}