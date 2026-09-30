package com.appblocker.domain.repository

import com.appblocker.domain.model.BlockedApp
import com.appblocker.domain.model.Schedule
import kotlinx.coroutines.flow.Flow

interface BlockedAppRepository {
    suspend fun addApp(app: BlockedApp)
    suspend fun removeApp(app: BlockedApp)
    suspend fun getByPackageName(packageName: String): BlockedApp?
    fun getAllBlockedApps(): Flow<List<BlockedApp>>
    fun observeEnabledBlockedPackageNames(): Flow<Set<String>>
    suspend fun isBlockingEnabled(packageName: String): Boolean
    suspend fun setBlockingEnabled(packageName: String, enabled: Boolean)

    suspend fun addSchedule(schedule: Schedule): Long
    suspend fun updateSchedule(schedule: Schedule)
    suspend fun deleteSchedule(schedule: Schedule)
    fun getAllSchedules(): Flow<List<Schedule>>
    fun getSchedulesForApp(packageName: String): Flow<List<Schedule>>

    /**
     * Every schedule that lists [packageName], paused ones included. Callers
     * need the paused ones to tell "no schedules" (always blocked) apart from
     * "only paused schedules" (not blocked by a schedule right now).
     */
    suspend fun getAllSchedulesForApp(packageName: String): List<Schedule>
}
