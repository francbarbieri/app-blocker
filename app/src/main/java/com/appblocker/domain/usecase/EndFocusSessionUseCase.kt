package com.appblocker.domain.usecase

import com.appblocker.domain.repository.FocusSessionRepository

class EndFocusSessionUseCase(
    private val focusSessionRepository: FocusSessionRepository
) {
    suspend operator fun invoke() {
        focusSessionRepository.endActiveSession()
    }
}
