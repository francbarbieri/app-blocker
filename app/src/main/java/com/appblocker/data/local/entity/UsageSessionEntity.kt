package com.appblocker.data.local.entity

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey

@Entity(
    tableName = "usage_sessions",
    foreignKeys = [
        ForeignKey(
            entity = BlockedAppEntity::class,
            parentColumns = ["package_name"],
            childColumns = ["app_package_name"],
            onDelete = ForeignKey.CASCADE
        )
    ],
    indices = [Index("app_package_name", "start_time")]
)
data class UsageSessionEntity(
    @PrimaryKey(autoGenerate = true)
    val id: Long = 0,

    @ColumnInfo(name = "app_package_name")
    val appPackageName: String,

    @ColumnInfo(name = "start_time")
    val startTime: Long,

    @ColumnInfo(name = "end_time")
    val endTime: Long? = null,

    @ColumnInfo(name = "duration_ms")
    val durationMs: Long? = null
)
