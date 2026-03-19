package com.appblocker.data.local.dao

import androidx.room.Dao
import androidx.room.Delete
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import com.appblocker.data.local.entity.BlockedAppEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface BlockedAppDao {

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(app: BlockedAppEntity)

    @Delete
    suspend fun delete(app: BlockedAppEntity)

    @Query("SELECT * FROM blocked_apps WHERE package_name = :packageName")
    suspend fun getByPackageName(packageName: String): BlockedAppEntity?

    @Query("SELECT * FROM blocked_apps ORDER BY app_name ASC")
    fun getAllBlockedApps(): Flow<List<BlockedAppEntity>>

    @Query("SELECT is_blocking_enabled FROM blocked_apps WHERE package_name = :packageName")
    suspend fun isBlockingEnabled(packageName: String): Boolean?

    @Query("UPDATE blocked_apps SET is_blocking_enabled = :enabled WHERE package_name = :packageName")
    suspend fun setBlockingEnabled(packageName: String, enabled: Boolean)
}
