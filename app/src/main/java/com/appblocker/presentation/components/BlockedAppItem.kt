package com.appblocker.presentation.components

import android.graphics.drawable.Drawable
import androidx.compose.animation.animateColorAsState
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.SwipeToDismissBox
import androidx.compose.material3.SwipeToDismissBoxValue
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.rememberSwipeToDismissBoxState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
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
 * A single blocked-app row displayed inside a Card with swipe-to-delete support.
 *
 * @param app             The blocked app data to display.
 * @param onToggle        Called with (packageName, newEnabled) when the switch is toggled.
 * @param onDelete        Called with packageName when the item is swiped away.
 * @param modifier        Optional modifier.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun BlockedAppItem(
    app: BlockedApp,
    onToggle: (packageName: String, enabled: Boolean) -> Unit,
    onDelete: (packageName: String) -> Unit,
    modifier: Modifier = Modifier,
) {
    val dismissState = rememberSwipeToDismissBoxState(
        confirmValueChange = { value ->
            if (value == SwipeToDismissBoxValue.EndToStart) {
                onDelete(app.packageName)
                true
            } else {
                false
            }
        },
    )

    SwipeToDismissBox(
        state = dismissState,
        backgroundContent = {
            val color by animateColorAsState(
                targetValue = if (dismissState.targetValue == SwipeToDismissBoxValue.EndToStart) {
                    MaterialTheme.colorScheme.error
                } else {
                    MaterialTheme.colorScheme.surface
                },
                label = "swipe-bg-color",
            )
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .background(color, shape = MaterialTheme.shapes.medium)
                    .padding(horizontal = 24.dp),
                contentAlignment = Alignment.CenterEnd,
            ) {
                Icon(
                    imageVector = Icons.Default.Delete,
                    contentDescription = "Delete",
                    tint = MaterialTheme.colorScheme.onError,
                )
            }
        },
        enableDismissFromStartToEnd = false,
        modifier = modifier,
    ) {
        BlockedAppCard(
            app = app,
            onToggle = onToggle,
        )
    }
}

@Composable
private fun BlockedAppCard(
    app: BlockedApp,
    onToggle: (packageName: String, enabled: Boolean) -> Unit,
    modifier: Modifier = Modifier,
) {
    val iconBitmap = rememberAppIcon(app.packageName)

    Card(
        modifier = modifier.fillMaxWidth(),
        shape = MaterialTheme.shapes.medium,
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceVariant,
        ),
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            if (iconBitmap != null) {
                Image(
                    bitmap = iconBitmap,
                    contentDescription = "${app.appName} icon",
                    modifier = Modifier.size(48.dp),
                )
            } else {
                Box(modifier = Modifier.size(48.dp))
            }

            Spacer(modifier = Modifier.width(16.dp))

            Column(
                modifier = Modifier.weight(1f),
                verticalArrangement = Arrangement.Center,
            ) {
                Text(
                    text = app.appName,
                    style = MaterialTheme.typography.bodyLarge,
                    color = MaterialTheme.colorScheme.onSurface,
                )
                Text(
                    text = app.packageName,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }

            Spacer(modifier = Modifier.width(8.dp))

            Switch(
                checked = app.isBlockingEnabled,
                onCheckedChange = { checked ->
                    onToggle(app.packageName, checked)
                },
                modifier = Modifier.size(48.dp),
            )
        }
    }
}

/**
 * Loads an app icon from the PackageManager and converts it to an [ImageBitmap].
 * Returns null if the icon cannot be loaded (e.g. in preview or if the package is missing).
 */
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

@Preview(showBackground = true)
@Composable
private fun BlockedAppItemPreview() {
    AppBlockerTheme {
        BlockedAppCard(
            app = BlockedApp(
                packageName = "com.example.social",
                appName = "Social Media",
                isBlockingEnabled = true,
                createdAt = System.currentTimeMillis(),
            ),
            onToggle = { _, _ -> },
            modifier = Modifier.padding(8.dp),
        )
    }
}

@Preview(showBackground = true)
@Composable
private fun BlockedAppItemDisabledPreview() {
    AppBlockerTheme {
        BlockedAppCard(
            app = BlockedApp(
                packageName = "com.example.game",
                appName = "Fun Game",
                isBlockingEnabled = false,
                createdAt = System.currentTimeMillis(),
            ),
            onToggle = { _, _ -> },
            modifier = Modifier.padding(8.dp),
        )
    }
}

// endregion
