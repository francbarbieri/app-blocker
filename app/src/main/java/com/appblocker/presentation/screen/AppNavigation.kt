package com.appblocker.presentation.screen

import android.accessibilityservice.AccessibilityServiceInfo
import android.content.Context
import android.view.accessibility.AccessibilityManager
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.Schedule
import androidx.compose.material.icons.filled.Security
import androidx.compose.material.icons.outlined.Home
import androidx.compose.material.icons.outlined.Schedule
import androidx.compose.material.icons.outlined.Security
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.tooling.preview.Preview
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.repeatOnLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.appblocker.presentation.MainViewModel
import com.appblocker.presentation.theme.AppBlockerTheme

/**
 * Represents a tab in the bottom navigation.
 */
enum class NavTab(
    val label: String,
    val selectedIcon: ImageVector,
    val unselectedIcon: ImageVector,
) {
    Home(
        label = "Home",
        selectedIcon = Icons.Filled.Home,
        unselectedIcon = Icons.Outlined.Home,
    ),
    Apps(
        label = "Apps",
        selectedIcon = Icons.Filled.Security,
        unselectedIcon = Icons.Outlined.Security,
    ),
    Schedules(
        label = "Schedules",
        selectedIcon = Icons.Filled.Schedule,
        unselectedIcon = Icons.Outlined.Schedule,
    ),
}

/**
 * Top-level navigation composable that provides a shared Scaffold with
 * TopAppBar and bottom NavigationBar across all tabs.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AppNavigation(
    modifier: Modifier = Modifier,
) {
    val viewModel: MainViewModel = viewModel()
    val blockedApps by viewModel.blockedApps.collectAsStateWithLifecycle()
    val isLoading by viewModel.isLoading.collectAsStateWithLifecycle()
    val focusSession by viewModel.focusSession.collectAsStateWithLifecycle()
    val nowMs by viewModel.now.collectAsStateWithLifecycle()

    val context = LocalContext.current
    val lifecycleOwner = LocalLifecycleOwner.current

    // Accessibility service status -- re-checked on every resume
    var isAccessibilityEnabled by rememberSaveable { mutableStateOf(true) }

    LaunchedEffect(lifecycleOwner) {
        lifecycleOwner.lifecycle.repeatOnLifecycle(Lifecycle.State.RESUMED) {
            isAccessibilityEnabled = checkAccessibilityServiceStatus(context)
        }
    }

    var selectedTabIndex by rememberSaveable { mutableIntStateOf(0) }
    val tabs = NavTab.entries

    val currentTab = tabs[selectedTabIndex]

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Text(
                        text = when (currentTab) {
                            NavTab.Home -> "Home"
                            NavTab.Apps -> "Blocked Apps"
                            NavTab.Schedules -> "Schedules"
                        },
                    )
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.surface,
                    titleContentColor = MaterialTheme.colorScheme.onSurface,
                ),
            )
        },
        bottomBar = {
            NavigationBar {
                tabs.forEachIndexed { index, tab ->
                    NavigationBarItem(
                        selected = selectedTabIndex == index,
                        onClick = { selectedTabIndex = index },
                        icon = {
                            Icon(
                                imageVector = if (selectedTabIndex == index) {
                                    tab.selectedIcon
                                } else {
                                    tab.unselectedIcon
                                },
                                contentDescription = tab.label,
                            )
                        },
                        label = { Text(text = tab.label) },
                    )
                }
            }
        },
        modifier = modifier,
    ) { innerPadding ->
        when (currentTab) {
            NavTab.Home -> {
                HomeScreen(
                    blockedApps = blockedApps,
                    isAccessibilityEnabled = isAccessibilityEnabled,
                    focusSession = focusSession,
                    nowMs = nowMs,
                    onStartFocus = viewModel::startFocusSession,
                    onEndFocus = viewModel::endFocusSession,
                    onNavigateToApps = { selectedTabIndex = 1 },
                    onNavigateToSchedules = { selectedTabIndex = 2 },
                    modifier = Modifier.padding(innerPadding),
                )
            }

            NavTab.Apps -> {
                MainScreen(
                    modifier = Modifier.padding(innerPadding),
                )
            }

            NavTab.Schedules -> {
                SchedulesScreen(
                    modifier = Modifier.padding(innerPadding),
                )
            }
        }
    }
}

private fun checkAccessibilityServiceStatus(context: Context): Boolean {
    val am = context.getSystemService(Context.ACCESSIBILITY_SERVICE) as AccessibilityManager
    val enabledServices = am.getEnabledAccessibilityServiceList(
        AccessibilityServiceInfo.FEEDBACK_ALL_MASK
    )
    return enabledServices.any {
        it.resolveInfo.serviceInfo.packageName == context.packageName
    }
}

// region Previews

@Preview(showBackground = true, showSystemUi = true)
@Composable
private fun AppNavigationPreview() {
    AppBlockerTheme {
        // Preview is limited since ViewModel requires Application context
        // but the structure can be verified
        AppNavigation()
    }
}

// endregion
