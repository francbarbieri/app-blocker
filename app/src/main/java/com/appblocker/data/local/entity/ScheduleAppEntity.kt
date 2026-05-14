package com.appblocker.data.local.entity

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index

@Entity(
    tableName = "schedule_apps",
    primaryKeys = ["schedule_id", "app_package_name"],
    foreignKeys = [
        ForeignKey(
            entity = ScheduleEntity::class,
            parentColumns = ["id"],
            childColumns = ["schedule_id"],
            onDelete = ForeignKey.CASCADE
        ),
        ForeignKey(
            entity = BlockedAppEntity::class,
            parentColumns = ["package_name"],
            childColumns = ["app_package_name"],
            onDelete = ForeignKey.CASCADE
        )
    ],
    indices = [Index("app_package_name")]
)
data class ScheduleAppEntity(
    @ColumnInfo(name = "schedule_id")
    val scheduleId: Long,

    @ColumnInfo(name = "app_package_name")
    val appPackageName: String
)
