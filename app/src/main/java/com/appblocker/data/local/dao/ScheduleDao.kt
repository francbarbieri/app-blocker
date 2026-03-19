package com.appblocker.data.local.dao

import androidx.room.Dao
import androidx.room.Delete
import androidx.room.Insert
import androidx.room.Query
import androidx.room.Transaction
import androidx.room.Update
import com.appblocker.data.local.entity.ScheduleDayEntity
import com.appblocker.data.local.entity.ScheduleEntity
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

    @Query("SELECT * FROM schedules WHERE app_package_name = :packageName")
    fun getSchedulesForApp(packageName: String): Flow<List<ScheduleEntity>>

    @Query("SELECT * FROM schedules WHERE app_package_name = :packageName AND is_active = 1")
    suspend fun getActiveSchedulesForApp(packageName: String): List<ScheduleEntity>

    @Query("SELECT * FROM schedule_days WHERE schedule_id = :scheduleId")
    suspend fun getDaysForSchedule(scheduleId: Long): List<ScheduleDayEntity>

    @Query("SELECT * FROM schedule_days WHERE schedule_id IN (:scheduleIds)")
    suspend fun getDaysForSchedules(scheduleIds: List<Long>): List<ScheduleDayEntity>

    @Transaction
    suspend fun insertScheduleWithDays(schedule: ScheduleEntity, days: List<Int>): Long {
        val scheduleId = insertSchedule(schedule)
        if (days.isNotEmpty()) {
            insertDays(days.map { ScheduleDayEntity(scheduleId, it) })
        }
        return scheduleId
    }

    @Transaction
    suspend fun updateScheduleWithDays(schedule: ScheduleEntity, days: List<Int>) {
        updateSchedule(schedule)
        deleteDaysForSchedule(schedule.id)
        if (days.isNotEmpty()) {
            insertDays(days.map { ScheduleDayEntity(schedule.id, it) })
        }
    }
}
