package com.appblocker.data.local.entity

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "blocked_apps")
data class BlockedAppEntity(
    @PrimaryKey
    @ColumnInfo(name = "package_name")
    val packageName: String,

    @ColumnInfo(name = "app_name")
    val appName: String,

    @ColumnInfo(name = "is_blocking_enabled", defaultValue = "1")
    val isBlockingEnabled: Boolean = true,

    @ColumnInfo(name = "created_at")
    val createdAt: Long
)
