package com.appblocker.domain.usecase

import com.appblocker.domain.model.FocusSession
import com.appblocker.domain.repository.FocusSessionRepository
import kotlinx.coroutines.flow.Flow

class ObserveLatestFocusSessionUseCase(
    private val focusSessionRepository: FocusSessionRepository
) {
    operator fun invoke(): Flow<FocusSession?> =
        focusSessionRepository.observeLatestOpenSession()
}
