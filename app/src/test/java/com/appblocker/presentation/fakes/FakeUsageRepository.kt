package com.appblocker.presentation.fakes

import com.appblocker.domain.model.UnblockEvent
import com.appblocker.domain.model.UsageSession
import com.appblocker.domain.repository.UsageRepository
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.map

/**
 * In-memory [UsageRepository]. [getUsageMs] clips sessions to the requested
 * range exactly like the Room query, so domain tests exercise real semantics.
 */
class FakeUsageRepository : UsageRepository {
    val recordedEvents = mutableListOf<UnblockEvent>()
    val sessions = MutableStateFlow<List<UsageSession>>(emptyList())
    private val events = MutableStateFlow<List<UnblockEvent>>(emptyList())

    /** Every (packageNames, fromMs, toMs) passed to [getUsageMs]. */
    val usageQueries = mutableListOf<Triple<Set<String>, Long, Long>>()

    fun addSession(packageName: String, startTime: Long, endTime: Long) {
        sessions.value = sessions.value + UsageSession(
            id = sessions.value.size + 1L,
            appPackageName = packageName,
            startTime = startTime,
            endTime = endTime,
            durationMs = endTime - startTime,
        )
    }

    /** Simulates a slow database write, to expose interleaving bugs. */
    var startSessionDelayMs: Long = 0L

    override suspend fun startSession(appPackageName: String, startTime: Long): Long {
        if (startSessionDelayMs > 0) kotlinx.coroutines.delay(startSessionDelayMs)
        addSession(appPackageName, startTime, startTime)
        return sessions.value.last().id
    }

    override suspend fun updateSessionEnd(sessionId: Long, endTime: Long) {
        sessions.value = sessions.value.map {
            if (it.id == sessionId) it.copy(endTime = endTime, durationMs = endTime - it.startTime) else it
        }
    }

    override fun getSessionsForApp(packageName: String, startEpochMs: Long, endEpochMs: Long):
        Flow<List<UsageSession>> = sessions.map { list -> list.filter { it.appPackageName == packageName } }

    override suspend fun getUsageMs(packageNames: Collection<String>, fromMs: Long, toMs: Long): Long {
        usageQueries += Triple(packageNames.toSet(), fromMs, toMs)
        return sessions.value
            .filter { it.appPackageName in packageNames }
            .sumOf { session ->
                val end = session.endTime ?: return@sumOf 0L
                (minOf(end, toMs) - maxOf(session.startTime, fromMs)).coerceAtLeast(0L)
            }
    }

    override suspend fun getTodaySessionCount(packageName: String, dayStartEpochMs: Long): Int =
        sessions.value.count { it.appPackageName == packageName && it.startTime >= dayStartEpochMs }

    override fun observeSessionsOverlapping(fromMs: Long, toMs: Long): Flow<List<UsageSession>> =
        sessions.map { list ->
            list.filter { (it.endTime ?: Long.MIN_VALUE) > fromMs && it.startTime < toMs }
        }

    override suspend fun recordUnblockEvent(event: UnblockEvent) {
        recordedEvents.add(event)
        events.value = events.value + event
    }

    override fun getUnblockEventsForApp(packageName: String): Flow<List<UnblockEvent>> =
        events.map { list -> list.filter { it.appPackageName == packageName } }

    override suspend fun getUnblockEventCountSince(packageName: String, sinceEpochMs: Long): Int =
        events.value.count { it.appPackageName == packageName && it.timestamp >= sinceEpochMs }

    override fun observeUnblockEventsBetween(fromMs: Long, toMs: Long): Flow<List<UnblockEvent>> =
        events.map { list -> list.filter { it.timestamp in fromMs until toMs } }
}
