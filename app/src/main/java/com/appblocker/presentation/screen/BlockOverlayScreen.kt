package com.appblocker.presentation.screen

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.SizeTransform
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.systemBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.material3.Button
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import com.appblocker.domain.model.BlockReason
import com.appblocker.domain.model.TodayAppStats
import com.appblocker.presentation.theme.AppBlockerTheme
import kotlinx.coroutines.delay

@Composable
fun BlockOverlayScreen(
    state: BlockOverlayUiState,
    reason: BlockReason?,
    graceMinutes: Int,
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
                .systemBarsPadding()
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
                        (fadeIn(animationSpec = tween(durationMillis = 500, delayMillis = 300)) togetherWith
                            fadeOut(animationSpec = tween(durationMillis = 300)))
                            .using(SizeTransform(clip = false) { _, _ -> tween(0) })
                    },
                    contentAlignment = Alignment.Center,
                    contentKey = { it::class },
                    label = "blockOverlayState",
                ) { current ->
                    when (current) {
                        is BlockOverlayUiState.Reflection -> ReflectionContent(
                            state = current,
                            reason = reason,
                            graceMinutes = graceMinutes,
                            onLegitimate = onLegitimate,
                            onBreakingPlan = onBreakingPlan,
                        )
                        is BlockOverlayUiState.Motivational -> MotivationalContent(
                            message = current.message,
                            stats = current.stats,
                            graceMinutes = graceMinutes,
                            onGoBack = onGoBack,
                            onProceed = onProceed,
                        )
                    }
                }
            }
        }
    }
}

/**
 * Today's attempts, then today's screen time, then (once the pause runs out)
 * the choice. The big button never opens the app: tapping through on reflex
 * lands on "I'm breaking my plan" and then "Stick to my plan".
 */
@Composable
private fun ReflectionContent(
    state: BlockOverlayUiState.Reflection,
    reason: BlockReason?,
    graceMinutes: Int,
    onLegitimate: () -> Unit,
    onBreakingPlan: () -> Unit,
) {
    val step = state.step
    val stats = state.stats
    // Back from the motivational step everything is already shown; don't replay it.
    val animate = remember { step != ReflectionStep.DECIDE }
    val decide = step == ReflectionStep.DECIDE

    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        Reveal(visible = step >= ReflectionStep.ATTEMPTS) {
            if (stats != null) {
                AttemptsLine(attempt = stats.attempt, appName = state.appName)
            } else {
                Text(
                    text = "Take a moment before you decide.",
                    style = MaterialTheme.typography.headlineSmall,
                    color = MaterialTheme.colorScheme.onSurface,
                    textAlign = TextAlign.Center,
                )
            }
        }

        if (stats != null) {
            Spacer(modifier = Modifier.height(32.dp))
            Reveal(visible = step >= ReflectionStep.USAGE) {
                UsageLine(
                    usageMs = stats.usageMs,
                    appName = state.appName,
                    countUp = animate,
                    start = step >= ReflectionStep.USAGE,
                )
            }
        }

        if (reason != null) {
            Spacer(modifier = Modifier.height(24.dp))
            Reveal(visible = step >= ReflectionStep.USAGE) {
                ReasonPill(reason = reason)
            }
        }

        Spacer(modifier = Modifier.height(48.dp))

        Box(contentAlignment = Alignment.TopCenter) {
            val choicesAlpha by animateFloatAsState(
                targetValue = if (decide) 1f else 0f,
                animationSpec = tween(durationMillis = 600),
                label = "choicesAlpha",
            )
            Column(
                horizontalAlignment = Alignment.CenterHorizontally,
                modifier = Modifier.alpha(choicesAlpha),
            ) {
                Text(
                    text = confirmationQuestion(reason),
                    style = MaterialTheme.typography.titleLarge,
                    color = MaterialTheme.colorScheme.onSurface,
                    textAlign = TextAlign.Center,
                )
                Spacer(modifier = Modifier.height(24.dp))
                Button(
                    onClick = onBreakingPlan,
                    enabled = decide,
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    Text(text = "I'm breaking my plan")
                }
                Spacer(modifier = Modifier.height(8.dp))
                TextButton(onClick = onLegitimate, enabled = decide) {
                    Text(
                        text = "This is planned",
                        color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = DIM_ALPHA),
                    )
                }
                Text(
                    text = "Opening gives you $graceMinutes minutes before the next check-in.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = DIM_ALPHA),
                    textAlign = TextAlign.Center,
                )
            }
            if (step == ReflectionStep.ATTEMPTS || step == ReflectionStep.USAGE) {
                PauseProgress(pauseSeconds = state.pauseSeconds)
            }
        }
    }
}

@Composable
private fun AttemptsLine(attempt: Int, appName: String) {
    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        modifier = Modifier.semantics(mergeDescendants = true) { liveRegion = LiveRegionMode.Polite },
    ) {
        Text(
            text = ordinal(attempt),
            style = MaterialTheme.typography.displayLarge,
            fontWeight = FontWeight.Bold,
            color = MaterialTheme.colorScheme.primary,
        )
        Text(
            text = "attempt to open $appName today",
            style = MaterialTheme.typography.bodyLarge,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            textAlign = TextAlign.Center,
        )
    }
}

