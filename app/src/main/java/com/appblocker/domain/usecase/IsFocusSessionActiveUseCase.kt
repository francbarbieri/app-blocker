package com.appblocker.domain.usecase

import com.appblocker.domain.repository.FocusSessionRepository

class IsFocusSessionActiveUseCase(
    private val focusSessionRepository: FocusSessionRepository
) {
    suspend operator fun invoke(): Boolean =
        focusSessionRepository.getActiveSession() != null
}
