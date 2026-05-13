package com.appblocker.domain.repository

import com.appblocker.domain.model.UnblockEvent
import com.appblocker.domain.model.UsageSession
import kotlinx.coroutines.flow.Flow

interface UsageRepository {
    suspend fun startSession(appPackageName: String, startTime: Long): Long
    suspend fun endSession(sessionId: Long, endTime: Long)
    fun getSessionsForApp(packageName: String, startEpochMs: Long, endEpochMs: Long): Flow<List<UsageSession>>
    suspend fun getTodayTotalDurationMs(packageName: String, dayStartEpochMs: Long): Long
    suspend fun getTodaySessionCount(packageName: String, dayStartEpochMs: Long): Int

    suspend fun recordUnblockEvent(event: UnblockEvent)
    fun getUnblockEventsForApp(packageName: String): Flow<List<UnblockEvent>>
    suspend fun getUnblockEventCountSince(packageName: String, sinceEpochMs: Long): Int
}