/** Counts today's screen time up from zero, so the eye follows it. */
@Composable
private fun UsageLine(usageMs: Long, appName: String, countUp: Boolean, start: Boolean) {
    val shown = remember { Animatable(if (countUp) 0f else usageMs.toFloat()) }
    LaunchedEffect(start) {
        if (start) {
            shown.animateTo(
                targetValue = usageMs.toFloat(),
                animationSpec = tween(durationMillis = COUNT_UP_MS, easing = FastOutSlowInEasing),
            )
        }
    }
    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        modifier = Modifier.semantics(mergeDescendants = true) { liveRegion = LiveRegionMode.Polite },
    ) {
        if (usageMs < 60_000L) {
            Text(
                text = "No time in $appName yet today.",
                style = MaterialTheme.typography.headlineSmall,
                color = MaterialTheme.colorScheme.onSurface,
                textAlign = TextAlign.Center,
            )
            Text(
                text = "Keep it that way.",
                style = MaterialTheme.typography.bodyLarge,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        } else {
            Text(
                text = formatUsage(shown.value.toLong()),
                style = MaterialTheme.typography.displaySmall,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.onSurface,
            )
            Text(
                text = "already spent in $appName today",
                style = MaterialTheme.typography.bodyLarge,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                textAlign = TextAlign.Center,
            )
        }
    }
}

/** Fades and lifts [content] in when [visible] turns true; keeps its space before that. */
@Composable
private fun Reveal(visible: Boolean, content: @Composable () -> Unit) {
    val progress by animateFloatAsState(
        targetValue = if (visible) 1f else 0f,
        animationSpec = tween(durationMillis = 700, easing = FastOutSlowInEasing),
        label = "reveal",
    )
    Box(
        modifier = Modifier.graphicsLayer {
            alpha = progress
            translationY = (1f - progress) * 24.dp.toPx()
        },
    ) {
        content()
    }
}

/** A thin bar that fills over the pause, so the wait reads as deliberate. */
@Composable
private fun PauseProgress(pauseSeconds: Int) {
    val progress = remember { Animatable(0f) }
    LaunchedEffect(pauseSeconds) {
        progress.animateTo(
            targetValue = 1f,
            animationSpec = tween(durationMillis = pauseSeconds * 1000, easing = LinearEasing),
        )
    }
    LinearProgressIndicator(
        progress = { progress.value },
        modifier = Modifier.width(120.dp),
        color = MaterialTheme.colorScheme.primary.copy(alpha = 0.6f),
        trackColor = MaterialTheme.colorScheme.primary.copy(alpha = 0.15f),
        drawStopIndicator = {},
    )
}

@Composable
private fun ReasonPill(reason: BlockReason) {
    Surface(
        shape = MaterialTheme.shapes.extraLarge,
        color = MaterialTheme.colorScheme.secondaryContainer,
        contentColor = MaterialTheme.colorScheme.onSecondaryContainer,
    ) {
        Text(
            text = describeBlockReason(reason),
            style = MaterialTheme.typography.labelLarge,
            textAlign = TextAlign.Center,
            modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp),
        )
    }
}

/** One-line explanation of why the overlay appeared. */
internal fun describeBlockReason(reason: BlockReason): String = when (reason) {
    BlockReason.FocusSession -> "Focus session in progress"
    BlockReason.AlwaysBlocked -> "On your block list"
    is BlockReason.ScheduledWindow ->
        reason.until?.let { "Scheduled block until $it" } ?: "Scheduled block all day"
    is BlockReason.DailyLimitReached ->
        "Daily limit of ${formatMinutesLabel(reason.limitMinutes)} reached"
}

/** "1st", "2nd", "3rd", "4th", "11th", "22nd". */
internal fun ordinal(n: Int): String {
    val suffix = when {
        n % 100 in 11..13 -> "th"
        n % 10 == 1 -> "st"
        n % 10 == 2 -> "nd"
        n % 10 == 3 -> "rd"
        else -> "th"
    }
    return "$n$suffix"
}

// The two answers ("I'm breaking my plan" / "This is planned") are statements,
// not yes/no replies, so every question below reads correctly with them.
private fun confirmationQuestion(reason: BlockReason?): String = when (reason) {
    BlockReason.FocusSession -> "You're in a focus session. Is this planned?"
    is BlockReason.DailyLimitReached -> "You've used today's time. Is this planned?"
    is BlockReason.ScheduledWindow -> "Opening before your planned time?"
    BlockReason.AlwaysBlocked, null -> "Is this a planned visit?"
}

