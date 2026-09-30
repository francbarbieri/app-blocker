package com.appblocker.domain.repository

import com.appblocker.domain.model.UnblockEvent
import com.appblocker.domain.model.UsageSession
import kotlinx.coroutines.flow.Flow

interface UsageRepository {
    /**
     * Opens a usage session at [startTime] and returns its id. The session's
     * end starts equal to its start and is moved forward with [updateSessionEnd]
     * while the app stays in the foreground, so a killed process loses at most
     * one heartbeat of usage instead of leaving an open-ended row.
     */
    suspend fun startSession(appPackageName: String, startTime: Long): Long

    /** Moves the session's end (and duration) to [endTime]. */
    suspend fun updateSessionEnd(sessionId: Long, endTime: Long)

    fun getSessionsForApp(packageName: String, startEpochMs: Long, endEpochMs: Long): Flow<List<UsageSession>>

    /**
     * Combined foreground time of [packageNames] inside `[fromMs, toMs)`.
     * Sessions that straddle either edge (for example one running across
     * midnight) only contribute the part inside the range.
     */
    suspend fun getUsageMs(packageNames: Collection<String>, fromMs: Long, toMs: Long): Long

    suspend fun getTodaySessionCount(packageName: String, dayStartEpochMs: Long): Int

    /** Sessions of every tracked app that overlap `[fromMs, toMs)`. Re-emits on change. */
    fun observeSessionsOverlapping(fromMs: Long, toMs: Long): Flow<List<UsageSession>>

    suspend fun recordUnblockEvent(event: UnblockEvent)
    fun getUnblockEventsForApp(packageName: String): Flow<List<UnblockEvent>>
    suspend fun getUnblockEventCountSince(packageName: String, sinceEpochMs: Long): Int

    /** Unblock decisions of every app with a timestamp in `[fromMs, toMs)`. Re-emits on change. */
    fun observeUnblockEventsBetween(fromMs: Long, toMs: Long): Flow<List<UnblockEvent>>
}
