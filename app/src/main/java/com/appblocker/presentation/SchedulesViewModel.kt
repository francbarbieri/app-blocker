package com.appblocker.presentation

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.appblocker.AppContainer
import com.appblocker.domain.model.BlockedApp
import com.appblocker.domain.model.Schedule
import com.appblocker.domain.model.ScheduleType
import com.appblocker.domain.usecase.DeleteScheduleUseCase
import com.appblocker.domain.usecase.GetAllBlockedAppsUseCase
import com.appblocker.domain.usecase.ObserveSchedulesUseCase
import com.appblocker.domain.usecase.SaveScheduleUseCase
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

data class SchedulesUiState(
    val schedules: List<Schedule> = emptyList(),
    val blockedApps: List<BlockedApp> = emptyList(),
    val isLoading: Boolean = true,
    val editor: EditorState? = null,
)

sealed interface EditorState {
    val startTime: String
    val endTime: String
    val days: Set<Int>
    val appPackageNames: Set<String>

    data class Creating(
        override val startTime: String = "09:00",
        override val endTime: String = "17:00",
        override val days: Set<Int> = emptySet(),
        override val appPackageNames: Set<String> = emptySet(),
    ) : EditorState

    data class Editing(
        val id: Long,
        override val startTime: String,
        override val endTime: String,
        override val days: Set<Int>,
        override val appPackageNames: Set<String>,
    ) : EditorState
}

class SchedulesViewModel(
    private val observeSchedules: ObserveSchedulesUseCase,
    private val getAllBlockedApps: GetAllBlockedAppsUseCase,
    private val saveSchedule: SaveScheduleUseCase,
    private val deleteSchedule: DeleteScheduleUseCase,
) : ViewModel() {

    private val _state = MutableStateFlow(SchedulesUiState())
    val state: StateFlow<SchedulesUiState> = _state.asStateFlow()

    init {
        viewModelScope.launch {
            combine(
                observeSchedules(),
                getAllBlockedApps(),
            ) { schedules, blockedApps -> schedules to blockedApps }
                .collect { (schedules, blockedApps) ->
                    _state.update {
                        it.copy(
                            schedules = schedules,
                            blockedApps = blockedApps,
                            isLoading = false,
                        )
                    }
                }
        }
    }

    fun startCreating() {
        _state.update { it.copy(editor = EditorState.Creating()) }
    }

    fun startEditing(schedule: Schedule) {
        _state.update {
            it.copy(
                editor = EditorState.Editing(
                    id = schedule.id,
                    startTime = schedule.startTime ?: "09:00",
                    endTime = schedule.endTime ?: "17:00",
                    days = schedule.days.toSet(),
                    appPackageNames = schedule.appPackageNames.toSet(),
                ),
            )
        }
    }

    fun dismissEditor() {
        _state.update { it.copy(editor = null) }
    }

    fun updateStartTime(time: String) {
        _state.update { it.withEditor { e -> e.copyWith(startTime = time) } }
    }

    fun updateEndTime(time: String) {
        _state.update { it.withEditor { e -> e.copyWith(endTime = time) } }
    }

    fun toggleDay(day: Int) {
        _state.update {
            it.withEditor { e ->
                val newDays = if (day in e.days) e.days - day else e.days + day
                e.copyWith(days = newDays)
            }
        }
    }

    fun toggleApp(packageName: String) {
        _state.update {
            it.withEditor { e ->
                val newApps = if (packageName in e.appPackageNames) {
                    e.appPackageNames - packageName
                } else {
                    e.appPackageNames + packageName
                }
                e.copyWith(appPackageNames = newApps)
            }
        }
    }

    fun saveEditor() {
        val editor = _state.value.editor ?: return
        if (editor.appPackageNames.isEmpty()) return
        viewModelScope.launch {
            val schedule = Schedule(
                id = (editor as? EditorState.Editing)?.id ?: 0L,
                appPackageNames = editor.appPackageNames.toList(),
                scheduleType = ScheduleType.TIME_WINDOW,
                startTime = editor.startTime,
                endTime = editor.endTime,
                dailyLimitMinutes = null,
                isActive = true,
                days = editor.days.toList(),
            )
            saveSchedule(schedule)
            _state.update { it.copy(editor = null) }
        }
    }

    fun deleteEditing() {
        val editor = _state.value.editor as? EditorState.Editing ?: return
        viewModelScope.launch {
            val schedule = _state.value.schedules.firstOrNull { it.id == editor.id }
            if (schedule != null) {
                deleteSchedule(schedule)
            }
            _state.update { it.copy(editor = null) }
        }
    }

    private inline fun SchedulesUiState.withEditor(transform: (EditorState) -> EditorState): SchedulesUiState {
        val current = editor ?: return this
        return copy(editor = transform(current))
    }

    private fun EditorState.copyWith(
        startTime: String = this.startTime,
        endTime: String = this.endTime,
        days: Set<Int> = this.days,
        appPackageNames: Set<String> = this.appPackageNames,
    ): EditorState = when (this) {
        is EditorState.Creating -> copy(
            startTime = startTime,
            endTime = endTime,
            days = days,
            appPackageNames = appPackageNames,
        )
        is EditorState.Editing -> copy(
            startTime = startTime,
            endTime = endTime,
            days = days,
            appPackageNames = appPackageNames,
        )
    }

    class Factory(private val container: AppContainer) : ViewModelProvider.Factory {
        @Suppress("UNCHECKED_CAST")
        override fun <T : ViewModel> create(modelClass: Class<T>): T = SchedulesViewModel(
            observeSchedules = container.observeSchedulesUseCase,
            getAllBlockedApps = container.getAllBlockedAppsUseCase,
            saveSchedule = container.saveScheduleUseCase,
            deleteSchedule = container.deleteScheduleUseCase,
        ) as T
    }
}
