package com.appblocker.presentation.screen

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.BarChart
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedCard
import androidx.compose.material3.SegmentedButton
import androidx.compose.material3.SegmentedButtonDefaults
import androidx.compose.material3.SingleChoiceSegmentedButtonRow
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.repeatOnLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.appblocker.appContainer
import com.appblocker.domain.model.ActivitySummary
import com.appblocker.domain.model.AppActivity
import com.appblocker.domain.model.DayUsage
import com.appblocker.domain.model.OutcomeCounts
import com.appblocker.presentation.ActivityRange
import com.appblocker.presentation.ActivityUiState
import com.appblocker.presentation.ActivityViewModel
import com.appblocker.presentation.components.rememberAppIcon
import com.appblocker.presentation.theme.AppBlockerTheme
import java.time.LocalDate
import java.time.format.TextStyle
import java.util.Locale

@Composable
fun ActivityScreen(
    modifier: Modifier = Modifier,
    viewModel: ActivityViewModel = viewModel(
        factory = ActivityViewModel.Factory(LocalContext.current.appContainer)
    ),
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    val range by viewModel.selectedRange.collectAsStateWithLifecycle()
    val lifecycleOwner = LocalLifecycleOwner.current

    LaunchedEffect(lifecycleOwner) {
        lifecycleOwner.lifecycle.repeatOnLifecycle(Lifecycle.State.RESUMED) {
            viewModel.refresh()
        }
    }

    ActivityContent(
        state = state,
        range = range,
        onSelectRange = viewModel::selectRange,
        modifier = modifier,
    )
}

