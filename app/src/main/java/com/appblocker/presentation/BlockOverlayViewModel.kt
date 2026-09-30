package com.appblocker.presentation

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.appblocker.domain.model.UnblockOutcome
import com.appblocker.domain.usecase.GetMotivationalMessageUseCase
import com.appblocker.domain.usecase.RecordUsageUseCase
import com.appblocker.presentation.screen.BlockOverlayEvent
import com.appblocker.presentation.screen.BlockOverlayUiState
import kotlinx.coroutines.Job
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.delay
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

    private val _state = MutableStateFlow<BlockOverlayUiState>(
        BlockOverlayUiState.BreathingPause(appName, BREATH_SECONDS)
    )
    val state: StateFlow<BlockOverlayUiState> = _state.asStateFlow()

    private val _events = Channel<BlockOverlayEvent>(Channel.BUFFERED)
    val events = _events.receiveAsFlow()

    private var countdownJob: Job? = null

    // Set once the user has made a decision, so a double tap (or a back press
    // racing a button) cannot record two outcomes for one overlay.
    private var decided = false

    init {
        countdownJob = viewModelScope.launch {
            while (true) {
                delay(1000)
                val current = _state.value as? BlockOverlayUiState.BreathingPause ?: return@launch
                val next = current.secondsRemaining - 1
                _state.value = current.copy(secondsRemaining = next)
                if (next <= 0) {
                    delay(ZERO_HOLD_MS)
                    if (_state.value is BlockOverlayUiState.BreathingPause) {
                        _state.value = BlockOverlayUiState.Confirmation(appName)
                    }
                    return@launch
                }
            }
        }
    }

    fun onSkipBreath() {
        countdownJob?.cancel()
        if (_state.value is BlockOverlayUiState.BreathingPause) {
            _state.value = BlockOverlayUiState.Confirmation(appName)
        }
    }

    fun onBreakingPlan() {
        viewModelScope.launch {
            val message = try {
                getMotivationalMessage(packageName)
            } catch (t: Throwable) {
                android.util.Log.w(TAG, "Failed to load motivational message", t)
                FALLBACK_MESSAGE
            }
            _state.value = BlockOverlayUiState.Motivational(appName, message)
        }
    }

    fun onLegitimate() = decide(UnblockOutcome.LEGITIMATE, BlockOverlayEvent.GrantGraceAndClose)

    fun onGoBack() = decide(UnblockOutcome.BACKED_OFF, BlockOverlayEvent.GoHome)

    fun onProceed() = decide(UnblockOutcome.BROKE_PLAN_PROCEEDED, BlockOverlayEvent.GrantGraceAndClose)

    /**
     * Back pressed before the user reached the motivational step. Leaving
     * without opening the app is the best outcome, so it counts as walking away.
     */
    fun onDismissToHome() {
        countdownJob?.cancel()
        decide(UnblockOutcome.BACKED_OFF, BlockOverlayEvent.GoHome)
    }

    private fun decide(outcome: UnblockOutcome, event: BlockOverlayEvent) {
        if (decided) return
        decided = true
        viewModelScope.launch {
            try {
                recordUsage.recordUnblock(packageName, outcome)
            } catch (t: Throwable) {
                android.util.Log.w(TAG, "Failed to record unblock outcome", t)
            }
            _events.send(event)
        }
    }

    fun onBackPressedFromMotivational() {
        _state.value = BlockOverlayUiState.Confirmation(appName)
    }

    companion object {
        const val FALLBACK_MESSAGE = "You've got this! Stay focused."
        const val BREATH_SECONDS = 5
        const val ZERO_HOLD_MS = 800L
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