@Composable
private fun TypewriterText(
    text: String,
    style: TextStyle,
    color: Color,
    modifier: Modifier = Modifier,
    textAlign: TextAlign? = null,
    delayPerCharMs: Long = 35L,
    start: Boolean = true,
    onComplete: () -> Unit = {},
) {
    var visibleChars by remember(text) { mutableStateOf(0) }
    LaunchedEffect(text, start) {
        if (!start) return@LaunchedEffect
        for (i in 1..text.length) {
            visibleChars = i
            delay(delayPerCharMs)
        }
        onComplete()
    }
    val annotated = buildAnnotatedString {
        withStyle(SpanStyle(color = color)) {
            append(text.take(visibleChars))
        }
        if (visibleChars < text.length) {
            withStyle(SpanStyle(color = Color.Transparent)) {
                append(text.substring(visibleChars))
            }
        }
    }
    Text(
        text = annotated,
        style = style,
        textAlign = textAlign,
        modifier = modifier,
    )
}

/**
 * The last stop before opening the app. "Stick to my plan" sits where
 * "I'm breaking my plan" was, so a reflexive second tap goes home.
 */
@Composable
private fun MotivationalContent(
    message: String,
    stats: TodayAppStats?,
    graceMinutes: Int,
    onGoBack: () -> Unit,
    onProceed: () -> Unit,
) {
    var titleDone by remember { mutableStateOf(false) }
    var messageDone by remember { mutableStateOf(false) }
    val buttonsAlpha by animateFloatAsState(
        targetValue = if (messageDone) 1f else 0f,
        animationSpec = tween(durationMillis = 500, delayMillis = 200),
        label = "motivationalButtonsAlpha",
    )

    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        TypewriterText(
            text = "You're about to break your plan.",
            style = MaterialTheme.typography.headlineMedium,
            color = MaterialTheme.colorScheme.onSurface,
            textAlign = TextAlign.Center,
            onComplete = { titleDone = true },
        )

        Spacer(modifier = Modifier.height(32.dp))

        TypewriterText(
            text = "“$message”",
            style = MaterialTheme.typography.titleLarge,
            color = MaterialTheme.colorScheme.primary,
            textAlign = TextAlign.Center,
            delayPerCharMs = 45L,
            start = titleDone,
            onComplete = { messageDone = true },
        )

        if (stats != null) {
            Spacer(modifier = Modifier.height(24.dp))
            Text(
                text = "${ordinal(stats.attempt)} attempt today · ${formatUsage(stats.usageMs)} already spent",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                textAlign = TextAlign.Center,
                modifier = Modifier.alpha(buttonsAlpha),
            )
        }

        Spacer(modifier = Modifier.height(48.dp))

        Button(
            onClick = onGoBack,
            enabled = messageDone,
            modifier = Modifier
                .fillMaxWidth()
                .alpha(buttonsAlpha),
        ) {
            Text(text = "Stick to my plan")
        }

        Spacer(modifier = Modifier.height(8.dp))

        TextButton(
            onClick = onProceed,
            enabled = messageDone,
            modifier = Modifier.alpha(buttonsAlpha),
        ) {
            Text(
                text = "Open anyway for $graceMinutes min",
                color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = DIM_ALPHA),
            )
        }
    }
}

private const val DIM_ALPHA = 0.6f
private const val COUNT_UP_MS = 1_500

private val previewStats = TodayAppStats(attempt = 7, usageMs = 134 * 60_000L)

@Preview(showBackground = true, uiMode = android.content.res.Configuration.UI_MODE_NIGHT_YES)
@Composable
private fun ReflectionPausePreview() {
    AppBlockerTheme {
        BlockOverlayScreen(
            state = BlockOverlayUiState.Reflection(
                appName = "Instagram",
                stats = previewStats,
                step = ReflectionStep.USAGE,
                pauseSeconds = 21,
            ),
            reason = BlockReason.ScheduledWindow(until = "17:00"),
            graceMinutes = 5,
            onLegitimate = {},
            onBreakingPlan = {},
            onGoBack = {},
            onProceed = {},
        )
    }
}

@Preview(showBackground = true, uiMode = android.content.res.Configuration.UI_MODE_NIGHT_YES)
@Composable
private fun ReflectionDecidePreview() {
    AppBlockerTheme {
        BlockOverlayScreen(
            state = BlockOverlayUiState.Reflection(
                appName = "Instagram",
                stats = previewStats,
                step = ReflectionStep.DECIDE,
                pauseSeconds = 21,
            ),
            reason = BlockReason.DailyLimitReached(limitMinutes = 30),
            graceMinutes = 5,
            onLegitimate = {},
            onBreakingPlan = {},
            onGoBack = {},
            onProceed = {},
        )
    }
}

@Preview(showBackground = true, uiMode = android.content.res.Configuration.UI_MODE_NIGHT_YES)
@Composable
private fun MotivationalPreview() {
    AppBlockerTheme {
        BlockOverlayScreen(
            state = BlockOverlayUiState.Motivational(
                appName = "Instagram",
                message = "You set this limit for a reason. Future you will be glad you kept it.",
                stats = previewStats,
            ),
            reason = BlockReason.FocusSession,
            graceMinutes = 5,
            onLegitimate = {},
            onBreakingPlan = {},
            onGoBack = {},
            onProceed = {},
        )
    }
}
