package com.appblocker.presentation.screen

import android.accessibilityservice.AccessibilityServiceInfo
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.provider.Settings
import android.view.accessibility.AccessibilityManager
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Block
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.repeatOnLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.appblocker.domain.model.BlockedApp
import com.appblocker.presentation.MainViewModel
import com.appblocker.presentation.components.BlockedAppItem
import com.appblocker.presentation.theme.AppBlockerTheme
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

// region Stateful wrapper

/**
 * Stateful MainScreen that owns the ViewModel and collects its state.
 * Now serves as the Apps tab content — no Scaffold or TopAppBar of its own.
 * Includes a FAB overlay and the app picker bottom sheet.
 */
@Composable
fun MainScreen(
    modifier: Modifier = Modifier,
) {
    val viewModel: MainViewModel = viewModel()
    val blockedApps by viewModel.blockedApps.collectAsStateWithLifecycle()
    val isLoading by viewModel.isLoading.collectAsStateWithLifecycle()

    val context = LocalContext.current
    val lifecycleOwner = LocalLifecycleOwner.current

    // Accessibility service status -- re-checked on every resume
    var isAccessibilityEnabled by rememberSaveable { mutableStateOf(true) }
    var accessibilityPromptDismissed by rememberSaveable { mutableStateOf(false) }

    LaunchedEffect(lifecycleOwner) {
        lifecycleOwner.lifecycle.repeatOnLifecycle(Lifecycle.State.RESUMED) {
            isAccessibilityEnabled = checkAccessibilityService(context)
            val prefs = context.getSharedPreferences(
                "${context.packageName}_preferences", Context.MODE_PRIVATE
            )
            accessibilityPromptDismissed = prefs.getBoolean("accessibility_prompt_dismissed", false)
        }
    }

    // App picker bottom sheet state
    var showAppPicker by rememberSaveable { mutableStateOf(false) }
    var pickerApps by remember { mutableStateOf<List<PickerAppInfo>>(emptyList()) }
    var isPickerLoading by remember { mutableStateOf(false) }
    val scope = rememberCoroutineScope()

    val onAddClick: () -> Unit = {
        showAppPicker = true
        isPickerLoading = true
        scope.launch {
            val apps = withContext(Dispatchers.IO) {
                loadInstalledApps(context, blockedApps.map { it.packageName }.toSet())
            }
            pickerApps = apps
            isPickerLoading = false
        }
    }

    Box(modifier = modifier.fillMaxSize()) {
        AppsScreenContent(
            blockedApps = blockedApps,
            isLoading = isLoading,
            showAccessibilityBanner = !isAccessibilityEnabled && !accessibilityPromptDismissed,
            onEnableAccessibility = {
                context.startActivity(Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS))
            },
            onDismissAccessibilityBanner = {
                accessibilityPromptDismissed = true
                val prefs = context.getSharedPreferences(
                    "${context.packageName}_preferences", Context.MODE_PRIVATE
                )
                prefs.edit().putBoolean("accessibility_prompt_dismissed", true).apply()
            },
            onToggle = { packageName, enabled ->
                viewModel.toggleAppBlocking(packageName, enabled)
            },
            onDelete = { packageName ->
                viewModel.removeApp(packageName)
            },
            modifier = Modifier.fillMaxSize(),
        )

        FloatingActionButton(
            onClick = onAddClick,
            containerColor = MaterialTheme.colorScheme.primary,
            contentColor = MaterialTheme.colorScheme.onPrimary,
            modifier = Modifier
                .align(Alignment.BottomEnd)
                .padding(16.dp),
        ) {
            Icon(
                imageVector = Icons.Default.Add,
                contentDescription = "Add app to block",
            )
        }
    }

    if (showAppPicker) {
        AppPickerBottomSheet(
            apps = pickerApps,
            isLoading = isPickerLoading,
            onAppSelected = { packageName, appName ->
                viewModel.addApp(packageName, appName)
                showAppPicker = false
            },
            onDismiss = { showAppPicker = false },
        )
    }
}

private fun checkAccessibilityService(context: Context): Boolean {
    val am = context.getSystemService(Context.ACCESSIBILITY_SERVICE) as AccessibilityManager
    val enabledServices = am.getEnabledAccessibilityServiceList(
        AccessibilityServiceInfo.FEEDBACK_ALL_MASK
    )
    return enabledServices.any {
        it.resolveInfo.serviceInfo.packageName == context.packageName
    }
}

private fun loadInstalledApps(
    context: Context,
    alreadyBlocked: Set<String>,
): List<PickerAppInfo> {
    val pm = context.packageManager
    val launcherIntent = Intent(Intent.ACTION_MAIN).apply {
        addCategory(Intent.CATEGORY_LAUNCHER)
    }
    return pm.queryIntentActivities(launcherIntent, PackageManager.MATCH_ALL)
        .mapNotNull { resolveInfo ->
            val pkgName = resolveInfo.activityInfo.packageName
            if (pkgName == context.packageName || pkgName in alreadyBlocked) {
                null
            } else {
                PickerAppInfo(
                    packageName = pkgName,
                    appName = resolveInfo.loadLabel(pm).toString(),
                )
            }
        }
        .distinctBy { it.packageName }
        .sortedBy { it.appName.lowercase() }
}

