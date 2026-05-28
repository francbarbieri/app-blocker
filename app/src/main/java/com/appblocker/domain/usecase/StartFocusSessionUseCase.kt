package com.appblocker.domain.usecase

import com.appblocker.domain.model.FocusSession
import com.appblocker.domain.repository.FocusSessionRepository

class StartFocusSessionUseCase(
    private val focusSessionRepository: FocusSessionRepository
) {
    suspend operator fun invoke(durationMs: Long?): FocusSession =
        focusSessionRepository.startSession(durationMs)
}
