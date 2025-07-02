package ai.liquid.leapchat.views

import ai.liquid.leapchat.R
import androidx.compose.foundation.Image
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.requiredSize
import androidx.compose.foundation.layout.requiredWidthIn
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp

@Composable
fun UserMessage(text: String) {
    Box {
        Column(
            horizontalAlignment = Alignment.End,
            modifier = Modifier.fillMaxWidth(1.0f).padding(start = 8.dp, end = 54.dp),
        ) {
            Text(
                text = "User",
                color = MaterialTheme.colorScheme.secondary,
                style = MaterialTheme.typography.titleSmall,
            )
            Spacer(modifier = Modifier.height(4.dp))
            Text(text = text, style = MaterialTheme.typography.bodyMedium)
        }
        Row(
            modifier = Modifier.padding(all = 8.dp).fillMaxWidth(1.0f),
            horizontalArrangement = Arrangement.Absolute.Right,
            verticalAlignment = Alignment.Top,
        ) {
            Spacer(modifier = Modifier.width(8.dp).requiredWidthIn(8.dp, 8.dp))
            Image(
                painter = painterResource(R.drawable.baseline_person_outline),
                contentDescription = "User icon",
                modifier =
                    Modifier.size(36.dp).requiredSize(36.dp)
                        .border(1.5.dp, MaterialTheme.colorScheme.primary),
            )
        }
    }
}

@Preview
@Composable
fun UserMessagePreview() {
    UserMessage(
        "Hello world! Hello world! Hello world! Hello world! Very long text is here to explode everything!Hello world! Hello world! Hello world! Hello world! Very long text is here to explode everything! ",
    )
}
