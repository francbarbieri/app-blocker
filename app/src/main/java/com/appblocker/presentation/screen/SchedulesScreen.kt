package com.appblocker.presentation.screen

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.outlined.Schedule
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Checkbox
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TimePicker
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.material3.rememberTimePickerState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.appblocker.domain.model.BlockedApp
import com.appblocker.domain.model.Schedule
import com.appblocker.domain.model.ScheduleType
import com.appblocker.presentation.EditorState
import com.appblocker.presentation.SchedulesUiState
import androidx.compose.ui.platform.LocalContext
import com.appblocker.appContainer
import com.appblocker.presentation.SchedulesViewModel
import com.appblocker.presentation.theme.AppBlockerTheme

@Composable
fun SchedulesScreen(
    modifier: Modifier = Modifier,
    viewModel: SchedulesViewModel = viewModel(
        factory = SchedulesViewModel.Factory(LocalContext.current.appContainer)
    ),
) {
    val state by viewModel.state.collectAsStateWithLifecycle()

    SchedulesContent(
        state = state,
        onCreateClick = viewModel::startCreating,
        onScheduleClick = viewModel::startEditing,
        onDismissEditor = viewModel::dismissEditor,
        onUpdateStart = viewModel::updateStartTime,
        onUpdateEnd = viewModel::updateEndTime,
        onToggleDay = viewModel::toggleDay,
        onToggleApp = viewModel::toggleApp,
        onSave = viewModel::saveEditor,
        onDelete = viewModel::deleteEditing,
        modifier = modifier,
    )
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun SchedulesContent(
    state: SchedulesUiState,
    onCreateClick: () -> Unit,
    onScheduleClick: (Schedule) -> Unit,
    onDismissEditor: () -> Unit,
    onUpdateStart: (String) -> Unit,
    onUpdateEnd: (String) -> Unit,
    onToggleDay: (Int) -> Unit,
    onToggleApp: (String) -> Unit,
    onSave: () -> Unit,
    onDelete: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Box(modifier = modifier.fillMaxSize()) {
        if (state.schedules.isEmpty() && !state.isLoading) {
            EmptyState(onCreateClick = onCreateClick)
        } else {
            ScheduleList(
                schedules = state.schedules,
                blockedApps = state.blockedApps,
                onScheduleClick = onScheduleClick,
            )
        }

        FloatingActionButton(
            onClick = onCreateClick,
            modifier = Modifier
                .align(Alignment.BottomEnd)
                .padding(24.dp),
        ) {
            Icon(imageVector = Icons.Filled.Add, contentDescription = "Add schedule")
        }
    }

    if (state.editor != null) {
        ScheduleEditorSheet(
            editor = state.editor,
            blockedApps = state.blockedApps,
            onDismiss = onDismissEditor,
            onUpdateStart = onUpdateStart,
            onUpdateEnd = onUpdateEnd,
            onToggleDay = onToggleDay,
            onToggleApp = onToggleApp,
            onSave = onSave,
            onDelete = onDelete,
        )
    }
}

@Composable
private fun EmptyState(onCreateClick: () -> Unit) {
    Column(
        modifier = Modifier.fillMaxSize(),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center,
    ) {
        Icon(
            imageVector = Icons.Outlined.Schedule,
            contentDescription = null,
            modifier = Modifier.size(72.dp),
            tint = MaterialTheme.colorScheme.onSurfaceVariant,
        )

        Spacer(modifier = Modifier.height(16.dp))

        Text(
            text = "No schedules yet",
            style = MaterialTheme.typography.headlineSmall,
            color = MaterialTheme.colorScheme.onSurface,
        )

        Spacer(modifier = Modifier.height(8.dp))

        Text(
            text = "Create a time window that\napplies to one or more apps",
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            textAlign = TextAlign.Center,
        )

        Spacer(modifier = Modifier.height(24.dp))

        OutlinedButton(onClick = onCreateClick) {
            Text(text = "Create schedule")
        }
    }
}

@Composable
private fun ScheduleList(
    schedules: List<Schedule>,
    blockedApps: List<BlockedApp>,
    onScheduleClick: (Schedule) -> Unit,
) {
    val appNamesByPackage = remember(blockedApps) {
        blockedApps.associate { it.packageName to it.appName }
    }
    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(start = 16.dp, end = 16.dp, top = 16.dp, bottom = 96.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        items(items = schedules, key = { it.id }) { schedule ->
            ScheduleCard(
                schedule = schedule,
                appNamesByPackage = appNamesByPackage,
                onClick = { onScheduleClick(schedule) },
            )
        }
    }
}

@Composable
private fun ScheduleCard(
    schedule: Schedule,
    appNamesByPackage: Map<String, String>,
    onClick: () -> Unit,
) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceContainerLow,
        ),
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Text(
                text = "${schedule.startTime ?: "--:--"} – ${schedule.endTime ?: "--:--"}",
                style = MaterialTheme.typography.titleMedium,
                color = MaterialTheme.colorScheme.onSurface,
            )

            Spacer(modifier = Modifier.height(4.dp))

            Text(
                text = formatDays(schedule.days),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )

            Spacer(modifier = Modifier.height(8.dp))

            Text(
                text = formatApps(schedule.appPackageNames, appNamesByPackage),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun ScheduleEditorSheet(
    editor: EditorState,
    blockedApps: List<BlockedApp>,
    onDismiss: () -> Unit,
    onUpdateStart: (String) -> Unit,
    onUpdateEnd: (String) -> Unit,
    onToggleDay: (Int) -> Unit,
    onToggleApp: (String) -> Unit,
    onSave: () -> Unit,
    onDelete: () -> Unit,
) {
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    val isEditing = editor is EditorState.Editing
    val canSave = editor.appPackageNames.isNotEmpty()

    var pickerForStart by remember { mutableStateOf<Boolean?>(null) }

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = sheetState,
        containerColor = MaterialTheme.colorScheme.surface,
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 24.dp)
                .padding(bottom = 24.dp),
        ) {
            Text(
                text = if (isEditing) "Edit schedule" else "New schedule",
                style = MaterialTheme.typography.titleLarge,
                color = MaterialTheme.colorScheme.onSurface,
            )

            Spacer(modifier = Modifier.height(16.dp))

            Text(
                text = "Time window",
                style = MaterialTheme.typography.labelLarge,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Spacer(modifier = Modifier.height(8.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                OutlinedButton(onClick = { pickerForStart = true }) {
                    Text(text = "Start  ${editor.startTime}")
                }
                OutlinedButton(onClick = { pickerForStart = false }) {
                    Text(text = "End  ${editor.endTime}")
                }
            }

            Spacer(modifier = Modifier.height(20.dp))

            Text(
                text = "Days",
                style = MaterialTheme.typography.labelLarge,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Spacer(modifier = Modifier.height(8.dp))
            DayChipsRow(selected = editor.days, onToggle = onToggleDay)
            if (editor.days.isEmpty()) {
                Spacer(modifier = Modifier.height(4.dp))
                Text(
                    text = "No days selected · applies every day",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }

            Spacer(modifier = Modifier.height(20.dp))

            Text(
                text = "Apps  (${editor.appPackageNames.size} selected)",
                style = MaterialTheme.typography.labelLarge,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Spacer(modifier = Modifier.height(8.dp))
            if (blockedApps.isEmpty()) {
                Text(
                    text = "No blocked apps yet. Add apps from the Apps tab first.",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            } else {
                BlockedAppCheckList(
                    apps = blockedApps,
                    selected = editor.appPackageNames,
                    onToggle = onToggleApp,
                )
            }

            Spacer(modifier = Modifier.height(24.dp))

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.End,
            ) {
                if (isEditing) {
                    TextButton(onClick = onDelete) {
                        Text(
                            text = "Delete",
                            color = MaterialTheme.colorScheme.error,
                        )
                    }
                    Spacer(modifier = Modifier.size(8.dp))
                }
                TextButton(onClick = onDismiss) {
                    Text(text = "Cancel")
                }
                Spacer(modifier = Modifier.size(8.dp))
                Button(onClick = onSave, enabled = canSave) {
                    Text(text = "Save")
                }
            }
        }
    }

    pickerForStart?.let { isStart ->
        TimePickerDialog(
            initial = if (isStart) editor.startTime else editor.endTime,
            onConfirm = { newTime ->
                if (isStart) onUpdateStart(newTime) else onUpdateEnd(newTime)
                pickerForStart = null
            },
            onDismiss = { pickerForStart = null },
        )
    }
}

@Composable
private fun DayChipsRow(selected: Set<Int>, onToggle: (Int) -> Unit) {
    val labels = listOf("M", "T", "W", "T", "F", "S", "S")
    Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
        labels.forEachIndexed { index, label ->
            val day = index + 1
            FilterChip(
                selected = day in selected,
                onClick = { onToggle(day) },
                label = { Text(text = label) },
                colors = FilterChipDefaults.filterChipColors(),
            )
        }
    }
}

@Composable
private fun BlockedAppCheckList(
    apps: List<BlockedApp>,
    selected: Set<String>,
    onToggle: (String) -> Unit,
) {
    Column(modifier = Modifier.heightIn(max = 240.dp)) {
        LazyColumn {
            items(items = apps, key = { it.packageName }) { app ->
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier
                        .fillMaxWidth()
                        .clickable { onToggle(app.packageName) }
                        .padding(vertical = 4.dp),
                ) {
                    Checkbox(
                        checked = app.packageName in selected,
                        onCheckedChange = { onToggle(app.packageName) },
                    )
                    Spacer(modifier = Modifier.size(8.dp))
                    Text(
                        text = app.appName,
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurface,
                    )
                }
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun TimePickerDialog(
    initial: String,
    onConfirm: (String) -> Unit,
    onDismiss: () -> Unit,
) {
    val (initialHour, initialMinute) = parseTime(initial)
    val pickerState = rememberTimePickerState(
        initialHour = initialHour,
        initialMinute = initialMinute,
        is24Hour = true,
    )
    AlertDialog(
        onDismissRequest = onDismiss,
        confirmButton = {
            TextButton(
                onClick = { onConfirm(formatTime(pickerState.hour, pickerState.minute)) },
            ) { Text(text = "OK") }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text(text = "Cancel") }
        },
        text = { TimePicker(state = pickerState) },
    )
}

private fun parseTime(value: String): Pair<Int, Int> {
    val parts = value.split(":")
    val hour = parts.getOrNull(0)?.toIntOrNull()?.coerceIn(0, 23) ?: 9
    val minute = parts.getOrNull(1)?.toIntOrNull()?.coerceIn(0, 59) ?: 0
    return hour to minute
}

private fun formatTime(hour: Int, minute: Int): String =
    "%02d:%02d".format(hour, minute)

private fun formatDays(days: List<Int>): String {
    if (days.isEmpty()) return "Every day"
    val names = listOf("Mon", "Tue", "Wed", "Thu", "Fri", "Sat", "Sun")
    return days.sorted().joinToString("  ") { names[it - 1] }
}

private fun formatApps(packageNames: List<String>, namesByPackage: Map<String, String>): String {
    if (packageNames.isEmpty()) return "No apps"
    val labels = packageNames.map { namesByPackage[it] ?: it }
    return when {
        labels.size <= 3 -> labels.joinToString(", ")
        else -> labels.take(2).joinToString(", ") + " +${labels.size - 2} more"
    }
}

// region Previews

@Preview(showBackground = true, showSystemUi = true)
@Composable
private fun SchedulesEmptyPreview() {
    AppBlockerTheme {
        SchedulesContent(
            state = SchedulesUiState(isLoading = false),
            onCreateClick = {},
            onScheduleClick = {},
            onDismissEditor = {},
            onUpdateStart = {},
            onUpdateEnd = {},
            onToggleDay = {},
            onToggleApp = {},
            onSave = {},
            onDelete = {},
        )
    }
}

@Preview(showBackground = true, showSystemUi = true)
@Composable
private fun SchedulesListPreview() {
    AppBlockerTheme {
        SchedulesContent(
            state = SchedulesUiState(
                schedules = listOf(
                    Schedule(
                        id = 1,
                        appPackageNames = listOf("com.instagram.android", "com.twitter.android"),
                        scheduleType = ScheduleType.TIME_WINDOW,
                        startTime = "09:00",
                        endTime = "17:00",
                        isActive = true,
                        days = listOf(1, 2, 3, 4, 5),
                    ),
                    Schedule(
                        id = 2,
                        appPackageNames = listOf("com.tiktok.android"),
                        scheduleType = ScheduleType.TIME_WINDOW,
                        startTime = "22:00",
                        endTime = "23:59",
                        isActive = true,
                        days = emptyList(),
                    ),
                ),
                blockedApps = listOf(
                    BlockedApp(packageName = "com.instagram.android", appName = "Instagram", isBlockingEnabled = true, createdAt = 0L),
                    BlockedApp(packageName = "com.twitter.android", appName = "Twitter", isBlockingEnabled = true, createdAt = 0L),
                    BlockedApp(packageName = "com.tiktok.android", appName = "TikTok", isBlockingEnabled = true, createdAt = 0L),
                ),
                isLoading = false,
            ),
            onCreateClick = {},
            onScheduleClick = {},
            onDismissEditor = {},
            onUpdateStart = {},
            onUpdateEnd = {},
            onToggleDay = {},
            onToggleApp = {},
            onSave = {},
            onDelete = {},
        )
    }
}

// endregion
