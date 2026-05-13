package com.appblocker.presentation

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.appblocker.domain.model.UnblockOutcome
import com.appblocker.domain.usecase.GetMotivationalMessageUseCase
import com.appblocker.domain.usecase.RecordUsageUseCase
import com.appblocker.presentation.screen.BlockOverlayEvent
import com.appblocker.presentation.screen.BlockOverlayUiState
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.launch

class BlockOverlayViewModel(
    private val packageName: String,
    private val appName: String,
    private val recordUsage: RecordUsageUseCase,
    private val getMotivationalMessage: GetMotivationalMessageUseCase
) : ViewModel() {

    private val _state = MutableStateFlow<BlockOverlayUiState>(BlockOverlayUiState.Confirmation(appName))
    val state: StateFlow<BlockOverlayUiState> = _state.asStateFlow()

    private val _events = Channel<BlockOverlayEvent>(Channel.BUFFERED)
    val events = _events.receiveAsFlow()

    fun onBreakingPlan() {
        viewModelScope.launch {
            val message = try {
                getMotivationalMessage(packageName)?.message ?: FALLBACK_MESSAGE
            } catch (t: Throwable) {
                android.util.Log.w(TAG, "Failed to load motivational message for $packageName", t)
                FALLBACK_MESSAGE
            }
            _state.value = BlockOverlayUiState.Motivational(appName, message)
        }
    }

    fun onLegitimate() {
        viewModelScope.launch {
            try {
                recordUsage.recordUnblock(packageName, UnblockOutcome.LEGITIMATE)
            } catch (t: Throwable) {
                android.util.Log.w(TAG, "Failed to record LEGITIMATE outcome for $packageName", t)
            }
            _events.send(BlockOverlayEvent.GrantGraceAndClose)
        }
    }

    fun onGoBack() {
        viewModelScope.launch {
            try {
                recordUsage.recordUnblock(packageName, UnblockOutcome.BACKED_OFF)
            } catch (t: Throwable) {
                android.util.Log.w(TAG, "Failed to record BACKED_OFF outcome for $packageName", t)
            }
            _events.send(BlockOverlayEvent.GoHome)
        }
    }

    fun onProceed() {
        viewModelScope.launch {
            try {
                recordUsage.recordUnblock(packageName, UnblockOutcome.BROKE_PLAN_PROCEEDED)
            } catch (t: Throwable) {
                android.util.Log.w(TAG, "Failed to record BROKE_PLAN_PROCEEDED outcome for $packageName", t)
            }
            _events.send(BlockOverlayEvent.GrantGraceAndClose)
        }
    }

    fun onBackPressedFromMotivational() {
        _state.value = BlockOverlayUiState.Confirmation(appName)
    }

    companion object {
        const val FALLBACK_MESSAGE = "You've got this! Stay focused."
        private const val TAG = "BlockOverlayViewModel"
    }

    class Factory(
        private val packageName: String,
        private val appName: String,
        private val recordUsage: RecordUsageUseCase,
        private val getMotivationalMessage: GetMotivationalMessageUseCase
    ) : ViewModelProvider.Factory {
        @Suppress("UNCHECKED_CAST")
        override fun <T : ViewModel> create(modelClass: Class<T>): T {
            return BlockOverlayViewModel(packageName, appName, recordUsage, getMotivationalMessage) as T
        }
    }
}
