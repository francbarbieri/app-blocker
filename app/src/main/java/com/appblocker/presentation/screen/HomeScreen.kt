package com.appblocker.presentation.screen

import android.graphics.drawable.Drawable
import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Security
import androidx.compose.material.icons.outlined.ChevronRight
import androidx.compose.material3.ElevatedCard
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.core.graphics.drawable.toBitmap
import com.appblocker.domain.model.BlockedApp
import com.appblocker.presentation.theme.AppBlockerTheme

/**
 * Dashboard home screen showing a summary of blocking status,
 * quick actions, and a preview of blocked apps.
 */
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
        verticalArrangement = Arrangement.spacedBy(20.dp),
    ) {
        // Hero summary card
        HeroSummaryCard(
            blockedCount = blockedApps.size,
            isAccessibilityEnabled = isAccessibilityEnabled,
        )

        // Quick actions row
        QuickActionsRow(
            onAddApp = onNavigateToApps,
            onNewSchedule = onNavigateToSchedules,
        )

        // Blocked apps preview section
        BlockedAppsSection(
            blockedApps = blockedApps,
            onSeeAll = onNavigateToApps,
        )
    }
}

@Composable
private fun HeroSummaryCard(
    blockedCount: Int,
    isAccessibilityEnabled: Boolean,
    modifier: Modifier = Modifier,
) {
    ElevatedCard(
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
                modifier = Modifier.size(56.dp),
                tint = MaterialTheme.colorScheme.onPrimaryContainer,
            )

            Spacer(modifier = Modifier.height(16.dp))

            Text(
                text = "$blockedCount app${if (blockedCount != 1) "s" else ""} blocked",
                style = MaterialTheme.typography.headlineMedium,
                color = MaterialTheme.colorScheme.onPrimaryContainer,
            )

            Spacer(modifier = Modifier.height(4.dp))

            Text(
                text = if (isAccessibilityEnabled) "Blocking is active" else "Blocking is inactive",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onPrimaryContainer,
            )
        }
    }
}

@Composable
private fun QuickActionsRow(
    onAddApp: () -> Unit,
    onNewSchedule: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Row(
        modifier = modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        FilledTonalButton(
            onClick = onAddApp,
            modifier = Modifier.weight(1f),
        ) {
            Text(text = "Add App")
        }

        FilledTonalButton(
            onClick = onNewSchedule,
            modifier = Modifier.weight(1f),
        ) {
            Text(text = "New Schedule")
        }
    }
}

@Composable
private fun BlockedAppsSection(
    blockedApps: List<BlockedApp>,
    onSeeAll: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Column(modifier = modifier.fillMaxWidth()) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(
                text = "Your blocked apps",
                style = MaterialTheme.typography.titleMedium,
                color = MaterialTheme.colorScheme.onSurface,
            )

            if (blockedApps.isNotEmpty()) {
                TextButton(onClick = onSeeAll) {
                    Text(text = "See all")
                    Spacer(modifier = Modifier.width(4.dp))
                    Icon(
                        imageVector = Icons.Outlined.ChevronRight,
                        contentDescription = null,
                        modifier = Modifier.size(18.dp),
                    )
                }
            }
        }

        Spacer(modifier = Modifier.height(8.dp))

        if (blockedApps.isEmpty()) {
            Text(
                text = "No apps blocked yet",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(vertical = 8.dp),
            )
        } else {
            Column(
                verticalArrangement = Arrangement.spacedBy(4.dp),
            ) {
                blockedApps.take(3).forEach { app ->
                    CompactAppRow(app = app)
                }
            }
        }
    }
}

@Composable
private fun CompactAppRow(
    app: BlockedApp,
    modifier: Modifier = Modifier,
) {
    val iconBitmap = rememberAppIcon(app.packageName)

    Row(
        modifier = modifier
            .fillMaxWidth()
            .padding(vertical = 8.dp, horizontal = 4.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        if (iconBitmap != null) {
            Image(
                bitmap = iconBitmap,
                contentDescription = "${app.appName} icon",
                modifier = Modifier.size(32.dp),
            )
        } else {
            Box(modifier = Modifier.size(32.dp))
        }

        Spacer(modifier = Modifier.width(12.dp))

        Text(
            text = app.appName,
            style = MaterialTheme.typography.bodyLarge,
            color = MaterialTheme.colorScheme.onSurface,
        )
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
private fun HeroSummaryCardPreview() {
    AppBlockerTheme {
        HeroSummaryCard(
            blockedCount = 5,
            isAccessibilityEnabled = true,
            modifier = Modifier.padding(16.dp),
        )
    }
}

// endregion
