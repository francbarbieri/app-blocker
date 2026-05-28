package com.appblocker.domain.usecase

import com.appblocker.domain.model.UnblockEvent
import com.appblocker.domain.model.UnblockOutcome
import com.appblocker.domain.repository.UsageRepository
import com.appblocker.domain.util.Clock

class RecordUsageUseCase(
    private val repository: UsageRepository,
    private val clock: Clock,
) {
    suspend fun startSession(packageName: String): Long {
        return repository.startSession(packageName, clock.nowMs())
    }

    suspend fun endSession(sessionId: Long) {
        repository.endSession(sessionId, clock.nowMs())
    }

    suspend fun recordUnblock(packageName: String, outcome: UnblockOutcome) {
        val event = UnblockEvent(
            appPackageName = packageName,
            timestamp = clock.nowMs(),
            outcome = outcome
        )
        repository.recordUnblockEvent(event)
    }
}
