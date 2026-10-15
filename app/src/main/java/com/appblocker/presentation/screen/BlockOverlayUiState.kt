package com.appblocker.presentation.screen

import com.appblocker.domain.model.TodayAppStats

sealed interface BlockOverlayUiState {
    val appName: String

    /**
     * Today's numbers, revealed one line at a time. The choice to open the
     * app stays locked until [step] reaches [ReflectionStep.DECIDE].
     */
    data class Reflection(
        override val appName: String,
        /** Null while loading, or when the numbers could not be read. */
        val stats: TodayAppStats?,
        val step: ReflectionStep,
        val pauseSeconds: Int,
    ) : BlockOverlayUiState

    data class Motivational(
        override val appName: String,
        val message: String,
        val stats: TodayAppStats?,
    ) : BlockOverlayUiState
}

enum class ReflectionStep { LOADING, ATTEMPTS, USAGE, DECIDE }

sealed interface BlockOverlayEvent {
    data object GrantGraceAndClose : BlockOverlayEvent
    data object GoHome : BlockOverlayEvent
}
