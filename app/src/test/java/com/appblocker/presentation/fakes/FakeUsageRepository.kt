package com.appblocker.presentation.fakes

import com.appblocker.domain.model.UnblockEvent
import com.appblocker.domain.model.UsageSession
import com.appblocker.domain.repository.UsageRepository
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow

class FakeUsageRepository : UsageRepository {
    val recordedEvents = mutableListOf<UnblockEvent>()

    override suspend fun startSession(appPackageName: String, startTime: Long): Long = 0L
    override suspend fun endSession(sessionId: Long, endTime: Long) = Unit
    override fun getSessionsForApp(packageName: String, startEpochMs: Long, endEpochMs: Long):
        Flow<List<UsageSession>> = MutableStateFlow(emptyList())
    override suspend fun getTodayTotalDurationMs(packageName: String, dayStartEpochMs: Long): Long = 0L
    override suspend fun getTodaySessionCount(packageName: String, dayStartEpochMs: Long): Int = 0

    override suspend fun recordUnblockEvent(event: UnblockEvent) {
        recordedEvents.add(event)
    }

    override fun getUnblockEventsForApp(packageName: String): Flow<List<UnblockEvent>> =
        MutableStateFlow(emptyList())

    override suspend fun getUnblockEventCountSince(packageName: String, sinceEpochMs: Long): Int = 0
}