// endregion

// region Stateless content

/**
 * Stateless composable containing the Apps tab UI. Receives data and callbacks only.
 * No Scaffold or TopAppBar — those are provided by AppNavigation.
 */
@Composable
fun AppsScreenContent(
    blockedApps: List<BlockedApp>,
    isLoading: Boolean,
    showAccessibilityBanner: Boolean,
    onEnableAccessibility: () -> Unit,
    onDismissAccessibilityBanner: () -> Unit,
    onToggle: (packageName: String, enabled: Boolean) -> Unit,
    onDelete: (packageName: String) -> Unit,
    modifier: Modifier = Modifier,
) {
    Column(
        modifier = modifier.fillMaxSize(),
    ) {
        if (showAccessibilityBanner) {
            AccessibilityBanner(
                onEnable = onEnableAccessibility,
                onDismiss = onDismissAccessibilityBanner,
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 8.dp),
            )
        }

        when {
            isLoading -> {
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .weight(1f),
                    contentAlignment = Alignment.Center,
                ) {
                    CircularProgressIndicator(
                        color = MaterialTheme.colorScheme.primary,
                    )
                }
            }

            blockedApps.isEmpty() -> {
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .weight(1f),
                    contentAlignment = Alignment.Center,
                ) {
                    Column(
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.Center,
                    ) {
                        Icon(
                            imageVector = Icons.Default.Block,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.padding(bottom = 16.dp),
                        )
                        Text(
                            text = "No blocked apps yet.\nTap + to add one.",
                            style = MaterialTheme.typography.bodyLarge,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            textAlign = TextAlign.Center,
                        )
                    }
                }
            }

            else -> {
                LazyColumn(
                    modifier = Modifier
                        .fillMaxSize()
                        .weight(1f),
                    contentPadding = PaddingValues(horizontal = 12.dp, vertical = 8.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    items(
                        items = blockedApps,
                        key = { it.packageName },
                    ) { app ->
                        BlockedAppItem(
                            app = app,
                            onToggle = onToggle,
                            onDelete = onDelete,
                        )
                    }
                }
            }
        }
    }
}

// endregion

// region Accessibility banner

@Composable
private fun AccessibilityBanner(
    onEnable: () -> Unit,
    onDismiss: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Card(
        modifier = modifier,
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.errorContainer,
        ),
        shape = MaterialTheme.shapes.medium,
    ) {
        Column(
            modifier = Modifier.padding(16.dp),
        ) {
            Text(
                text = "Accessibility Service Required",
                style = MaterialTheme.typography.titleSmall,
                color = MaterialTheme.colorScheme.onErrorContainer,
            )
            Spacer(modifier = Modifier.height(4.dp))
            Text(
                text = "App Blocker needs the Accessibility Service to detect and block apps.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onErrorContainer,
            )
            Spacer(modifier = Modifier.height(8.dp))
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.End,
            ) {
                TextButton(onClick = onDismiss) {
                    Text(
                        text = "Later",
                        color = MaterialTheme.colorScheme.onErrorContainer,
                    )
                }
                TextButton(onClick = onEnable) {
                    Text(
                        text = "Enable",
                        color = MaterialTheme.colorScheme.onErrorContainer,
                    )
                }
            }
        }
    }
}

// endregion

// region Previews

private val sampleApps = listOf(
    BlockedApp("com.example.social", "Social Media", true, System.currentTimeMillis()),
    BlockedApp("com.example.video", "Video Player", true, System.currentTimeMillis()),
    BlockedApp("com.example.game", "Fun Game", false, System.currentTimeMillis()),
)

@Preview(showBackground = true, showSystemUi = true)
@Composable
private fun AppsScreenContentPreview() {
    AppBlockerTheme {
        AppsScreenContent(
            blockedApps = sampleApps,
            isLoading = false,
            showAccessibilityBanner = false,
            onEnableAccessibility = {},
            onDismissAccessibilityBanner = {},
            onToggle = { _, _ -> },
            onDelete = {},
        )
    }
}

@Preview(showBackground = true, showSystemUi = true)
@Composable
private fun AppsScreenContentEmptyPreview() {
    AppBlockerTheme {
        AppsScreenContent(
            blockedApps = emptyList(),
            isLoading = false,
            showAccessibilityBanner = true,
            onEnableAccessibility = {},
            onDismissAccessibilityBanner = {},
            onToggle = { _, _ -> },
            onDelete = {},
        )
    }
}

@Preview(showBackground = true, showSystemUi = true)
@Composable
private fun AppsScreenContentLoadingPreview() {
    AppBlockerTheme {
        AppsScreenContent(
            blockedApps = emptyList(),
            isLoading = true,
            showAccessibilityBanner = false,
            onEnableAccessibility = {},
            onDismissAccessibilityBanner = {},
            onToggle = { _, _ -> },
            onDelete = {},
        )
    }
}

@Preview(showBackground = true)
@Composable
private fun AccessibilityBannerPreview() {
    AppBlockerTheme {
        AccessibilityBanner(
            onEnable = {},
            onDismiss = {},
            modifier = Modifier.padding(16.dp),
        )
    }
}

// endregion
