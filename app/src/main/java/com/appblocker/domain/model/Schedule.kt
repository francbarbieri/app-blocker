package com.appblocker.domain.model

data class Schedule(
    val id: Long = 0,
    val appPackageNames: List<String> = emptyList(),
    val scheduleType: ScheduleType,
    val startTime: String? = null,
    val endTime: String? = null,
    val dailyLimitMinutes: Int? = null,
    val isActive: Boolean = true,
    val days: List<Int> = emptyList()
)
