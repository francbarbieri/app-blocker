package com.appblocker.data.local.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.Query
import com.appblocker.data.local.entity.UnblockEventEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface UnblockEventDao {

    @Insert
    suspend fun insert(event: UnblockEventEntity)

    @Query(
        """SELECT * FROM unblock_events
        WHERE app_package_name = :packageName
        ORDER BY timestamp DESC"""
    )
    fun getEventsForApp(packageName: String): Flow<List<UnblockEventEntity>>

    @Query(
        """SELECT COUNT(*) FROM unblock_events
        WHERE app_package_name = :packageName
        AND timestamp >= :sinceEpochMs"""
    )
    suspend fun getEventCountSince(packageName: String, sinceEpochMs: Long): Int

    @Query(
        """SELECT COUNT(*) FROM unblock_events
        WHERE app_package_name = :packageName
        AND user_proceeded = 1
        AND timestamp >= :sinceEpochMs"""
    )
    suspend fun getProceededCountSince(packageName: String, sinceEpochMs: Long): Int
}
