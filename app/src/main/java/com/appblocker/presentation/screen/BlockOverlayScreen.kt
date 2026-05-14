package com.appblocker.presentation.screen

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import com.appblocker.presentation.theme.AppBlockerTheme

@Composable
fun BlockOverlayScreen(
    state: BlockOverlayUiState,
    onSkipBreath: () -> Unit,
    onLegitimate: () -> Unit,
    onBreakingPlan: () -> Unit,
    onGoBack: () -> Unit,
    onProceed: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Surface(
        modifier = modifier.fillMaxSize(),
        color = MaterialTheme.colorScheme.surfaceContainerLow,
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(horizontal = 32.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center,
        ) {
            Column(
                modifier = Modifier.widthIn(max = 360.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
            ) {
                AnimatedContent(
                    targetState = state,
                    transitionSpec = {
                        fadeIn(animationSpec = tween(250)) togetherWith fadeOut(animationSpec = tween(250))
                    },
                    contentKey = { it::class },
                    label = "blockOverlayState",
                ) { current ->
                    when (current) {
                        is BlockOverlayUiState.BreathingPause -> BreathingPauseContent(
                            appName = current.appName,
                            secondsRemaining = current.secondsRemaining,
                            onSkip = onSkipBreath,
                        )
                        is BlockOverlayUiState.Confirmation -> ConfirmationContent(
                            appName = current.appName,
                            onLegitimate = onLegitimate,
                            onBreakingPlan = onBreakingPlan,
                        )
                        is BlockOverlayUiState.Motivational -> MotivationalContent(
                            appName = current.appName,
                            message = current.message,
                            onGoBack = onGoBack,
                            onProceed = onProceed,
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun BreathingPauseContent(
    appName: String,
    secondsRemaining: Int,
    onSkip: () -> Unit,
) {
    val breathing = rememberInfiniteTransition(label = "breathing")
    val breathingAlpha by breathing.animateFloat(
        initialValue = 0.4f,
        targetValue = 1.0f,
        animationSpec = infiniteRepeatable(
            animation = tween(2000, easing = FastOutSlowInEasing),
            repeatMode = RepeatMode.Reverse,
        ),
        label = "breathingAlpha",
    )

    val animatedProgress by animateFloatAsState(
        targetValue = secondsRemaining / 5f,
        animationSpec = tween(1000, easing = LinearEasing),
        label = "countdownProgress",
    )

    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        Text(
            text = "Take a deep breath.",
            style = MaterialTheme.typography.headlineMedium,
            color = MaterialTheme.colorScheme.onSurface,
            textAlign = TextAlign.Center,
        )

        Spacer(modifier = Modifier.height(8.dp))

        Text(
            text = "$appName will still be there in a moment.",
            style = MaterialTheme.typography.bodyLarge,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            textAlign = TextAlign.Center,
        )

        Spacer(modifier = Modifier.height(40.dp))

        Box(
            contentAlignment = Alignment.Center,
            modifier = Modifier.size(140.dp),
        ) {
            CircularProgressIndicator(
                progress = { animatedProgress },
                modifier = Modifier.fillMaxSize(),
                color = MaterialTheme.colorScheme.primary.copy(alpha = 0.7f),
                trackColor = MaterialTheme.colorScheme.primary.copy(alpha = 0.15f),
                strokeWidth = 6.dp,
            )
            Text(
                text = "${secondsRemaining.coerceAtLeast(1)}",
                style = MaterialTheme.typography.displayLarge,
                color = MaterialTheme.colorScheme.onSurface,
                modifier = Modifier.semantics {
                    liveRegion = LiveRegionMode.Polite
                    contentDescription = "$secondsRemaining seconds remaining"
                },
            )
        }

        Spacer(modifier = Modifier.height(24.dp))

        Text(
            text = "Breathe in… and out.",
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.alpha(breathingAlpha),
        )

        Spacer(modifier = Modifier.height(48.dp))

        TextButton(onClick = onSkip) {
            Text(
                text = "Skip",
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

@Composable
private fun ConfirmationContent(
    appName: String,
    onLegitimate: () -> Unit,
    onBreakingPlan: () -> Unit,
) {
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        Text(
            text = "Opening before your planned time?",
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

        Button(onClick = onLegitimate) {
            Text(text = "No, this is planned")
        }

        Spacer(modifier = Modifier.height(12.dp))

        OutlinedButton(onClick = onBreakingPlan) {
            Text(text = "Yes, I'm breaking my plan")
        }
    }
}

@Composable
private fun MotivationalContent(
    appName: String,
    message: String,
    onGoBack: () -> Unit,
    onProceed: () -> Unit,
) {
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
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
}

@Preview(showBackground = true)
@Composable
private fun BreathingPausePreview() {
    AppBlockerTheme {
        BlockOverlayScreen(
            state = BlockOverlayUiState.BreathingPause(appName = "Instagram", secondsRemaining = 4),
            onSkipBreath = {},
            onLegitimate = {},
            onBreakingPlan = {},
            onGoBack = {},
            onProceed = {},
        )
    }
}

@Preview(showBackground = true, uiMode = android.content.res.Configuration.UI_MODE_NIGHT_YES)
@Composable
private fun BreathingPauseDarkPreview() {
    AppBlockerTheme {
        BlockOverlayScreen(
            state = BlockOverlayUiState.BreathingPause(appName = "Instagram", secondsRemaining = 4),
            onSkipBreath = {},
            onLegitimate = {},
            onBreakingPlan = {},
            onGoBack = {},
            onProceed = {},
        )
    }
}

@Preview(showBackground = true)
@Composable
private fun ConfirmationPreview() {
    AppBlockerTheme {
        BlockOverlayScreen(
            state = BlockOverlayUiState.Confirmation(appName = "Instagram"),
            onSkipBreath = {},
            onLegitimate = {},
            onBreakingPlan = {},
            onGoBack = {},
            onProceed = {},
        )
    }
}

@Preview(showBackground = true, uiMode = android.content.res.Configuration.UI_MODE_NIGHT_YES)
@Composable
private fun ConfirmationDarkPreview() {
    AppBlockerTheme {
        BlockOverlayScreen(
            state = BlockOverlayUiState.Confirmation(appName = "Instagram"),
            onSkipBreath = {},
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
                message = "You've got this. Every moment you resist builds the life you want.",
            ),
            onSkipBreath = {},
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
                message = "You've got this. Every moment you resist builds the life you want.",
            ),
            onSkipBreath = {},
            onLegitimate = {},
            onBreakingPlan = {},
            onGoBack = {},
            onProceed = {},
        )
    }
}
