package com.appblocker.presentation

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.appblocker.data.local.AppDatabase
import com.appblocker.data.repository.BlockedAppRepositoryImpl
import com.appblocker.data.repository.FocusSessionRepositoryImpl
import com.appblocker.domain.model.BlockedApp
import com.appblocker.domain.model.FocusSession
import com.appblocker.domain.usecase.AddBlockedAppUseCase
import com.appblocker.domain.usecase.GetAllBlockedAppsUseCase
import com.appblocker.domain.usecase.RemoveBlockedAppUseCase
import com.appblocker.domain.usecase.ToggleBlockingUseCase
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.launch

class MainViewModel(application: Application) : AndroidViewModel(application) {

    private val database = AppDatabase.getInstance(application)
    private val repository = BlockedAppRepositoryImpl(
        database.blockedAppDao(),
        database.scheduleDao()
    )
    private val focusSessionRepository = FocusSessionRepositoryImpl(database.focusSessionDao())

    private val getAllBlockedApps = GetAllBlockedAppsUseCase(repository)
    private val addBlockedApp = AddBlockedAppUseCase(repository)
    private val removeBlockedApp = RemoveBlockedAppUseCase(repository)
    private val toggleBlocking = ToggleBlockingUseCase(repository)

    private val _blockedApps = MutableStateFlow<List<BlockedApp>>(emptyList())
    val blockedApps: StateFlow<List<BlockedApp>> = _blockedApps

    private val _isLoading = MutableStateFlow(true)
    val isLoading: StateFlow<Boolean> = _isLoading

    private val _focusSession = MutableStateFlow<FocusSession?>(null)
    val focusSession: StateFlow<FocusSession?> = _focusSession

    private val _now = MutableStateFlow(System.currentTimeMillis())
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
            focusSessionRepository.observeLatestOpenSession().collect { session ->
                _focusSession.value = session
            }
        }
    }

    private fun startClockTick() {
        viewModelScope.launch {
            while (true) {
                _now.value = System.currentTimeMillis()
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
            focusSessionRepository.startSession(durationMs)
        }
    }

    fun endFocusSession() {
        viewModelScope.launch {
            focusSessionRepository.endActiveSession()
        }
    }
}
