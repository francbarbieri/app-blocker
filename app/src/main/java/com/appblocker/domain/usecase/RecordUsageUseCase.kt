package com.appblocker.domain.usecase

import com.appblocker.domain.model.UnblockEvent
import com.appblocker.domain.repository.UsageRepository

class RecordUsageUseCase(private val repository: UsageRepository) {
    suspend fun startSession(packageName: String): Long {
        return repository.startSession(packageName, System.currentTimeMillis())
    }

    suspend fun endSession(sessionId: Long) {
        repository.endSession(sessionId, System.currentTimeMillis())
    }

    suspend fun recordUnblock(packageName: String, userProceeded: Boolean) {
        val event = UnblockEvent(
            appPackageName = packageName,
            timestamp = System.currentTimeMillis(),
            userProceeded = userProceeded
        )
        repository.recordUnblockEvent(event)
    }
}
