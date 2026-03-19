package com.appblocker.data.repository

import com.appblocker.data.local.dao.BlockedAppDao
import com.appblocker.data.local.dao.ScheduleDao
import com.appblocker.data.local.entity.BlockedAppEntity
import com.appblocker.data.local.entity.ScheduleEntity
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

    override suspend fun isBlockingEnabled(packageName: String): Boolean {
        return blockedAppDao.isBlockingEnabled(packageName) ?: false
    }

    override suspend fun setBlockingEnabled(packageName: String, enabled: Boolean) {
        blockedAppDao.setBlockingEnabled(packageName, enabled)
    }

    override suspend fun addSchedule(schedule: Schedule): Long {
        return scheduleDao.insertScheduleWithDays(schedule.toEntity(), schedule.days)
    }

    override suspend fun updateSchedule(schedule: Schedule) {
        scheduleDao.updateScheduleWithDays(schedule.toEntity(), schedule.days)
    }

    override suspend fun deleteSchedule(schedule: Schedule) {
        scheduleDao.deleteSchedule(schedule.toEntity())
    }

    override fun getSchedulesForApp(packageName: String): Flow<List<Schedule>> {
        return scheduleDao.getSchedulesForApp(packageName).map { entities ->
            val ids = entities.map { it.id }
            val allDays = if (ids.isNotEmpty()) {
                scheduleDao.getDaysForSchedules(ids).groupBy { it.scheduleId }
            } else {
                emptyMap()
            }
            entities.map { entity ->
                val days = allDays[entity.id]?.map { it.dayOfWeek } ?: emptyList()
                entity.toDomain(days)
            }
        }
    }

    override suspend fun getActiveSchedulesForApp(packageName: String): List<Schedule> {
        val entities = scheduleDao.getActiveSchedulesForApp(packageName)
        val ids = entities.map { it.id }
        val allDays = if (ids.isNotEmpty()) {
            scheduleDao.getDaysForSchedules(ids).groupBy { it.scheduleId }
        } else {
            emptyMap()
        }
        return entities.map { entity ->
            val days = allDays[entity.id]?.map { it.dayOfWeek } ?: emptyList()
            entity.toDomain(days)
        }
    }

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
        appPackageName = appPackageName,
        scheduleType = scheduleType.name,
        startTime = startTime,
        endTime = endTime,
        dailyLimitMinutes = dailyLimitMinutes,
        isActive = isActive
    )

    private fun ScheduleEntity.toDomain(days: List<Int>) = Schedule(
        id = id,
        appPackageName = appPackageName,
        scheduleType = ScheduleType.valueOf(scheduleType),
        startTime = startTime,
        endTime = endTime,
        dailyLimitMinutes = dailyLimitMinutes,
        isActive = isActive,
        days = days
    )
}
