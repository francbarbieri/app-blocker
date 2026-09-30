package com.appblocker.presentation.screen

import androidx.compose.foundation.clickable
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.draw.clip
import androidx.compose.foundation.selection.toggleable
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.outlined.HourglassBottom
import androidx.compose.material.icons.outlined.Schedule
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Checkbox
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.SegmentedButton
import androidx.compose.material3.SegmentedButtonDefaults
import androidx.compose.material3.SingleChoiceSegmentedButtonRow
import androidx.compose.material3.Slider
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TimePicker
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.material3.rememberTimePickerState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.appblocker.appContainer
import com.appblocker.domain.model.BlockedApp
import com.appblocker.domain.model.Schedule
import com.appblocker.domain.model.ScheduleType
import com.appblocker.presentation.EditorState
import com.appblocker.presentation.SchedulesUiState
import com.appblocker.presentation.SchedulesViewModel
import com.appblocker.presentation.theme.AppBlockerTheme
import kotlin.math.roundToInt

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
        actions = ScheduleActions(
            onCreate = viewModel::startCreating,
            onEdit = viewModel::startEditing,
            onSetActive = viewModel::setScheduleActive,
            onDismissEditor = viewModel::dismissEditor,
            onUpdateType = viewModel::updateType,
            onUpdateStart = viewModel::updateStartTime,
            onUpdateEnd = viewModel::updateEndTime,
            onUpdateDailyLimit = viewModel::updateDailyLimit,
            onToggleDay = viewModel::toggleDay,
            onToggleApp = viewModel::toggleApp,
            onSave = viewModel::saveEditor,
            onDelete = viewModel::deleteEditing,
        ),
        modifier = modifier,
    )
}

/** Every callback the schedules UI can fire, bundled to keep signatures readable. */
private data class ScheduleActions(
    val onCreate: () -> Unit = {},
    val onEdit: (Schedule) -> Unit = {},
    val onSetActive: (Schedule, Boolean) -> Unit = { _, _ -> },
    val onDismissEditor: () -> Unit = {},
    val onUpdateType: (ScheduleType) -> Unit = {},
    val onUpdateStart: (String) -> Unit = {},
    val onUpdateEnd: (String) -> Unit = {},
    val onUpdateDailyLimit: (Int) -> Unit = {},
    val onToggleDay: (Int) -> Unit = {},
    val onToggleApp: (String) -> Unit = {},
    val onSave: () -> Unit = {},
    val onDelete: () -> Unit = {},
)

@Composable
private fun SchedulesContent(
    state: SchedulesUiState,
    actions: ScheduleActions,
    modifier: Modifier = Modifier,
) {
    Box(modifier = modifier.fillMaxSize()) {
        if (state.schedules.isEmpty() && !state.isLoading) {
            EmptyState(onCreateClick = actions.onCreate)
        } else {
            ScheduleList(
                schedules = state.schedules,
                blockedApps = state.blockedApps,
                onScheduleClick = actions.onEdit,
                onSetActive = actions.onSetActive,
            )
        }

        FloatingActionButton(
            onClick = actions.onCreate,
            modifier = Modifier
                .align(Alignment.BottomEnd)
                .padding(24.dp),
        ) {
            Icon(imageVector = Icons.Filled.Add, contentDescription = "Add schedule")
        }
    }

    state.editor?.let { editor ->
        ScheduleEditorSheet(
            editor = editor,
            blockedApps = state.blockedApps,
            actions = actions,
        )
    }
}

@Composable
private fun EmptyState(onCreateClick: () -> Unit) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(horizontal = 32.dp),
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
            text = "Without a schedule, apps on your list are blocked all the time. " +
                "Add a time window or a daily limit to block them only when it matters.",
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
    onSetActive: (Schedule, Boolean) -> Unit,
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
                onActiveChange = { active -> onSetActive(schedule, active) },
            )
        }
    }
}

