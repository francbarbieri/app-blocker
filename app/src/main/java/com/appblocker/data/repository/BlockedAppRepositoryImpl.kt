package com.appblocker.data.repository

import com.appblocker.data.local.dao.BlockedAppDao
import com.appblocker.data.local.dao.ScheduleDao
import com.appblocker.data.local.entity.BlockedAppEntity
import com.appblocker.data.local.entity.ScheduleEntity
import com.appblocker.data.local.relation.ScheduleWithDaysAndApps
import com.appblocker.domain.model.BlockedApp
import com.appblocker.domain.model.Schedule
import com.appblocker.domain.model.ScheduleType
import com.appblocker.domain.repository.BlockedAppRepository
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

class BlockedAppRepositoryImpl(
    private val blockedAppDao: BlockedAppDao,
    private val scheduleDao: ScheduleDao
) : BlockedAppRepository {

    override suspend fun addApp(app: BlockedApp) {
        blockedAppDao.insert(app.toEntity())
    }

    override suspend fun removeApp(app: BlockedApp) {
        blockedAppDao.delete(app.toEntity())
    }

    override suspend fun getByPackageName(packageName: String): BlockedApp? {
        return blockedAppDao.getByPackageName(packageName)?.toDomain()
    }

    override fun getAllBlockedApps(): Flow<List<BlockedApp>> {
        return blockedAppDao.getAllBlockedApps().map { list ->
            list.map { it.toDomain() }
        }
    }

    override fun observeEnabledBlockedPackageNames(): Flow<Set<String>> {
        return blockedAppDao.getAllBlockedApps().map { list ->
            list.asSequence()
                .filter { it.isBlockingEnabled }
                .map { it.packageName }
                .toSet()
        }
    }

    override suspend fun isBlockingEnabled(packageName: String): Boolean {
        return blockedAppDao.isBlockingEnabled(packageName) ?: false
    }

    override suspend fun setBlockingEnabled(packageName: String, enabled: Boolean) {
        blockedAppDao.setBlockingEnabled(packageName, enabled)
    }

    override suspend fun addSchedule(schedule: Schedule): Long {
        return scheduleDao.insertScheduleWithDaysAndApps(
            schedule.toEntity(),
            schedule.days,
            schedule.appPackageNames
        )
    }

    override suspend fun updateSchedule(schedule: Schedule) {
        scheduleDao.updateScheduleWithDaysAndApps(
            schedule.toEntity(),
            schedule.days,
            schedule.appPackageNames
        )
    }

    override suspend fun deleteSchedule(schedule: Schedule) {
        scheduleDao.deleteSchedule(schedule.toEntity())
    }

    override fun getAllSchedules(): Flow<List<Schedule>> {
        return scheduleDao.getAllSchedulesWithDaysAndApps().map { list ->
            list.map { it.toDomain() }
        }
    }

    override fun getSchedulesForApp(packageName: String): Flow<List<Schedule>> {
        return scheduleDao.getSchedulesWithDaysAndAppsForApp(packageName).map { list ->
            list.map { it.toDomain() }
        }
    }

    override suspend fun getActiveSchedulesForApp(packageName: String): List<Schedule> {
        return scheduleDao.getActiveSchedulesWithDaysAndAppsForApp(packageName)
            .map { it.toDomain() }
    }

    private fun ScheduleWithDaysAndApps.toDomain(): Schedule = schedule.toDomain(
        apps = apps.map { it.appPackageName },
        days = days.map { it.dayOfWeek },
    )

    private fun BlockedApp.toEntity() = BlockedAppEntity(
        packageName = packageName,
        appName = appName,
        isBlockingEnabled = isBlockingEnabled,
        createdAt = createdAt
    )

    private fun BlockedAppEntity.toDomain() = BlockedApp(
        packageName = packageName,
        appName = appName,
        isBlockingEnabled = isBlockingEnabled,
        createdAt = createdAt
    )

    private fun Schedule.toEntity() = ScheduleEntity(
        id = id,
        scheduleType = scheduleType.name,
        startTime = startTime,
        endTime = endTime,
        dailyLimitMinutes = dailyLimitMinutes,
        isActive = isActive
    )

    private fun ScheduleEntity.toDomain(apps: List<String>, days: List<Int>) = Schedule(
        id = id,
        appPackageNames = apps,
        scheduleType = ScheduleType.valueOf(scheduleType),
        startTime = startTime,
        endTime = endTime,
        dailyLimitMinutes = dailyLimitMinutes,
        isActive = isActive,
        days = days
    )
}
