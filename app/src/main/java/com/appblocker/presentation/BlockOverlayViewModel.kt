package com.appblocker.presentation

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.appblocker.domain.model.MIN_PAUSE_SECONDS
import com.appblocker.domain.model.UnblockOutcome
import com.appblocker.domain.model.reflectionPauseSeconds
import com.appblocker.domain.usecase.GetMotivationalMessageUseCase
import com.appblocker.domain.usecase.GetTodayAppStatsUseCase
import com.appblocker.domain.usecase.RecordUsageUseCase
import com.appblocker.presentation.screen.BlockOverlayEvent
import com.appblocker.presentation.screen.BlockOverlayUiState
import com.appblocker.presentation.screen.ReflectionStep
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
    private val getMotivationalMessage: GetMotivationalMessageUseCase,
    private val getTodayAppStats: GetTodayAppStatsUseCase,
) : ViewModel() {

    private var reflection = BlockOverlayUiState.Reflection(
        appName = appName,
        stats = null,
        step = ReflectionStep.LOADING,
        pauseSeconds = MIN_PAUSE_SECONDS,
    )

    private val _state = MutableStateFlow<BlockOverlayUiState>(reflection)
    val state: StateFlow<BlockOverlayUiState> = _state.asStateFlow()

    private val _events = Channel<BlockOverlayEvent>(Channel.BUFFERED)
    val events = _events.receiveAsFlow()

    private val timeline: Job

    // Set once the user has made a decision, so a double tap (or a back press
    // racing a button) cannot record two outcomes for one overlay.
    private var decided = false

    private val unlocked: Boolean
        get() = reflection.step == ReflectionStep.DECIDE

    /**
     * There is no way to skip ahead: the attempts line shows first, the
     * usage line [USAGE_LINE_DELAY_MS] later, and the choices only once the
     * whole pause (longer with every attempt today) has run out.
     */
    init {
        timeline = viewModelScope.launch {
            val stats = try {
                getTodayAppStats(packageName)
            } catch (t: Throwable) {
                android.util.Log.w(TAG, "Failed to load today's stats", t)
                null
            }
            val pauseMs = reflectionPauseSeconds(stats?.attempt ?: 1) * 1000L
            showReflection(
                reflection.copy(
                    stats = stats,
                    step = ReflectionStep.ATTEMPTS,
                    pauseSeconds = (pauseMs / 1000L).toInt(),
                )
            )
            delay(USAGE_LINE_DELAY_MS)
            showReflection(reflection.copy(step = ReflectionStep.USAGE))
            delay(pauseMs - USAGE_LINE_DELAY_MS)
            showReflection(reflection.copy(step = ReflectionStep.DECIDE))
        }
    }

    private fun showReflection(next: BlockOverlayUiState.Reflection) {
        reflection = next
        if (_state.value is BlockOverlayUiState.Reflection) _state.value = next
    }

    fun onBreakingPlan() {
        if (!unlocked || decided) return
        viewModelScope.launch {
            val message = try {
                getMotivationalMessage(packageName)
            } catch (t: Throwable) {
                android.util.Log.w(TAG, "Failed to load motivational message", t)
                FALLBACK_MESSAGE
            }
            _state.value = BlockOverlayUiState.Motivational(appName, message, reflection.stats)
        }
    }

    fun onLegitimate() {
        if (!unlocked) return
        decide(UnblockOutcome.LEGITIMATE, BlockOverlayEvent.GrantGraceAndClose)
    }

    fun onGoBack() = decide(UnblockOutcome.BACKED_OFF, BlockOverlayEvent.GoHome)

    fun onProceed() {
        if (_state.value !is BlockOverlayUiState.Motivational) return
        decide(UnblockOutcome.BROKE_PLAN_PROCEEDED, BlockOverlayEvent.GrantGraceAndClose)
    }

    /** Leaving without opening the app is the best outcome: it counts as walking away. */
    fun onDismissToHome() {
        timeline.cancel()
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
        _state.value = reflection
    }

    companion object {
        const val FALLBACK_MESSAGE = "You made this plan for a reason. Keep it."
        const val USAGE_LINE_DELAY_MS = 2_500L
        private const val TAG = "BlockOverlayViewModel"
    }

    class Factory(
        private val packageName: String,
        private val appName: String,
        private val recordUsage: RecordUsageUseCase,
        private val getMotivationalMessage: GetMotivationalMessageUseCase,
        private val getTodayAppStats: GetTodayAppStatsUseCase,
    ) : ViewModelProvider.Factory {
        @Suppress("UNCHECKED_CAST")
        override fun <T : ViewModel> create(modelClass: Class<T>): T {
            return BlockOverlayViewModel(
                packageName,
                appName,
                recordUsage,
                getMotivationalMessage,
                getTodayAppStats,
            ) as T
        }
    }
}
