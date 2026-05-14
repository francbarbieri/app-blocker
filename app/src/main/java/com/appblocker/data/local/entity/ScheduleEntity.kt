package com.appblocker.data.local.entity

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "schedules")
data class ScheduleEntity(
    @PrimaryKey(autoGenerate = true)
    val id: Long = 0,

    @ColumnInfo(name = "schedule_type")
    val scheduleType: String,

    @ColumnInfo(name = "start_time")
    val startTime: String? = null,

    @ColumnInfo(name = "end_time")
    val endTime: String? = null,

    @ColumnInfo(name = "daily_limit_minutes")
    val dailyLimitMinutes: Int? = null,

    @ColumnInfo(name = "is_active", defaultValue = "1")
    val isActive: Boolean = true
)
