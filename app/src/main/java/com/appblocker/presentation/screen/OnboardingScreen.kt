package com.appblocker.presentation.screen

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import com.appblocker.presentation.theme.AppBlockerTheme
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.drop

private val pages = listOf(
    OnboardingPageData(
        visual = OnboardingVisual.PULSE_RING,
        title = "Take back control",
        subtitle = "Your phone is a tool. Make it work for you — not against you.",
    ),
    OnboardingPageData(
        visual = OnboardingVisual.SHIELD_BLOCK,
        title = "Block what distracts you",
        subtitle = "Choose the apps that steal your focus. We'll keep them out of reach.",
    ),
    OnboardingPageData(
        visual = OnboardingVisual.CLOCK_SWEEP,
        title = "Your rules, your schedule",
        subtitle = "Set time windows or daily limits. Stay in control without going cold turkey.",
    ),
)

@Composable
fun OnboardingScreen(
    onFinish: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val pagerState = rememberPagerState(pageCount = { pages.size })
    val isLastPage = pagerState.currentPage == pages.lastIndex

    // Track whether user has ever swiped — hide hint after first interaction
    var hasInteracted by rememberSaveable { mutableStateOf(false) }

    LaunchedEffect(Unit) {
        snapshotFlow { pagerState.currentPage }
            .drop(1) // ignore initial emission
            .collect { hasInteracted = true }
    }

    // Swipe hint animation (slides in, then fades out)
    val showHint = !hasInteracted && pagerState.currentPage == 0
    val hintAlpha = remember { Animatable(0f) }
    val hintOffsetX = remember { Animatable(0f) }

    LaunchedEffect(showHint) {
        if (showHint) {
            delay(800) // wait for page entry animations
            hintAlpha.animateTo(1f, tween(400))
            // Subtle slide loop
            while (true) {
                hintOffsetX.animateTo(8f, tween(600, easing = FastOutSlowInEasing))
                hintOffsetX.animateTo(0f, tween(600, easing = FastOutSlowInEasing))
                delay(1200)
            }
        } else {
            hintAlpha.animateTo(0f, tween(200))
        }
    }

    Column(
        modifier = modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background)
            .statusBarsPadding()
            .navigationBarsPadding(),
    ) {
        // Skip button row
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 12.dp),
            horizontalArrangement = Arrangement.End,
        ) {
            if (!isLastPage) {
                TextButton(onClick = onFinish) {
                    Text("Skip")
                }
            }
        }

        // Pager content
        HorizontalPager(
            state = pagerState,
            modifier = Modifier.weight(1f),
        ) { page ->
            OnboardingPage(
                data = pages[page],
                isActive = pagerState.settledPage == page,
            )
        }

        // Bottom section: hint + indicators + last-page CTA
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 32.dp)
                .padding(bottom = 32.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            // Swipe hint
            Text(
                text = "Swipe \u2192",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.graphicsLayer {
                    alpha = hintAlpha.value
                    translationX = hintOffsetX.value.dp.toPx()
                },
            )

            Spacer(modifier = Modifier.height(20.dp))

            // Animated page indicators
            Row(
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                repeat(pages.size) { index ->
                    val isSelected = index == pagerState.currentPage

                    val color by animateColorAsState(
                        targetValue = if (isSelected) {
                            MaterialTheme.colorScheme.primary
                        } else {
                            MaterialTheme.colorScheme.outlineVariant
                        },
                        animationSpec = tween(300),
                        label = "dotColor",
                    )

                    val scale by animateFloatAsState(
                        targetValue = if (isSelected) 1f else 0.7f,
                        animationSpec = spring(
                            dampingRatio = Spring.DampingRatioMediumBouncy,
                            stiffness = Spring.StiffnessLow,
                        ),
                        label = "dotScale",
                    )

                    val width by animateFloatAsState(
                        targetValue = if (isSelected) 24f else 8f,
                        animationSpec = tween(300, easing = FastOutSlowInEasing),
                        label = "dotWidth",
                    )

                    Box(
                        modifier = Modifier
                            .height(8.dp)
                            .width(width.dp)
                            .graphicsLayer {
                                scaleY = scale
                            }
                            .clip(CircleShape)
                            .background(color),
                    )
                }
            }

            Spacer(modifier = Modifier.height(32.dp))

            // CTA only on last page — animated entrance
            AnimatedVisibility(
                visible = isLastPage,
                enter = fadeIn(tween(300)) + slideInHorizontally(
                    initialOffsetX = { it / 4 },
                    animationSpec = tween(300, easing = FastOutSlowInEasing),
                ),
                exit = fadeOut(tween(200)) + slideOutHorizontally(
                    targetOffsetX = { -it / 4 },
                    animationSpec = tween(200),
                ),
            ) {
                Button(
                    onClick = onFinish,
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(56.dp),
                    shape = MaterialTheme.shapes.large,
                ) {
                    Text(
                        text = "Start blocking",
                        style = MaterialTheme.typography.titleMedium,
                    )
                }
            }
        }
    }
}

@Preview(showBackground = true, showSystemUi = true)
@Composable
private fun OnboardingScreenPreview() {
    AppBlockerTheme {
        OnboardingScreen(onFinish = {})
    }
}
