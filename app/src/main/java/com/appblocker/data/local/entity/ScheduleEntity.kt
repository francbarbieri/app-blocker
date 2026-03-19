package com.appblocker.data.local.entity

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey

@Entity(
    tableName = "schedules",
    foreignKeys = [
        ForeignKey(
            entity = BlockedAppEntity::class,
            parentColumns = ["package_name"],
            childColumns = ["app_package_name"],
            onDelete = ForeignKey.CASCADE
        )
    ],
    indices = [Index("app_package_name")]
)
data class ScheduleEntity(
    @PrimaryKey(autoGenerate = true)
    val id: Long = 0,

    @ColumnInfo(name = "app_package_name")
    val appPackageName: String,

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