@Composable
private fun ActivityContent(
    state: ActivityUiState,
    range: ActivityRange,
    onSelectRange: (ActivityRange) -> Unit,
    modifier: Modifier = Modifier,
) {
    val summary = state.summary
    if (state.isLoading && summary == null) {
        Box(modifier = modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
            CircularProgressIndicator()
        }
        return
    }

    Column(
        modifier = modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        RangeSelector(selected = range, onSelect = onSelectRange)

        val week = state.week
        val hasAnyData = week != null && (week.totalUsageMs > 0 || week.outcomes.total > 0)
        if (summary == null || !hasAnyData) {
            EmptyActivity()
            return@Column
        }

        UsageHero(summary = summary, range = range)

        if (week != null) {
            WeeklyUsageCard(days = week.daily)
        }

        DecisionsCard(outcomes = summary.outcomes, range = range)

        if (summary.apps.isNotEmpty()) {
            AppBreakdownCard(apps = summary.apps)
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun RangeSelector(selected: ActivityRange, onSelect: (ActivityRange) -> Unit) {
    val ranges = ActivityRange.entries
    SingleChoiceSegmentedButtonRow(modifier = Modifier.fillMaxWidth()) {
        ranges.forEachIndexed { index, range ->
            SegmentedButton(
                selected = range == selected,
                onClick = { onSelect(range) },
                shape = SegmentedButtonDefaults.itemShape(index = index, count = ranges.size),
            ) {
                Text(text = range.label)
            }
        }
    }
}

@Composable
private fun UsageHero(summary: ActivitySummary, range: ActivityRange) {
    Column(modifier = Modifier.fillMaxWidth()) {
        Text(
            text = if (range == ActivityRange.Today) {
                "Time in blocked apps today"
            } else {
                "Time in blocked apps, last 7 days"
            },
            style = MaterialTheme.typography.labelLarge,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Spacer(modifier = Modifier.height(4.dp))
        Text(
            text = formatUsage(summary.totalUsageMs),
            style = MaterialTheme.typography.displayMedium,
            fontWeight = FontWeight.SemiBold,
            color = MaterialTheme.colorScheme.onSurface,
        )
        if (range == ActivityRange.Week) {
            Text(
                text = "About ${formatUsage(summary.totalUsageMs / summary.days)} a day",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

@Composable
private fun SectionCard(
    title: String,
    content: @Composable () -> Unit,
) {
    OutlinedCard(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.outlinedCardColors(
            containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.4f),
        ),
    ) {
        Column(modifier = Modifier.padding(20.dp)) {
            Text(
                text = title,
                style = MaterialTheme.typography.titleMedium,
                color = MaterialTheme.colorScheme.onSurface,
            )
            Spacer(modifier = Modifier.height(16.dp))
            content()
        }
    }
}

/**
 * Single-series column chart: one hue, today in full strength and earlier
 * days one step quieter. Tapping a day reveals its value; every bar also
 * carries a spoken description, so the chart is readable without color.
 */
@Composable
private fun WeeklyUsageCard(days: List<DayUsage>) {
    SectionCard(title = "Last 7 days") {
        val maxMs = days.maxOfOrNull { it.usageMs }?.coerceAtLeast(1L) ?: 1L
        var selectedIndex by remember(days.map { it.date }) { mutableIntStateOf(days.lastIndex) }
        val barArea = 120.dp
        val today = days.lastOrNull()?.date

        Row(
            modifier = Modifier
                .fillMaxWidth()
                .height(barArea + 24.dp),
            verticalAlignment = Alignment.Bottom,
        ) {
            days.forEachIndexed { index, day ->
                val isToday = day.date == today
                val isSelected = index == selectedIndex
                Column(
                    modifier = Modifier
                        .weight(1f)
                        .fillMaxHeight()
                        .clickable(role = Role.Button) { selectedIndex = index }
                        .clearAndSetSemantics {
                            contentDescription = "${dayName(day.date, isToday)}: ${formatUsage(day.usageMs)}"
                            selected = isSelected
                        },
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.Bottom,
                ) {
                    Text(
                        text = if (isSelected) formatUsageCompact(day.usageMs) else "",
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.onSurface,
                        maxLines = 1,
                    )
                    Spacer(modifier = Modifier.height(4.dp))
                    val fraction = day.usageMs.toFloat() / maxMs
                    val barHeight = if (day.usageMs > 0) {
                        (barArea * fraction).coerceAtLeast(3.dp)
                    } else {
                        0.dp
                    }
                    Box(
                        modifier = Modifier
                            .width(20.dp)
                            .height(barHeight)
                            .clip(RoundedCornerShape(topStart = 4.dp, topEnd = 4.dp))
                            .background(
                                if (isToday) {
                                    MaterialTheme.colorScheme.primary
                                } else {
                                    MaterialTheme.colorScheme.primary.copy(alpha = 0.55f)
                                }
                            ),
                    )
                }
            }
        }
        HorizontalDivider(
            thickness = 1.dp,
            color = MaterialTheme.colorScheme.outlineVariant,
        )
        Spacer(modifier = Modifier.height(6.dp))
        Row(modifier = Modifier.fillMaxWidth()) {
            days.forEach { day ->
                Text(
                    text = if (day.date == today) "Today" else dayInitial(day.date),
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    textAlign = TextAlign.Center,
                    maxLines = 1,
                    modifier = Modifier
                        .weight(1f)
                        .clearAndSetSemantics { },
                )
            }
        }
    }
}

@Composable
private fun DecisionsCard(outcomes: OutcomeCounts, range: ActivityRange) {
    SectionCard(title = "When the block screen appeared") {
        val unplanned = outcomes.walkedAway + outcomes.openedAnyway
        val rate = outcomes.resistRate
        if (rate == null) {
            Text(
                text = if (outcomes.planned > 0) {
                    "Only planned opens ${if (range == ActivityRange.Today) "today" else "this week"}."
                } else {
                    "No block screens ${if (range == ActivityRange.Today) "today" else "this week"} yet."
                },
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        } else {
            Text(
                text = "You walked away ${outcomes.walkedAway} of $unplanned times",
                style = MaterialTheme.typography.bodyLarge,
                color = MaterialTheme.colorScheme.onSurface,
            )
            Spacer(modifier = Modifier.height(10.dp))
            Meter(fraction = rate)
        }

        Spacer(modifier = Modifier.height(16.dp))

        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            StatTile(label = "Walked away", value = outcomes.walkedAway, modifier = Modifier.weight(1f))
            StatTile(label = "Opened anyway", value = outcomes.openedAnyway, modifier = Modifier.weight(1f))
            StatTile(label = "Planned", value = outcomes.planned, modifier = Modifier.weight(1f))
        }
    }
}

/** A single ratio: fill in the accent, track a lighter step of the same hue. */
@Composable
private fun Meter(fraction: Float) {
    val clamped = fraction.coerceIn(0f, 1f)
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .height(8.dp)
            .clip(RoundedCornerShape(4.dp))
            .background(MaterialTheme.colorScheme.primary.copy(alpha = 0.2f))
            .semantics { contentDescription = "${(clamped * 100).toInt()} percent walked away" },
    ) {
        if (clamped > 0f) {
            Box(
                modifier = Modifier
                    .fillMaxWidth(clamped)
                    .fillMaxHeight()
                    .clip(RoundedCornerShape(4.dp))
                    .background(MaterialTheme.colorScheme.primary),
            )
        }
    }
}

@Composable
private fun StatTile(label: String, value: Int, modifier: Modifier = Modifier) {
    Column(
        modifier = modifier
            .clip(MaterialTheme.shapes.medium)
            .background(MaterialTheme.colorScheme.surface.copy(alpha = 0.6f))
            .padding(horizontal = 12.dp, vertical = 10.dp)
            .semantics(mergeDescendants = true) {},
    ) {
        // Two lines for every tile keeps the values aligned when one label wraps
        // ("Opened anyway" does on most phones).
        Text(
            text = label,
            style = MaterialTheme.typography.labelMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            minLines = 2,
            maxLines = 2,
            overflow = TextOverflow.Ellipsis,
        )
        Text(
            text = value.toString(),
            style = MaterialTheme.typography.headlineSmall,
            fontWeight = FontWeight.SemiBold,
            color = MaterialTheme.colorScheme.onSurface,
        )
    }
}

@Composable
private fun AppBreakdownCard(apps: List<AppActivity>) {
    SectionCard(title = "By app") {
        Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
            apps.forEach { app -> AppActivityRow(app = app) }
        }
    }
}

@Composable
private fun AppActivityRow(app: AppActivity) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = Modifier
            .fillMaxWidth()
            .semantics(mergeDescendants = true) {},
    ) {
        val icon = rememberAppIcon(app.packageName)
        if (icon != null) {
            Image(
                bitmap = icon,
                contentDescription = null,
                modifier = Modifier
                    .size(36.dp)
                    .clip(CircleShape),
            )
        } else {
            Box(
                modifier = Modifier
                    .size(36.dp)
                    .clip(CircleShape)
                    .background(MaterialTheme.colorScheme.surfaceVariant),
            )
        }
        Spacer(modifier = Modifier.width(12.dp))
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = app.appName,
                style = MaterialTheme.typography.bodyLarge,
                color = MaterialTheme.colorScheme.onSurface,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            Text(
                text = blockScreensLabel(app.outcomes),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        Spacer(modifier = Modifier.width(12.dp))
        Text(
            text = formatUsage(app.usageMs),
            style = MaterialTheme.typography.bodyLarge,
            color = MaterialTheme.colorScheme.onSurface,
        )
    }
}

@Composable
private fun EmptyActivity() {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(top = 48.dp, start = 16.dp, end = 16.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Icon(
            imageVector = Icons.Outlined.BarChart,
            contentDescription = null,
            modifier = Modifier.size(64.dp),
            tint = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Spacer(modifier = Modifier.height(16.dp))
        Text(
            text = "Nothing to show yet",
            style = MaterialTheme.typography.headlineSmall,
            color = MaterialTheme.colorScheme.onSurface,
        )
        Spacer(modifier = Modifier.height(8.dp))
        Text(
            text = "Time spent in blocked apps and your answers on the block screen will show up here.",
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            textAlign = TextAlign.Center,
        )
    }
}

private fun blockScreensLabel(outcomes: OutcomeCounts): String = when (outcomes.total) {
    0 -> "No block screens"
    1 -> "1 block screen"
    else -> "${outcomes.total} block screens"
} + if (outcomes.walkedAway > 0) " · walked away ${outcomes.walkedAway}" else ""

private fun dayInitial(date: LocalDate): String =
    date.dayOfWeek.getDisplayName(TextStyle.SHORT, Locale.getDefault())

private fun dayName(date: LocalDate, isToday: Boolean): String =
    if (isToday) "Today" else date.dayOfWeek.getDisplayName(TextStyle.FULL, Locale.getDefault())

// region Previews

private val previewToday = LocalDate.of(2026, 9, 29)

private val previewWeek = ActivitySummary(
    days = 7,
    totalUsageMs = 312 * 60_000L,
    outcomes = OutcomeCounts(planned = 3, walkedAway = 9, openedAnyway = 4),
    daily = listOf(62, 48, 75, 20, 41, 38, 28).mapIndexed { i, minutes ->
        DayUsage(previewToday.minusDays(6L - i), minutes * 60_000L)
    },
    apps = listOf(
        AppActivity("com.instagram.android", "Instagram", 190 * 60_000L, OutcomeCounts(1, 6, 3)),
        AppActivity("com.zhiliaoapp.musically", "TikTok", 122 * 60_000L, OutcomeCounts(2, 3, 1)),
    ),
)

@Preview(showBackground = true, showSystemUi = true)
@Composable
private fun ActivityWeekPreview() {
    AppBlockerTheme {
        ActivityContent(
            state = ActivityUiState(summary = previewWeek, week = previewWeek, isLoading = false),
            range = ActivityRange.Week,
            onSelectRange = {},
        )
    }
}

@Preview(showBackground = true, showSystemUi = true)
@Composable
private fun ActivityEmptyPreview() {
    AppBlockerTheme {
        ActivityContent(
            state = ActivityUiState(
                summary = previewWeek.copy(totalUsageMs = 0, outcomes = OutcomeCounts(), apps = emptyList()),
                week = previewWeek.copy(totalUsageMs = 0, outcomes = OutcomeCounts(), apps = emptyList()),
                isLoading = false,
            ),
            range = ActivityRange.Today,
            onSelectRange = {},
        )
    }
}

// endregion
