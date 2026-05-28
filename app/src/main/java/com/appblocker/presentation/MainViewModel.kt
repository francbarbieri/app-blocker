package com.appblocker.presentation

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.appblocker.AppContainer
import com.appblocker.domain.model.BlockedApp
import com.appblocker.domain.model.FocusSession
import com.appblocker.domain.usecase.AddBlockedAppUseCase
import com.appblocker.domain.usecase.EndFocusSessionUseCase
import com.appblocker.domain.usecase.GetAllBlockedAppsUseCase
import com.appblocker.domain.usecase.ObserveLatestFocusSessionUseCase
import com.appblocker.domain.usecase.RemoveBlockedAppUseCase
import com.appblocker.domain.usecase.StartFocusSessionUseCase
import com.appblocker.domain.usecase.ToggleBlockingUseCase
import com.appblocker.domain.util.Clock
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.launch

class MainViewModel(
    private val getAllBlockedApps: GetAllBlockedAppsUseCase,
    private val addBlockedApp: AddBlockedAppUseCase,
    private val removeBlockedApp: RemoveBlockedAppUseCase,
    private val toggleBlocking: ToggleBlockingUseCase,
    private val observeLatestFocusSession: ObserveLatestFocusSessionUseCase,
    private val startFocusSessionUseCase: StartFocusSessionUseCase,
    private val endFocusSessionUseCase: EndFocusSessionUseCase,
    private val clock: Clock,
) : ViewModel() {

    private val _blockedApps = MutableStateFlow<List<BlockedApp>>(emptyList())
    val blockedApps: StateFlow<List<BlockedApp>> = _blockedApps

    private val _isLoading = MutableStateFlow(true)
    val isLoading: StateFlow<Boolean> = _isLoading

    private val _focusSession = MutableStateFlow<FocusSession?>(null)
    val focusSession: StateFlow<FocusSession?> = _focusSession

    private val _now = MutableStateFlow(clock.nowMs())
    val now: StateFlow<Long> = _now

    init {
        observeBlockedApps()
        observeFocusSession()
        startClockTick()
    }

    private fun observeBlockedApps() {
        viewModelScope.launch {
            getAllBlockedApps()
                .catch { _isLoading.value = false }
                .collect { apps ->
                    _blockedApps.value = apps
                    _isLoading.value = false
                }
        }
    }

    private fun observeFocusSession() {
        viewModelScope.launch {
            observeLatestFocusSession().collect { session ->
                _focusSession.value = session
            }
        }
    }

    private fun startClockTick() {
        viewModelScope.launch {
            while (true) {
                _now.value = clock.nowMs()
                delay(1_000)
            }
        }
    }

    fun addApp(packageName: String, appName: String) {
        viewModelScope.launch {
            addBlockedApp(packageName, appName)
        }
    }

    fun removeApp(packageName: String) {
        viewModelScope.launch {
            removeBlockedApp(packageName)
        }
    }

    fun toggleAppBlocking(packageName: String, enabled: Boolean) {
        viewModelScope.launch {
            toggleBlocking(packageName, enabled)
        }
    }

    fun startFocusSession(durationMs: Long?) {
        viewModelScope.launch {
            startFocusSessionUseCase(durationMs)
        }
    }

    fun endFocusSession() {
        viewModelScope.launch {
            endFocusSessionUseCase()
        }
    }

    class Factory(private val container: AppContainer) : ViewModelProvider.Factory {
        @Suppress("UNCHECKED_CAST")
        override fun <T : ViewModel> create(modelClass: Class<T>): T = MainViewModel(
            getAllBlockedApps = container.getAllBlockedAppsUseCase,
            addBlockedApp = container.addBlockedAppUseCase,
            removeBlockedApp = container.removeBlockedAppUseCase,
            toggleBlocking = container.toggleBlockingUseCase,
            observeLatestFocusSession = container.observeLatestFocusSessionUseCase,
            startFocusSessionUseCase = container.startFocusSessionUseCase,
            endFocusSessionUseCase = container.endFocusSessionUseCase,
            clock = container.clock,
        ) as T
    }
}
