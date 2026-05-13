package com.appblocker.presentation.screen

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
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
    state: BlockOverlayUiState,
    onLegitimate: () -> Unit,
    onBreakingPlan: () -> Unit,
    onGoBack: () -> Unit,
    onProceed: () -> Unit,
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
            when (state) {
                is BlockOverlayUiState.Confirmation -> ConfirmationContent(
                    appName = state.appName,
                    onLegitimate = onLegitimate,
                    onBreakingPlan = onBreakingPlan
                )
                is BlockOverlayUiState.Motivational -> MotivationalContent(
                    appName = state.appName,
                    message = state.message,
                    onGoBack = onGoBack,
                    onProceed = onProceed
                )
            }
        }
    }
}

@Composable
private fun ConfirmationContent(
    appName: String,
    onLegitimate: () -> Unit,
    onBreakingPlan: () -> Unit
) {
    Text(
        text = "Are you disabling this before your planned time?",
        style = MaterialTheme.typography.headlineMedium,
        color = MaterialTheme.colorScheme.onSurface,
        textAlign = TextAlign.Center,
    )

    Spacer(modifier = Modifier.height(8.dp))

    Text(
        text = appName,
        style = MaterialTheme.typography.titleMedium,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
    )

    Spacer(modifier = Modifier.height(48.dp))

    Button(onClick = onBreakingPlan) {
        Text(text = "Yes")
    }

    Spacer(modifier = Modifier.height(12.dp))

    OutlinedButton(onClick = onLegitimate) {
        Text(text = "No")
    }
}

@Composable
private fun MotivationalContent(
    appName: String,
    message: String,
    onGoBack: () -> Unit,
    onProceed: () -> Unit
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
        text = message,
        style = MaterialTheme.typography.bodyLarge,
        color = MaterialTheme.colorScheme.onSurface,
        textAlign = TextAlign.Center,
    )

    Spacer(modifier = Modifier.height(48.dp))

    Button(onClick = onGoBack) {
        Text(text = "Go Back")
    }

    Spacer(modifier = Modifier.height(16.dp))

    TextButton(onClick = onProceed) {
        Text(
            text = "Proceed Anyway",
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

@Preview(showBackground = true)
@Composable
private fun ConfirmationPreview() {
    AppBlockerTheme {
        BlockOverlayScreen(
            state = BlockOverlayUiState.Confirmation(appName = "Instagram"),
            onLegitimate = {},
            onBreakingPlan = {},
            onGoBack = {},
            onProceed = {},
        )
    }
}

@Preview(showBackground = true)
@Composable
private fun MotivationalPreview() {
    AppBlockerTheme {
        BlockOverlayScreen(
            state = BlockOverlayUiState.Motivational(
                appName = "Instagram",
                message = "Stay focused! You have better things to do."
            ),
            onLegitimate = {},
            onBreakingPlan = {},
            onGoBack = {},
            onProceed = {},
        )
    }
}

@Preview(showBackground = true, uiMode = android.content.res.Configuration.UI_MODE_NIGHT_YES)
@Composable
private fun MotivationalDarkPreview() {
    AppBlockerTheme {
        BlockOverlayScreen(
            state = BlockOverlayUiState.Motivational(
                appName = "Instagram",
                message = "Stay focused! You have better things to do."
            ),
            onLegitimate = {},
            onBreakingPlan = {},
            onGoBack = {},
            onProceed = {},
        )
    }
}
