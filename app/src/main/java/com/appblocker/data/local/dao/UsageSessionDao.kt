package com.appblocker.data.local.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.Query
import com.appblocker.data.local.entity.UsageSessionEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface UsageSessionDao {

    @Insert
    suspend fun insert(session: UsageSessionEntity): Long

    @Query("UPDATE usage_sessions SET end_time = :endTime, duration_ms = :endTime - start_time WHERE id = :sessionId")
    suspend fun updateEndTime(sessionId: Long, endTime: Long)

    @Query(
        """SELECT * FROM usage_sessions
        WHERE app_package_name = :packageName
        AND start_time >= :startEpochMs AND start_time <= :endEpochMs
        ORDER BY start_time DESC"""
    )
    fun getSessionsForApp(
        packageName: String,
        startEpochMs: Long,
        endEpochMs: Long
    ): Flow<List<UsageSessionEntity>>

    /**
     * Combined foreground time of [packageNames] inside [fromMs, toMs). Each
     * session is clipped to the range, so a session that started before
     * midnight only counts its after-midnight part toward "today".
     */
    @Query(
        """SELECT COALESCE(SUM(MIN(end_time, :toMs) - MAX(start_time, :fromMs)), 0)
        FROM usage_sessions
        WHERE app_package_name IN (:packageNames)
        AND end_time IS NOT NULL
        AND end_time > :fromMs
        AND start_time < :toMs"""
    )
    suspend fun getUsageMs(packageNames: List<String>, fromMs: Long, toMs: Long): Long

    @Query(
        """SELECT * FROM usage_sessions
        WHERE end_time IS NOT NULL
        AND end_time > :fromMs
        AND start_time < :toMs
        ORDER BY start_time ASC"""
    )
    fun observeSessionsOverlapping(fromMs: Long, toMs: Long): Flow<List<UsageSessionEntity>>

    @Query(
        """SELECT COUNT(*) FROM usage_sessions
        WHERE app_package_name = :packageName
        AND start_time >= :dayStartEpochMs"""
    )
    suspend fun getTodaySessionCount(packageName: String, dayStartEpochMs: Long): Int
}
