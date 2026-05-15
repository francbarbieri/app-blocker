package com.appblocker.data.local.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.Query
import com.appblocker.data.local.entity.FocusSessionEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface FocusSessionDao {

    @Insert
    suspend fun insert(session: FocusSessionEntity): Long

    @Query(
        """SELECT * FROM focus_sessions
        WHERE ended_at IS NULL
        AND (expires_at IS NULL OR expires_at > :nowEpochMs)
        ORDER BY started_at DESC
        LIMIT 1"""
    )
    suspend fun getActiveSession(nowEpochMs: Long): FocusSessionEntity?

    @Query(
        """SELECT * FROM focus_sessions
        WHERE ended_at IS NULL
        ORDER BY started_at DESC
        LIMIT 1"""
    )
    fun observeLatestOpenSession(): Flow<FocusSessionEntity?>

    @Query("UPDATE focus_sessions SET ended_at = :nowEpochMs WHERE id = :id")
    suspend fun markEnded(id: Long, nowEpochMs: Long)
}