@Composable
private fun ScheduleCard(
    schedule: Schedule,
    appNamesByPackage: Map<String, String>,
    onClick: () -> Unit,
    onActiveChange: (Boolean) -> Unit,
) {
    val contentAlpha = if (schedule.isActive) 1f else 0.55f
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceContainerLow,
        ),
    ) {
        Row(
            modifier = Modifier.padding(16.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Icon(
                imageVector = if (schedule.scheduleType == ScheduleType.DAILY_LIMIT) {
                    Icons.Outlined.HourglassBottom
                } else {
                    Icons.Outlined.Schedule
                },
                contentDescription = null,
                tint = MaterialTheme.colorScheme.primary,
                modifier = Modifier
                    .size(28.dp)
                    .alpha(contentAlpha),
            )

            Spacer(modifier = Modifier.width(16.dp))

            Column(
                modifier = Modifier
                    .weight(1f)
                    .alpha(contentAlpha),
            ) {
                Text(
                    text = scheduleTitle(schedule),
                    style = MaterialTheme.typography.titleMedium,
                    color = MaterialTheme.colorScheme.onSurface,
                )

                Spacer(modifier = Modifier.height(4.dp))

                Text(
                    text = if (schedule.isActive) formatDays(schedule.days) else "Paused",
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

            Spacer(modifier = Modifier.width(8.dp))

            Switch(
                checked = schedule.isActive,
                onCheckedChange = onActiveChange,
                modifier = Modifier.semantics {
                    contentDescription = if (schedule.isActive) "Pause schedule" else "Resume schedule"
                },
            )
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun ScheduleEditorSheet(
    editor: EditorState,
    blockedApps: List<BlockedApp>,
    actions: ScheduleActions,
) {
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    var pickerForStart by remember { mutableStateOf<Boolean?>(null) }

    ModalBottomSheet(
        onDismissRequest = actions.onDismissEditor,
        sheetState = sheetState,
        containerColor = MaterialTheme.colorScheme.surface,
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 24.dp)
                .padding(bottom = 24.dp),
        ) {
            Text(
                text = if (editor.isEditing) "Edit schedule" else "New schedule",
                style = MaterialTheme.typography.titleLarge,
                color = MaterialTheme.colorScheme.onSurface,
            )

            Spacer(modifier = Modifier.height(16.dp))

            ScheduleTypeSelector(
                selected = editor.type,
                onSelect = actions.onUpdateType,
            )

            Spacer(modifier = Modifier.height(20.dp))

            when (editor.type) {
                ScheduleType.TIME_WINDOW -> TimeWindowSection(
                    editor = editor,
                    onPickStart = { pickerForStart = true },
                    onPickEnd = { pickerForStart = false },
                )
                ScheduleType.DAILY_LIMIT -> DailyLimitSection(
                    minutes = editor.dailyLimitMinutes,
                    appCount = editor.appPackageNames.size,
                    onChange = actions.onUpdateDailyLimit,
                )
            }

            Spacer(modifier = Modifier.height(20.dp))

            SectionLabel(text = "Days")
            Spacer(modifier = Modifier.height(8.dp))
            DayToggleRow(selected = editor.days, onToggle = actions.onToggleDay)
            if (editor.days.isEmpty()) {
                Spacer(modifier = Modifier.height(4.dp))
                Text(
                    text = "No days selected · applies every day",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }

            Spacer(modifier = Modifier.height(20.dp))

            SectionLabel(text = "Apps  (${editor.appPackageNames.size} selected)")
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
                    onToggle = actions.onToggleApp,
                )
            }

            Spacer(modifier = Modifier.height(24.dp))

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.End,
            ) {
                if (editor.isEditing) {
                    TextButton(onClick = actions.onDelete) {
                        Text(
                            text = "Delete",
                            color = MaterialTheme.colorScheme.error,
                        )
                    }
                    Spacer(modifier = Modifier.size(8.dp))
                }
                TextButton(onClick = actions.onDismissEditor) {
                    Text(text = "Cancel")
                }
                Spacer(modifier = Modifier.size(8.dp))
                Button(onClick = actions.onSave, enabled = editor.canSave) {
                    Text(text = "Save")
                }
            }
        }
    }

    pickerForStart?.let { isStart ->
        TimePickerDialog(
            initial = if (isStart) editor.startTime else editor.endTime,
            onConfirm = { newTime ->
                if (isStart) actions.onUpdateStart(newTime) else actions.onUpdateEnd(newTime)
                pickerForStart = null
            },
            onDismiss = { pickerForStart = null },
        )
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun ScheduleTypeSelector(
    selected: ScheduleType,
    onSelect: (ScheduleType) -> Unit,
) {
    val options = listOf(
        ScheduleType.TIME_WINDOW to "Time window",
        ScheduleType.DAILY_LIMIT to "Daily limit",
    )
    SingleChoiceSegmentedButtonRow(modifier = Modifier.fillMaxWidth()) {
        options.forEachIndexed { index, (type, label) ->
            SegmentedButton(
                selected = type == selected,
                onClick = { onSelect(type) },
                shape = SegmentedButtonDefaults.itemShape(index = index, count = options.size),
            ) {
                Text(text = label)
            }
        }
    }
}

@Composable
private fun TimeWindowSection(
    editor: EditorState,
    onPickStart: () -> Unit,
    onPickEnd: () -> Unit,
) {
    SectionLabel(text = "Blocked between")
    Spacer(modifier = Modifier.height(8.dp))
    Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
        OutlinedButton(onClick = onPickStart) {
            Text(text = "Start  ${editor.startTime}")
        }
        OutlinedButton(onClick = onPickEnd) {
            Text(text = "End  ${editor.endTime}")
        }
    }
    Spacer(modifier = Modifier.height(4.dp))
    Text(
        text = when {
            editor.startTime == editor.endTime -> "Same start and end · blocks the whole day"
            editor.startTime > editor.endTime -> "Ends the next morning · overnight window"
            else -> "Blocked from ${editor.startTime} until ${editor.endTime}"
        },
        style = MaterialTheme.typography.bodySmall,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
    )
}

@Composable
private fun DailyLimitSection(
    minutes: Int,
    appCount: Int,
    onChange: (Int) -> Unit,
) {
    // The slider moves over preset steps rather than raw minutes, so the
    // common 15-90 minute range gets most of the track.
    var index by remember(minutes) { mutableFloatStateOf(nearestLimitStep(minutes).toFloat()) }
    val shown = LIMIT_STEPS[index.roundToInt()]

    SectionLabel(text = "Allowed per day")
    Spacer(modifier = Modifier.height(4.dp))
    Text(
        text = formatMinutesLabel(shown),
        style = MaterialTheme.typography.headlineSmall,
        color = MaterialTheme.colorScheme.onSurface,
    )
    Slider(
        value = index,
        onValueChange = { index = it },
        onValueChangeFinished = { onChange(LIMIT_STEPS[index.roundToInt()]) },
        valueRange = 0f..LIMIT_STEPS.lastIndex.toFloat(),
        steps = LIMIT_STEPS.size - 2,
        modifier = Modifier
            .fillMaxWidth()
            .semantics { contentDescription = "Daily limit, ${formatMinutesLabel(shown)}" },
    )
    Text(
        text = if (appCount > 1) {
            "Shared by the selected apps: once they add up to this much today, " +
                "they're all blocked until midnight."
        } else {
            "Once you've used it this long today, it's blocked until midnight."
        },
        style = MaterialTheme.typography.bodySmall,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
    )
}

/** Daily-limit choices in minutes, within [EditorState.DAILY_LIMIT_RANGE]. */
private val LIMIT_STEPS = listOf(5, 10, 15, 20, 30, 45, 60, 75, 90, 120, 150, 180, 240, 300, 360, 480, 600, 720)

private fun nearestLimitStep(minutes: Int): Int =
    LIMIT_STEPS.indices.minBy { kotlin.math.abs(LIMIT_STEPS[it] - minutes) }

@Composable
private fun SectionLabel(text: String) {
    Text(
        text = text,
        style = MaterialTheme.typography.labelLarge,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
    )
}

/** Seven equal-width round toggles; always fits, even on narrow phones. */
@Composable
private fun DayToggleRow(selected: Set<Int>, onToggle: (Int) -> Unit) {
    val labels = listOf("M", "T", "W", "T", "F", "S", "S")
    val names = listOf("Monday", "Tuesday", "Wednesday", "Thursday", "Friday", "Saturday", "Sunday")
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(6.dp),
    ) {
        labels.forEachIndexed { index, label ->
            val day = index + 1
            val isSelected = day in selected
            Box(
                contentAlignment = Alignment.Center,
                modifier = Modifier
                    .weight(1f)
                    .aspectRatio(1f)
                    .clip(CircleShape)
                    .background(
                        if (isSelected) {
                            MaterialTheme.colorScheme.primary
                        } else {
                            MaterialTheme.colorScheme.surfaceVariant
                        }
                    )
                    // Announced as a checkbox with its checked state.
                    .toggleable(
                        value = isSelected,
                        role = Role.Checkbox,
                        onValueChange = { onToggle(day) },
                    )
                    .semantics { contentDescription = names[index] },
            ) {
                Text(
                    text = label,
                    style = MaterialTheme.typography.labelLarge,
                    color = if (isSelected) {
                        MaterialTheme.colorScheme.onPrimary
                    } else {
                        MaterialTheme.colorScheme.onSurfaceVariant
                    },
                    modifier = Modifier.clearAndSetSemantics { },
                )
            }
        }
    }
}

@Composable
private fun BlockedAppCheckList(
    apps: List<BlockedApp>,
    selected: Set<String>,
    onToggle: (String) -> Unit,
) {
    // Plain Column: the sheet itself scrolls, and nesting a LazyColumn inside a
    // vertical scroll is not allowed.
    Column {
        apps.forEach { app ->
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

private fun scheduleTitle(schedule: Schedule): String = when (schedule.scheduleType) {
    ScheduleType.DAILY_LIMIT -> {
        val shared = if (schedule.appPackageNames.size > 1) ", shared" else ""
        schedule.dailyLimitMinutes?.let { "${formatMinutesLabel(it)} per day$shared" } ?: "Daily limit"
    }
    ScheduleType.TIME_WINDOW -> {
        val start = schedule.startTime ?: "--:--"
        val end = schedule.endTime ?: "--:--"
        when {
            start == end -> "All day"
            start > end -> "$start – $end · overnight"
            else -> "$start – $end"
        }
    }
}

private fun formatDays(days: List<Int>): String {
    if (days.isEmpty()) return "Every day"
    val sorted = days.sorted()
    if (sorted == listOf(1, 2, 3, 4, 5)) return "Weekdays"
    if (sorted == listOf(6, 7)) return "Weekends"
    val names = listOf("Mon", "Tue", "Wed", "Thu", "Fri", "Sat", "Sun")
    return sorted.joinToString("  ") { names[it - 1] }
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

private val previewApps = listOf(
    BlockedApp(packageName = "com.instagram.android", appName = "Instagram", isBlockingEnabled = true, createdAt = 0L),
    BlockedApp(packageName = "com.twitter.android", appName = "Twitter", isBlockingEnabled = true, createdAt = 0L),
    BlockedApp(packageName = "com.tiktok.android", appName = "TikTok", isBlockingEnabled = true, createdAt = 0L),
)

@Preview(showBackground = true, showSystemUi = true)
@Composable
private fun SchedulesEmptyPreview() {
    AppBlockerTheme {
        SchedulesContent(
            state = SchedulesUiState(isLoading = false),
            actions = ScheduleActions(),
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
                        endTime = "06:00",
                        isActive = false,
                        days = emptyList(),
                    ),
                    Schedule(
                        id = 3,
                        appPackageNames = listOf("com.instagram.android"),
                        scheduleType = ScheduleType.DAILY_LIMIT,
                        dailyLimitMinutes = 45,
                        isActive = true,
                        days = listOf(6, 7),
                    ),
                ),
                blockedApps = previewApps,
                isLoading = false,
            ),
            actions = ScheduleActions(),
        )
    }
}

// endregion
