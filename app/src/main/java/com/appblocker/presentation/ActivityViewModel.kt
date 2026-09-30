package com.appblocker.presentation

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.appblocker.AppContainer
import com.appblocker.domain.model.ActivitySummary
import com.appblocker.domain.usecase.ObserveActivitySummaryUseCase
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn

enum class ActivityRange(val days: Int, val label: String) {
    Today(1, "Today"),
    Week(7, "7 days"),
}

data class ActivityUiState(
    /** Totals for the selected range. */
    val summary: ActivitySummary? = null,
    /** Always the last 7 days, so the daily chart shows in both ranges. */
    val week: ActivitySummary? = null,
    val isLoading: Boolean = true,
)

@OptIn(ExperimentalCoroutinesApi::class)
class ActivityViewModel(
    private val observeActivitySummary: ObserveActivitySummaryUseCase,
) : ViewModel() {

    private val _selectedRange = MutableStateFlow(ActivityRange.Today)
    val selectedRange: StateFlow<ActivityRange> = _selectedRange.asStateFlow()

    // Bumped whenever the screen resumes, so "today" is recomputed after midnight.
    private val refreshes = MutableStateFlow(0)

    // Switching ranges keeps showing the previous numbers until the new ones
    // arrive (a few ms from Room) instead of flashing a loading state.
    val state: StateFlow<ActivityUiState> = combine(_selectedRange, refreshes) { range, _ -> range }
        .flatMapLatest { range ->
            val week = observeActivitySummary(ActivityRange.Week.days)
            val summaries = if (range == ActivityRange.Week) {
                week.map { ActivityUiState(summary = it, week = it, isLoading = false) }
            } else {
                combine(observeActivitySummary(range.days), week) { summary, weekSummary ->
                    ActivityUiState(summary = summary, week = weekSummary, isLoading = false)
                }
            }
            // Caught per subscription, so a failed read ends only this inner
            // flow: switching range or resuming the screen tries again.
            summaries.catch { emit(ActivityUiState(isLoading = false)) }
        }
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5_000),
            initialValue = ActivityUiState(),
        )

    fun selectRange(range: ActivityRange) {
        _selectedRange.value = range
    }

    fun refresh() {
        refreshes.value += 1
    }

    class Factory(private val container: AppContainer) : ViewModelProvider.Factory {
        @Suppress("UNCHECKED_CAST")
        override fun <T : ViewModel> create(modelClass: Class<T>): T =
            ActivityViewModel(container.observeActivitySummaryUseCase) as T
    }
}
