package ai.liquid.leapchat.views

import ai.liquid.leap.message.ChatMessage
import ai.liquid.leapchat.models.ChatMessageDisplayItem
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.ui.Modifier
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp

@Composable
fun ChatHistory(
    history: List<ChatMessageDisplayItem>,
    modifier: Modifier = Modifier,
) {
    val scrollState = rememberLazyListState()
    LaunchedEffect(history) {
        scrollState.animateScrollToItem(history.count())
    }
    LazyColumn(modifier = modifier, state = scrollState) {
        items(history) { message ->
            Box(modifier = Modifier.padding(vertical = 8.dp)) {
                when (message.role) {
                    ChatMessage.Role.USER -> {
                        UserMessage(message.text)
                    }

                    ChatMessage.Role.ASSISTANT -> {
                        AssistantMessage(message.text, message.reasoning)
                    }

                    else -> {}
                }
            }
        }
        item {
            Spacer(modifier = Modifier.width(8.dp))
        }
    }
}

@Preview
@Composable
fun ChatHistoryPreview() {
    ChatHistory(
        listOf(
            ChatMessageDisplayItem(ChatMessage.Role.USER, text = "Hello robot!", reasoning = null),
            ChatMessageDisplayItem(ChatMessage.Role.ASSISTANT, text = "Hello user!", reasoning = "I should be friendly"),
            ChatMessageDisplayItem(ChatMessage.Role.USER, text = "Are you really a robot or a human?", reasoning = null),
            ChatMessageDisplayItem(ChatMessage.Role.ASSISTANT, text = "I am a language model.", reasoning = "I should be accurate"),
        ),
    )
}
