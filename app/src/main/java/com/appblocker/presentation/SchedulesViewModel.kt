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

/**
 * The schedule being created ([id] == null) or edited. Both schedule types keep
 * their fields while the user flips between them, so switching back and forth
 * never loses input.
 */
data class EditorState(
    val id: Long? = null,
    val type: ScheduleType = ScheduleType.TIME_WINDOW,
    val startTime: String = "09:00",
    val endTime: String = "17:00",
    val dailyLimitMinutes: Int = DEFAULT_DAILY_LIMIT_MINUTES,
    val days: Set<Int> = emptySet(),
    val appPackageNames: Set<String> = emptySet(),
    val isActive: Boolean = true,
) {
    val isEditing: Boolean get() = id != null

    val canSave: Boolean
        get() = appPackageNames.isNotEmpty() &&
            (type != ScheduleType.DAILY_LIMIT || dailyLimitMinutes in DAILY_LIMIT_RANGE)

    companion object {
        const val DEFAULT_DAILY_LIMIT_MINUTES = 30
        val DAILY_LIMIT_RANGE = 5..720
    }
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
        _state.update { it.copy(editor = EditorState()) }
    }

    fun startEditing(schedule: Schedule) {
        _state.update {
            it.copy(
                editor = EditorState(
                    id = schedule.id,
                    type = schedule.scheduleType,
                    startTime = schedule.startTime ?: "09:00",
                    endTime = schedule.endTime ?: "17:00",
                    dailyLimitMinutes = schedule.dailyLimitMinutes
                        ?: EditorState.DEFAULT_DAILY_LIMIT_MINUTES,
                    days = schedule.days.toSet(),
                    appPackageNames = schedule.appPackageNames.toSet(),
                    isActive = schedule.isActive,
                ),
            )
        }
    }

    fun dismissEditor() {
        _state.update { it.copy(editor = null) }
    }

    fun updateType(type: ScheduleType) = updateEditor { it.copy(type = type) }

    fun updateStartTime(time: String) = updateEditor { it.copy(startTime = time) }

    fun updateEndTime(time: String) = updateEditor { it.copy(endTime = time) }

    fun updateDailyLimit(minutes: Int) = updateEditor {
        it.copy(dailyLimitMinutes = minutes.coerceIn(EditorState.DAILY_LIMIT_RANGE))
    }

    fun toggleDay(day: Int) = updateEditor {
        it.copy(days = if (day in it.days) it.days - day else it.days + day)
    }

    fun toggleApp(packageName: String) = updateEditor {
        val apps = it.appPackageNames
        it.copy(appPackageNames = if (packageName in apps) apps - packageName else apps + packageName)
    }

    fun saveEditor() {
        val editor = _state.value.editor ?: return
        if (!editor.canSave) return
        val isWindow = editor.type == ScheduleType.TIME_WINDOW
        val schedule = Schedule(
            id = editor.id ?: 0L,
            appPackageNames = editor.appPackageNames.toList(),
            scheduleType = editor.type,
            startTime = if (isWindow) editor.startTime else null,
            endTime = if (isWindow) editor.endTime else null,
            dailyLimitMinutes = if (isWindow) null else editor.dailyLimitMinutes,
            isActive = editor.isActive,
            days = editor.days.sorted(),
        )
        _state.update { it.copy(editor = null) }
        viewModelScope.launch { saveSchedule(schedule) }
    }

    fun deleteEditing() {
        val id = _state.value.editor?.id ?: return
        val schedule = _state.value.schedules.firstOrNull { it.id == id }
        _state.update { it.copy(editor = null) }
        if (schedule != null) {
            viewModelScope.launch { deleteSchedule(schedule) }
        }
    }

    /** Pauses or resumes a schedule straight from the list. */
    fun setScheduleActive(schedule: Schedule, active: Boolean) {
        if (schedule.isActive == active) return
        viewModelScope.launch { saveSchedule(schedule.copy(isActive = active)) }
    }

    private inline fun updateEditor(crossinline transform: (EditorState) -> EditorState) {
        _state.update { state ->
            val editor = state.editor ?: return@update state
            state.copy(editor = transform(editor))
        }
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
