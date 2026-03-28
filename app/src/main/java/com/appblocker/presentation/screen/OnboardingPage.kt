package com.appblocker.presentation.screen

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.rotate
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

/**
 * Defines the visual animation type for each onboarding page.
 */
enum class OnboardingVisual {
    PULSE_RING,   // Page 1: pulsing concentric rings — "awareness"
    SHIELD_BLOCK, // Page 2: rotating shield with block line — "protection"
    CLOCK_SWEEP,  // Page 3: sweeping clock arc — "control over time"
}

data class OnboardingPageData(
    val visual: OnboardingVisual,
    val title: String,
    val subtitle: String,
)

@Composable
fun OnboardingPage(
    data: OnboardingPageData,
    isActive: Boolean,
    modifier: Modifier = Modifier,
) {
    // Entry animations — triggered each time the page becomes active
    val visualAlpha = remember { Animatable(0f) }
    val visualScale = remember { Animatable(0.9f) }
    val titleAlpha = remember { Animatable(0f) }
    val titleOffsetY = remember { Animatable(20f) }
    val subtitleAlpha = remember { Animatable(0f) }

    LaunchedEffect(isActive) {
        if (isActive) {
            // Reset
            visualAlpha.snapTo(0f)
            visualScale.snapTo(0.9f)
            titleAlpha.snapTo(0f)
            titleOffsetY.snapTo(20f)
            subtitleAlpha.snapTo(0f)

            // Visual: scale in + fade (0–400ms) — parallel
            launch { visualAlpha.animateTo(1f, tween(400, easing = FastOutSlowInEasing)) }
            launch { visualScale.animateTo(1f, tween(500, easing = FastOutSlowInEasing)) }

            // Title: fade + slide up (delay 150ms, 400ms duration)
            delay(150)
            launch { titleAlpha.animateTo(1f, tween(400, easing = FastOutSlowInEasing)) }
            launch { titleOffsetY.animateTo(0f, tween(400, easing = FastOutSlowInEasing)) }

            // Subtitle: fade in (delay 300ms from title start)
            delay(150)
            subtitleAlpha.animateTo(1f, tween(350, easing = FastOutSlowInEasing))
        }
    }

    Column(
        modifier = modifier
            .fillMaxSize()
            .padding(horizontal = 40.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center,
    ) {
        // Animated visual element
        Box(
            modifier = Modifier
                .size(160.dp)
                .graphicsLayer {
                    alpha = visualAlpha.value
                    scaleX = visualScale.value
                    scaleY = visualScale.value
                },
            contentAlignment = Alignment.Center,
        ) {
            when (data.visual) {
                OnboardingVisual.PULSE_RING -> PulseRingVisual()
                OnboardingVisual.SHIELD_BLOCK -> ShieldBlockVisual()
                OnboardingVisual.CLOCK_SWEEP -> ClockSweepVisual()
            }
        }

        Spacer(modifier = Modifier.height(48.dp))

        // Title with fade + slide
        Text(
            text = data.title,
            style = MaterialTheme.typography.headlineLarge,
            textAlign = TextAlign.Center,
            color = MaterialTheme.colorScheme.onBackground,
            modifier = Modifier.graphicsLayer {
                alpha = titleAlpha.value
                translationY = titleOffsetY.value
            },
        )

        Spacer(modifier = Modifier.height(16.dp))

        // Subtitle with delayed fade
        Text(
            text = data.subtitle,
            style = MaterialTheme.typography.bodyLarge,
            textAlign = TextAlign.Center,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.graphicsLayer {
                alpha = subtitleAlpha.value
            },
        )
    }
}

// ---------------------------------------------------------------------------
// Animated visuals — Canvas-based, looping subtly
// ---------------------------------------------------------------------------

/**
 * Page 1: Concentric rings that pulse outward. Conveys "awareness" / "wake up".
 */
@Composable
private fun PulseRingVisual() {
    val primary = MaterialTheme.colorScheme.primary
    val infinite = rememberInfiniteTransition(label = "pulse")

    val pulse1 by infinite.animateFloat(
        initialValue = 0.6f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(
            animation = tween(2000, easing = LinearEasing),
            repeatMode = RepeatMode.Reverse,
        ),
        label = "ring1",
    )
    val pulse2 by infinite.animateFloat(
        initialValue = 1f,
        targetValue = 0.6f,
        animationSpec = infiniteRepeatable(
            animation = tween(2000, easing = LinearEasing),
            repeatMode = RepeatMode.Reverse,
        ),
        label = "ring2",
    )

    Canvas(modifier = Modifier.size(160.dp)) {
        val center = Offset(size.width / 2, size.height / 2)
        val maxRadius = size.minDimension / 2

        drawRing(center, maxRadius * 0.35f * pulse1, primary.copy(alpha = 0.6f), 3f)
        drawRing(center, maxRadius * 0.6f * pulse2, primary.copy(alpha = 0.35f), 2.5f)
        drawRing(center, maxRadius * 0.85f * pulse1, primary.copy(alpha = 0.15f), 2f)

        // Center dot
        drawCircle(color = primary, radius = 8f, center = center)
    }
}

private fun DrawScope.drawRing(center: Offset, radius: Float, color: Color, width: Float) {
    drawCircle(
        color = color,
        radius = radius,
        center = center,
        style = Stroke(width = width.dp.toPx()),
    )
}

/**
 * Page 2: A circle with a slow-rotating diagonal "block" line. Conveys "blocking".
 */
@Composable
private fun ShieldBlockVisual() {
    val primary = MaterialTheme.colorScheme.primary
    val infinite = rememberInfiniteTransition(label = "shield")

    val rotation by infinite.animateFloat(
        initialValue = 0f,
        targetValue = 360f,
        animationSpec = infiniteRepeatable(
            animation = tween(8000, easing = LinearEasing),
        ),
        label = "rotate",
    )

    Canvas(modifier = Modifier.size(160.dp)) {
        val center = Offset(size.width / 2, size.height / 2)
        val radius = size.minDimension / 2 * 0.7f

        // Outer circle
        drawCircle(
            color = primary.copy(alpha = 0.2f),
            radius = radius,
            center = center,
            style = Stroke(width = 3.dp.toPx()),
        )

        // Rotating block line
        rotate(degrees = rotation, pivot = center) {
            drawLine(
                color = primary,
                start = Offset(center.x - radius * 0.7f, center.y - radius * 0.7f),
                end = Offset(center.x + radius * 0.7f, center.y + radius * 0.7f),
                strokeWidth = 4.dp.toPx(),
                cap = StrokeCap.Round,
            )
        }

        // Inner filled circle
        drawCircle(
            color = primary.copy(alpha = 0.08f),
            radius = radius * 0.5f,
            center = center,
        )
    }
}

/**
 * Page 3: Arc that sweeps around a clock face. Conveys "time control".
 */
@Composable
private fun ClockSweepVisual() {
    val primary = MaterialTheme.colorScheme.primary
    val infinite = rememberInfiniteTransition(label = "clock")

    val sweep by infinite.animateFloat(
        initialValue = 0f,
        targetValue = 360f,
        animationSpec = infiniteRepeatable(
            animation = tween(4000, easing = LinearEasing),
        ),
        label = "sweep",
    )

    Canvas(modifier = Modifier.size(160.dp)) {
        val center = Offset(size.width / 2, size.height / 2)
        val radius = size.minDimension / 2 * 0.7f

        // Background circle
        drawCircle(
            color = primary.copy(alpha = 0.1f),
            radius = radius,
            center = center,
        )

        // Tick marks (12 o'clock positions)
        for (i in 0 until 12) {
            val angle = Math.toRadians((i * 30).toDouble())
            val innerR = radius * 0.82f
            val outerR = radius * 0.95f
            drawLine(
                color = primary.copy(alpha = 0.3f),
                start = Offset(
                    center.x + (innerR * kotlin.math.sin(angle)).toFloat(),
                    center.y - (innerR * kotlin.math.cos(angle)).toFloat(),
                ),
                end = Offset(
                    center.x + (outerR * kotlin.math.sin(angle)).toFloat(),
                    center.y - (outerR * kotlin.math.cos(angle)).toFloat(),
                ),
                strokeWidth = 2.dp.toPx(),
                cap = StrokeCap.Round,
            )
        }

        // Sweeping arc (120° wide, rotating)
        val arcSize = radius * 2
        drawArc(
            color = primary.copy(alpha = 0.35f),
            startAngle = sweep - 90f,
            sweepAngle = 120f,
            useCenter = false,
            topLeft = Offset(center.x - radius, center.y - radius),
            size = Size(arcSize, arcSize),
            style = Stroke(width = 4.dp.toPx(), cap = StrokeCap.Round),
        )

        // Center dot
        drawCircle(color = primary, radius = 5f, center = center)
    }
}
