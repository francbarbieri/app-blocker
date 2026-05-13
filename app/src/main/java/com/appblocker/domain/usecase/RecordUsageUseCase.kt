package com.appblocker.domain.usecase

import com.appblocker.domain.model.UnblockEvent
import com.appblocker.domain.model.UnblockOutcome
import com.appblocker.domain.repository.UsageRepository

class RecordUsageUseCase(private val repository: UsageRepository) {
    suspend fun startSession(packageName: String): Long {
        return repository.startSession(packageName, System.currentTimeMillis())
    }

    suspend fun endSession(sessionId: Long) {
        repository.endSession(sessionId, System.currentTimeMillis())
    }

    suspend fun recordUnblock(packageName: String, outcome: UnblockOutcome) {
        val event = UnblockEvent(
            appPackageName = packageName,
            timestamp = System.currentTimeMillis(),
            outcome = outcome
        )
        repository.recordUnblockEvent(event)
    }
}
