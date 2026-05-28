package com.appblocker.data.local.dao

import androidx.room.Dao
import androidx.room.Delete
import androidx.room.Insert
import androidx.room.Query
import androidx.room.Transaction
import androidx.room.Update
import com.appblocker.data.local.entity.ScheduleAppEntity
import com.appblocker.data.local.entity.ScheduleDayEntity
import com.appblocker.data.local.entity.ScheduleEntity
import com.appblocker.data.local.relation.ScheduleWithDaysAndApps
import kotlinx.coroutines.flow.Flow

@Dao
interface ScheduleDao {

    @Insert
    suspend fun insertSchedule(schedule: ScheduleEntity): Long

    @Update
    suspend fun updateSchedule(schedule: ScheduleEntity)

    @Delete
    suspend fun deleteSchedule(schedule: ScheduleEntity)

    @Insert
    suspend fun insertDays(days: List<ScheduleDayEntity>)

    @Query("DELETE FROM schedule_days WHERE schedule_id = :scheduleId")
    suspend fun deleteDaysForSchedule(scheduleId: Long)

    @Insert
    suspend fun insertApps(apps: List<ScheduleAppEntity>)

    @Query("DELETE FROM schedule_apps WHERE schedule_id = :scheduleId")
    suspend fun deleteAppsForSchedule(scheduleId: Long)

    @Transaction
    @Query("SELECT * FROM schedules ORDER BY id DESC")
    fun getAllSchedulesWithDaysAndApps(): Flow<List<ScheduleWithDaysAndApps>>

    @Transaction
    @Query(
        """SELECT s.* FROM schedules s
        INNER JOIN schedule_apps sa ON sa.schedule_id = s.id
        WHERE sa.app_package_name = :packageName"""
    )
    fun getSchedulesWithDaysAndAppsForApp(packageName: String): Flow<List<ScheduleWithDaysAndApps>>

    @Transaction
    @Query(
        """SELECT s.* FROM schedules s
        INNER JOIN schedule_apps sa ON sa.schedule_id = s.id
        WHERE sa.app_package_name = :packageName AND s.is_active = 1"""
    )
    suspend fun getActiveSchedulesWithDaysAndAppsForApp(packageName: String): List<ScheduleWithDaysAndApps>

    @Transaction
    suspend fun insertScheduleWithDaysAndApps(
        schedule: ScheduleEntity,
        days: List<Int>,
        appPackageNames: List<String>
    ): Long {
        val scheduleId = insertSchedule(schedule)
        if (days.isNotEmpty()) {
            insertDays(days.map { ScheduleDayEntity(scheduleId, it) })
        }
        if (appPackageNames.isNotEmpty()) {
            insertApps(appPackageNames.map { ScheduleAppEntity(scheduleId, it) })
        }
        return scheduleId
    }

    @Transaction
    suspend fun updateScheduleWithDaysAndApps(
        schedule: ScheduleEntity,
        days: List<Int>,
        appPackageNames: List<String>
    ) {
        updateSchedule(schedule)
        deleteDaysForSchedule(schedule.id)
        deleteAppsForSchedule(schedule.id)
        if (days.isNotEmpty()) {
            insertDays(days.map { ScheduleDayEntity(schedule.id, it) })
        }
        if (appPackageNames.isNotEmpty()) {
            insertApps(appPackageNames.map { ScheduleAppEntity(schedule.id, it) })
        }
    }
}
