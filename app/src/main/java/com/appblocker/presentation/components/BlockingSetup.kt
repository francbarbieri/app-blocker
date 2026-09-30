package com.appblocker.presentation.components

import android.accessibilityservice.AccessibilityServiceInfo
import android.content.ActivityNotFoundException
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.provider.Settings
import android.view.accessibility.AccessibilityManager
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.height
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.repeatOnLifecycle

/** Whether App Blocker's accessibility service is currently enabled. */
fun isBlockingServiceEnabled(context: Context): Boolean {
    val am = context.getSystemService(Context.ACCESSIBILITY_SERVICE) as? AccessibilityManager
        ?: return false
    return am.getEnabledAccessibilityServiceList(AccessibilityServiceInfo.FEEDBACK_ALL_MASK)
        .any { it.resolveInfo.serviceInfo.packageName == context.packageName }
}

/**
 * Tracks [isBlockingServiceEnabled], checked immediately and again every time
 * the screen resumes (for example after returning from system settings).
 */
@Composable
fun rememberBlockingServiceEnabled(): Boolean {
    val context = LocalContext.current
    val lifecycleOwner = LocalLifecycleOwner.current
    var enabled by remember { mutableStateOf(isBlockingServiceEnabled(context)) }
    LaunchedEffect(lifecycleOwner) {
        lifecycleOwner.lifecycle.repeatOnLifecycle(Lifecycle.State.RESUMED) {
            enabled = isBlockingServiceEnabled(context)
        }
    }
    return enabled
}

private fun Context.startActivitySafely(intent: Intent): Boolean = try {
    startActivity(intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
    true
} catch (_: ActivityNotFoundException) {
    false
}

fun openAccessibilitySettings(context: Context) {
    context.startActivitySafely(Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS))
}

/** App info for App Blocker, where Android 13+ hides "Allow restricted settings". */
fun openAppDetails(context: Context) {
    val intent = Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS)
        .setData(Uri.fromParts("package", context.packageName, null))
    context.startActivitySafely(intent)
}

/**
 * Explains how to enable the accessibility service, including the Android 13+
 * "restricted setting" that blocks it for apps installed from an APK file.
 */
@Composable
fun BlockingSetupDialog(onDismiss: () -> Unit) {
    val context = LocalContext.current
    val showRestrictedHint = Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(text = "Turn on blocking") },
        text = {
            Column {
                Text(
                    text = "App Blocker uses Android's Accessibility service to notice which app " +
                        "opens. It never looks at what's on your screen, and nothing leaves your phone.",
                    style = MaterialTheme.typography.bodyMedium,
                )
                Spacer(modifier = Modifier.height(12.dp))
                Text(
                    text = "1. Tap Open settings.\n" +
                        "2. Find App Blocker (often under Downloaded or Installed apps).\n" +
                        "3. Turn it on and confirm.",
                    style = MaterialTheme.typography.bodyMedium,
                )
                if (showRestrictedHint) {
                    Spacer(modifier = Modifier.height(12.dp))
                    Text(
                        text = "Switch greyed out or \"Restricted setting\"? Android limits apps " +
                            "installed from a file. Open App info, tap ⋮ in the top corner, choose " +
                            "Allow restricted settings, then try again.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
        },
        confirmButton = {
            TextButton(
                onClick = {
                    openAccessibilitySettings(context)
                    onDismiss()
                },
            ) { Text(text = "Open settings") }
        },
        dismissButton = {
            if (showRestrictedHint) {
                TextButton(
                    onClick = {
                        openAppDetails(context)
                        onDismiss()
                    },
                ) { Text(text = "App info") }
            } else {
                TextButton(onClick = onDismiss) { Text(text = "Not now") }
            }
        },
    )
}
