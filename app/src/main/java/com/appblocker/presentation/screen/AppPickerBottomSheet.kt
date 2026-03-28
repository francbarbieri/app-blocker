package com.appblocker.presentation.screen

import android.graphics.drawable.Drawable
import androidx.compose.foundation.Image
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
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
import com.appblocker.presentation.theme.AppBlockerTheme

/**
 * Data class representing an app entry in the picker.
 * Uses packageName and appName only; the icon is resolved at render time
 * from the system PackageManager to avoid holding Drawable references in state.
 */
data class PickerAppInfo(
    val packageName: String,
    val appName: String,
)

/**
 * A Material 3 ModalBottomSheet that displays a scrollable list of installed apps
 * and invokes [onAppSelected] when the user taps one.
 *
 * @param apps         The list of apps to display.
 * @param isLoading    Whether the app list is still loading.
 * @param onAppSelected Called with (packageName, appName) when an app is tapped.
 * @param onDismiss    Called when the sheet is dismissed.
 * @param modifier     Optional modifier for the sheet content.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AppPickerBottomSheet(
    apps: List<PickerAppInfo>,
    isLoading: Boolean,
    onAppSelected: (packageName: String, appName: String) -> Unit,
    onDismiss: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = false)

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = sheetState,
        containerColor = MaterialTheme.colorScheme.surface,
        contentColor = MaterialTheme.colorScheme.onSurface,
    ) {
        AppPickerContent(
            apps = apps,
            isLoading = isLoading,
            onAppSelected = onAppSelected,
            onDismiss = onDismiss,
            modifier = modifier,
        )
    }
}

/**
 * The inner content of the bottom sheet, extracted so it can be previewed
 * without the ModalBottomSheet wrapper.
 */
@Composable
private fun AppPickerContent(
    apps: List<PickerAppInfo>,
    isLoading: Boolean,
    onAppSelected: (packageName: String, appName: String) -> Unit,
    onDismiss: () -> Unit = {},
    modifier: Modifier = Modifier,
) {
    Column(
        modifier = modifier
            .fillMaxWidth()
            .padding(bottom = 16.dp),
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(start = 24.dp, end = 8.dp, top = 8.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(
                text = "Select an App",
                style = MaterialTheme.typography.titleLarge,
                color = MaterialTheme.colorScheme.onSurface,
                modifier = Modifier.weight(1f),
            )
            IconButton(onClick = onDismiss) {
                Icon(
                    imageVector = Icons.Default.Close,
                    contentDescription = "Close",
                    tint = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }

        when {
            isLoading -> {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(200.dp),
                    contentAlignment = Alignment.Center,
                ) {
                    CircularProgressIndicator(
                        color = MaterialTheme.colorScheme.primary,
                    )
                }
            }

            apps.isEmpty() -> {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(200.dp),
                    contentAlignment = Alignment.Center,
                ) {
                    Text(
                        text = "No apps found",
                        style = MaterialTheme.typography.bodyLarge,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }

            else -> {
                LazyColumn {
                    items(
                        items = apps,
                        key = { it.packageName },
                    ) { app ->
                        AppRow(
                            app = app,
                            onClick = { onAppSelected(app.packageName, app.appName) },
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun AppRow(
    app: PickerAppInfo,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val iconBitmap = rememberAppIcon(app.packageName)

    Row(
        modifier = modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .padding(horizontal = 24.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        if (iconBitmap != null) {
            Image(
                bitmap = iconBitmap,
                contentDescription = "${app.appName} icon",
                modifier = Modifier.size(40.dp),
            )
        } else {
            Box(modifier = Modifier.size(40.dp))
        }

        Spacer(modifier = Modifier.width(16.dp))

        Column(
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
private fun AppPickerContentLoadingPreview() {
    AppBlockerTheme {
        AppPickerContent(
            apps = emptyList(),
            isLoading = true,
            onAppSelected = { _, _ -> },
        )
    }
}

@Preview(showBackground = true)
@Composable
private fun AppPickerContentListPreview() {
    AppBlockerTheme {
        AppPickerContent(
            apps = listOf(
                PickerAppInfo("com.example.social", "Social Media"),
                PickerAppInfo("com.example.video", "Video Player"),
                PickerAppInfo("com.example.game", "Fun Game"),
            ),
            isLoading = false,
            onAppSelected = { _, _ -> },
        )
    }
}

@Preview(showBackground = true)
@Composable
private fun AppPickerContentEmptyPreview() {
    AppBlockerTheme {
        AppPickerContent(
            apps = emptyList(),
            isLoading = false,
            onAppSelected = { _, _ -> },
        )
    }
}

// endregion
