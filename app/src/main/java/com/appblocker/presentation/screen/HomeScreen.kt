package com.appblocker.presentation.screen

import android.graphics.drawable.Drawable
import androidx.compose.foundation.Image
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Security
import androidx.compose.material.icons.outlined.BarChart
import androidx.compose.material.icons.outlined.ChevronRight
import androidx.compose.material.icons.outlined.Schedule
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ElevatedCard
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedCard
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.core.graphics.drawable.toBitmap
import com.appblocker.domain.model.BlockedApp
import com.appblocker.presentation.theme.AppBlockerTheme

@Composable
fun HomeScreen(
    blockedApps: List<BlockedApp>,
    isAccessibilityEnabled: Boolean,
    onNavigateToApps: () -> Unit,
    onNavigateToSchedules: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Column(
        modifier = modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 16.dp, vertical = 16.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        // Hero card with blocked count + app icons
        HeroSummaryCard(
            blockedApps = blockedApps,
            isAccessibilityEnabled = isAccessibilityEnabled,
            onClick = onNavigateToApps,
        )

        // Feature cards
        FeatureCard(
            icon = Icons.Outlined.Schedule,
            title = "Schedules",
            description = "Set time windows and daily limits",
            onClick = onNavigateToSchedules,
        )

        FeatureCard(
            icon = Icons.Outlined.BarChart,
            title = "Activity",
            description = "Track your screen time and habits",
            onClick = { /* placeholder */ },
        )
    }
}

@Composable
private fun HeroSummaryCard(
    blockedApps: List<BlockedApp>,
    isAccessibilityEnabled: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    ElevatedCard(
        onClick = onClick,
        modifier = modifier.fillMaxWidth(),
        colors = CardDefaults.elevatedCardColors(
            containerColor = MaterialTheme.colorScheme.primaryContainer,
        ),
        elevation = CardDefaults.elevatedCardElevation(
            defaultElevation = 4.dp,
        ),
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(24.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Icon(
                imageVector = Icons.Filled.Security,
                contentDescription = null,
                modifier = Modifier.size(48.dp),
                tint = MaterialTheme.colorScheme.onPrimaryContainer,
            )

            Spacer(modifier = Modifier.height(12.dp))

            Text(
                text = "${blockedApps.size} app${if (blockedApps.size != 1) "s" else ""} blocked",
                style = MaterialTheme.typography.headlineMedium,
                color = MaterialTheme.colorScheme.onPrimaryContainer,
            )

            Spacer(modifier = Modifier.height(4.dp))

            Text(
                text = if (isAccessibilityEnabled) "Blocking is active" else "Blocking is inactive",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onPrimaryContainer,
            )

            // Show up to 3 app icons in a row
            if (blockedApps.isNotEmpty()) {
                Spacer(modifier = Modifier.height(16.dp))
                BlockedAppIcons(
                    apps = blockedApps.take(3),
                    totalCount = blockedApps.size,
                )
            }
        }
    }
}

@Composable
private fun BlockedAppIcons(
    apps: List<BlockedApp>,
    totalCount: Int,
    modifier: Modifier = Modifier,
) {
    Row(
        modifier = modifier,
        horizontalArrangement = Arrangement.Center,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        // Overlapping icons
        apps.forEachIndexed { index, app ->
            val iconBitmap = rememberAppIcon(app.packageName)
            Box(
                modifier = Modifier
                    .offset(x = (-8 * index).dp)
            ) {
                if (iconBitmap != null) {
                    Image(
                        bitmap = iconBitmap,
                        contentDescription = "${app.appName} icon",
                        modifier = Modifier
                            .size(36.dp)
                            .clip(CircleShape),
                    )
                } else {
                    Box(modifier = Modifier.size(36.dp))
                }
            }
        }

        if (totalCount > 3) {
            Text(
                text = "+${totalCount - 3}",
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.onPrimaryContainer,
                modifier = Modifier.offset(x = (-8 * apps.size).dp),
            )
        }
    }
}

@Composable
private fun FeatureCard(
    icon: ImageVector,
    title: String,
    description: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    OutlinedCard(
        modifier = modifier
            .fillMaxWidth()
            .clickable(onClick = onClick),
        colors = CardDefaults.outlinedCardColors(
            containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.4f),
        ),
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(20.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Icon(
                imageVector = icon,
                contentDescription = null,
                modifier = Modifier.size(32.dp),
                tint = MaterialTheme.colorScheme.primary,
            )

            Spacer(modifier = Modifier.width(16.dp))

            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = title,
                    style = MaterialTheme.typography.titleMedium,
                    color = MaterialTheme.colorScheme.onSurface,
                )
                Text(
                    text = description,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }

            Icon(
                imageVector = Icons.Outlined.ChevronRight,
                contentDescription = null,
                modifier = Modifier.size(20.dp),
                tint = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

@Composable
private fun rememberAppIcon(packageName: String): ImageBitmap? {
    val context = LocalContext.current
    return remember(packageName) {
        try {
            val drawable: Drawable = context.packageManager.getApplicationIcon(packageName)
            drawable.toBitmap(width = 96, height = 96).asImageBitmap()
        } catch (_: Exception) {
            null
        }
    }
}

// region Previews

private val sampleApps = listOf(
    BlockedApp("com.example.social", "Social Media", true, System.currentTimeMillis()),
    BlockedApp("com.example.video", "Video Player", true, System.currentTimeMillis()),
    BlockedApp("com.example.game", "Fun Game", false, System.currentTimeMillis()),
)

@Preview(showBackground = true, showSystemUi = true)
@Composable
private fun HomeScreenPreview() {
    AppBlockerTheme {
        HomeScreen(
            blockedApps = sampleApps,
            isAccessibilityEnabled = true,
            onNavigateToApps = {},
            onNavigateToSchedules = {},
        )
    }
}

@Preview(showBackground = true, showSystemUi = true)
@Composable
private fun HomeScreenEmptyPreview() {
    AppBlockerTheme {
        HomeScreen(
            blockedApps = emptyList(),
            isAccessibilityEnabled = false,
            onNavigateToApps = {},
            onNavigateToSchedules = {},
        )
    }
}

@Preview(showBackground = true)
@Composable
private fun FeatureCardPreview() {
    AppBlockerTheme {
        FeatureCard(
            icon = Icons.Outlined.Schedule,
            title = "Schedules",
            description = "Set time windows and daily limits",
            onClick = {},
            modifier = Modifier.padding(16.dp),
        )
    }
}

// endregion
