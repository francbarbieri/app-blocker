package com.appblocker.domain.repository

import com.appblocker.domain.model.BlockedApp
import com.appblocker.domain.model.Schedule
import kotlinx.coroutines.flow.Flow

interface BlockedAppRepository {
    suspend fun addApp(app: BlockedApp)
    suspend fun removeApp(app: BlockedApp)
    suspend fun getByPackageName(packageName: String): BlockedApp?
    fun getAllBlockedApps(): Flow<List<BlockedApp>>
    suspend fun isBlockingEnabled(packageName: String): Boolean
    suspend fun setBlockingEnabled(packageName: String, enabled: Boolean)

    suspend fun addSchedule(schedule: Schedule): Long
    suspend fun updateSchedule(schedule: Schedule)
    suspend fun deleteSchedule(schedule: Schedule)
    fun getSchedulesForApp(packageName: String): Flow<List<Schedule>>
    suspend fun getActiveSchedulesForApp(packageName: String): List<Schedule>
}
