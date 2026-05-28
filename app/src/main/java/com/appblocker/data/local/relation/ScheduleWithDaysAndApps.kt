package com.appblocker.data.local.relation

import androidx.room.Embedded
import androidx.room.Relation
import com.appblocker.data.local.entity.ScheduleAppEntity
import com.appblocker.data.local.entity.ScheduleDayEntity
import com.appblocker.data.local.entity.ScheduleEntity

data class ScheduleWithDaysAndApps(
    @Embedded val schedule: ScheduleEntity,
    @Relation(parentColumn = "id", entityColumn = "schedule_id")
    val days: List<ScheduleDayEntity>,
    @Relation(parentColumn = "id", entityColumn = "schedule_id")
    val apps: List<ScheduleAppEntity>,
)
