package com.appblocker.data.repository

import com.appblocker.data.local.dao.UnblockEventDao
import com.appblocker.data.local.dao.UsageSessionDao
import com.appblocker.data.local.entity.UnblockEventEntity
import com.appblocker.data.local.entity.UsageSessionEntity
import com.appblocker.domain.model.UnblockEvent
import com.appblocker.domain.model.UnblockOutcome
import com.appblocker.domain.model.UsageSession
import com.appblocker.domain.repository.UsageRepository
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

class UsageRepositoryImpl(
    private val usageSessionDao: UsageSessionDao,
    private val unblockEventDao: UnblockEventDao
) : UsageRepository {

    override suspend fun startSession(appPackageName: String, startTime: Long): Long {
        return usageSessionDao.insert(
            UsageSessionEntity(
                appPackageName = appPackageName,
                startTime = startTime
            )
        )
    }

    override suspend fun endSession(sessionId: Long, endTime: Long) {
        usageSessionDao.updateEndTime(sessionId, endTime)
    }

    override fun getSessionsForApp(
        packageName: String,
        startEpochMs: Long,
        endEpochMs: Long
    ): Flow<List<UsageSession>> {
        return usageSessionDao.getSessionsForApp(packageName, startEpochMs, endEpochMs)
            .map { list -> list.map { it.toDomain() } }
    }

    override suspend fun getTodayTotalDurationMs(packageName: String, dayStartEpochMs: Long): Long {
        return usageSessionDao.getTodayTotalDurationMs(packageName, dayStartEpochMs)
    }

    override suspend fun getTodaySessionCount(packageName: String, dayStartEpochMs: Long): Int {
        return usageSessionDao.getTodaySessionCount(packageName, dayStartEpochMs)
    }

    override suspend fun recordUnblockEvent(event: UnblockEvent) {
        unblockEventDao.insert(event.toEntity())
    }

    override fun getUnblockEventsForApp(packageName: String): Flow<List<UnblockEvent>> {
        return unblockEventDao.getEventsForApp(packageName)
            .map { list -> list.map { it.toDomain() } }
    }

    override suspend fun getUnblockEventCountSince(packageName: String, sinceEpochMs: Long): Int {
        return unblockEventDao.getEventCountSince(packageName, sinceEpochMs)
    }

    private fun UsageSessionEntity.toDomain() = UsageSession(
        id = id,
        appPackageName = appPackageName,
        startTime = startTime,
        endTime = endTime,
        durationMs = durationMs
    )

    private fun UnblockEvent.toEntity() = UnblockEventEntity(
        id = id,
        appPackageName = appPackageName,
        timestamp = timestamp,
        outcome = outcome.name
    )

    private fun UnblockEventEntity.toDomain() = UnblockEvent(
        id = id,
        appPackageName = appPackageName,
        timestamp = timestamp,
        outcome = UnblockOutcome.valueOf(outcome)
    )
}
