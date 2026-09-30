package com.appblocker.presentation.fakes

import com.appblocker.domain.model.BlockedApp
import com.appblocker.domain.model.Schedule
import com.appblocker.domain.repository.BlockedAppRepository
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.map

/** In-memory [BlockedAppRepository] that behaves like the Room-backed one. */
class FakeBlockedAppRepository(
    apps: List<BlockedApp> = emptyList(),
    schedules: List<Schedule> = emptyList(),
) : BlockedAppRepository {
    val apps = MutableStateFlow(apps)
    val schedules = MutableStateFlow(schedules)

    override suspend fun addApp(app: BlockedApp) {
        apps.value = apps.value.filterNot { it.packageName == app.packageName } + app
    }

    override suspend fun removeApp(app: BlockedApp) {
        apps.value = apps.value.filterNot { it.packageName == app.packageName }
    }

    override suspend fun getByPackageName(packageName: String): BlockedApp? =
        apps.value.firstOrNull { it.packageName == packageName }

    override fun getAllBlockedApps(): Flow<List<BlockedApp>> = apps

    override fun observeEnabledBlockedPackageNames(): Flow<Set<String>> =
        apps.map { list -> list.filter { it.isBlockingEnabled }.map { it.packageName }.toSet() }

    override suspend fun isBlockingEnabled(packageName: String): Boolean =
        getByPackageName(packageName)?.isBlockingEnabled ?: false

    override suspend fun setBlockingEnabled(packageName: String, enabled: Boolean) {
        apps.value = apps.value.map {
            if (it.packageName == packageName) it.copy(isBlockingEnabled = enabled) else it
        }
    }

    override suspend fun addSchedule(schedule: Schedule): Long {
        val id = (schedules.value.maxOfOrNull { it.id } ?: 0L) + 1L
        schedules.value = schedules.value + schedule.copy(id = id)
        return id
    }

    override suspend fun updateSchedule(schedule: Schedule) {
        schedules.value = schedules.value.map { if (it.id == schedule.id) schedule else it }
    }

    override suspend fun deleteSchedule(schedule: Schedule) {
        schedules.value = schedules.value.filterNot { it.id == schedule.id }
    }

    override fun getAllSchedules(): Flow<List<Schedule>> = schedules

    override fun getSchedulesForApp(packageName: String): Flow<List<Schedule>> =
        schedules.map { list -> list.filter { packageName in it.appPackageNames } }

    override suspend fun getAllSchedulesForApp(packageName: String): List<Schedule> =
        schedules.value.filter { packageName in it.appPackageNames }
}
