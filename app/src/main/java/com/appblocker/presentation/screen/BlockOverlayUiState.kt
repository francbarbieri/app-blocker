package com.appblocker.presentation.screen

sealed interface BlockOverlayUiState {
    val appName: String

    data class BreathingPause(
        override val appName: String,
        val secondsRemaining: Int
    ) : BlockOverlayUiState

    data class Confirmation(override val appName: String) : BlockOverlayUiState

    data class Motivational(
        override val appName: String,
        val message: String
    ) : BlockOverlayUiState
}

sealed interface BlockOverlayEvent {
    data object GrantGraceAndClose : BlockOverlayEvent
    data object GoHome : BlockOverlayEvent
}
