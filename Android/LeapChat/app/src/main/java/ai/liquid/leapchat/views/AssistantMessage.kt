package ai.liquid.leapchat.views

import ai.liquid.leapchat.R
import androidx.compose.foundation.Image
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp

@Composable
fun AssistantMessage(
    text: String,
    reasoningText: String?,
) {
    val reasoningText = reasoningText?.trim()
    Row(modifier = Modifier.padding(all = 8.dp).fillMaxWidth(1.0f).testTag("AssistantMessageView"), horizontalArrangement = Arrangement.Absolute.Left) {
        Image(
            painter = painterResource(R.drawable.smart_toy_outline),
            contentDescription = "Assistant icon",
            modifier =
                Modifier.size(36.dp)
                    .border(1.5.dp, MaterialTheme.colorScheme.secondary),
        )
        Spacer(modifier = Modifier.width(8.dp))
        Column(horizontalAlignment = Alignment.Start) {
            Text(text = "Assistant", color = MaterialTheme.colorScheme.secondary, style = MaterialTheme.typography.titleSmall)
            if (!reasoningText.isNullOrEmpty()) {
                Spacer(modifier = Modifier.height(4.dp))
                Text(text = reasoningText, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.secondary)
            }
            Spacer(modifier = Modifier.height(4.dp))
            Text(text = text, style = MaterialTheme.typography.bodyMedium, modifier = Modifier.testTag("AssistantMessageViewText"))
        }
    }
}

@Preview
@Composable
fun AssistantMessagePreview() {
    AssistantMessage("Hello world!", null)
}

@Preview
@Composable
fun AssistantMessageWithReasoningPreview() {
    AssistantMessage("Hello world!", "I need to be friendly")
}
