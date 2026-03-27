package com.appblocker.presentation.screen

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import com.appblocker.presentation.theme.AppBlockerTheme

@Composable
fun BlockOverlayScreen(
    appName: String,
    motivationalMessage: String,
    onGoBack: () -> Unit,
    onProceedAnyway: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Surface(
        modifier = modifier.fillMaxSize(),
        color = MaterialTheme.colorScheme.surface,
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(32.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center,
        ) {
            Text(
                text = "This app is blocked",
                style = MaterialTheme.typography.headlineMedium,
                color = MaterialTheme.colorScheme.onSurface,
            )

            Spacer(modifier = Modifier.height(8.dp))

            Text(
                text = appName,
                style = MaterialTheme.typography.titleMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )

            Spacer(modifier = Modifier.height(32.dp))

            Text(
                text = motivationalMessage,
                style = MaterialTheme.typography.bodyLarge,
                color = MaterialTheme.colorScheme.onSurface,
                textAlign = TextAlign.Center,
            )

            Spacer(modifier = Modifier.height(48.dp))

            Button(onClick = onGoBack) {
                Text(text = "Go Back")
            }

            Spacer(modifier = Modifier.height(16.dp))

            TextButton(onClick = onProceedAnyway) {
                Text(
                    text = "Proceed Anyway",
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
    }
}

@Preview(showBackground = true)
@Composable
private fun BlockOverlayScreenPreview() {
    AppBlockerTheme {
        BlockOverlayScreen(
            appName = "Instagram",
            motivationalMessage = "Stay focused! You have better things to do.",
            onGoBack = {},
            onProceedAnyway = {},
        )
    }
}

@Preview(showBackground = true, uiMode = android.content.res.Configuration.UI_MODE_NIGHT_YES)
@Composable
private fun BlockOverlayScreenDarkPreview() {
    AppBlockerTheme {
        BlockOverlayScreen(
            appName = "Instagram",
            motivationalMessage = "Stay focused! You have better things to do.",
            onGoBack = {},
            onProceedAnyway = {},
        )
    }
}
