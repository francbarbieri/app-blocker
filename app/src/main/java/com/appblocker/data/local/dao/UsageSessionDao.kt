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

    @Query(
        """SELECT COALESCE(SUM(duration_ms), 0) FROM usage_sessions
        WHERE app_package_name = :packageName
        AND start_time >= :dayStartEpochMs"""
    )
    suspend fun getTodayTotalDurationMs(packageName: String, dayStartEpochMs: Long): Long

    @Query(
        """SELECT COALESCE(SUM(duration_ms), 0) FROM usage_sessions
        WHERE app_package_name = :packageName
        AND start_time >= :startEpochMs AND start_time <= :endEpochMs"""
    )
    suspend fun getTotalDurationForRange(
        packageName: String,
        startEpochMs: Long,
        endEpochMs: Long
    ): Long

    @Query(
        """SELECT COUNT(*) FROM usage_sessions
        WHERE app_package_name = :packageName
        AND start_time >= :dayStartEpochMs"""
    )
    suspend fun getTodaySessionCount(packageName: String, dayStartEpochMs: Long): Int
}
