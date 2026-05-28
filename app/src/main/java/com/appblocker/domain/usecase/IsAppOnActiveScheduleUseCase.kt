package com.appblocker.domain.usecase

import com.appblocker.domain.model.Schedule
import com.appblocker.domain.model.ScheduleType
import com.appblocker.domain.repository.BlockedAppRepository
import com.appblocker.domain.repository.UsageRepository
import com.appblocker.domain.util.Clock
import java.time.Instant
import java.time.LocalDate
import java.time.LocalTime
import java.time.ZoneId

class IsAppOnActiveScheduleUseCase(
    private val blockedAppRepository: BlockedAppRepository,
    private val usageRepository: UsageRepository,
    private val clock: Clock,
    private val zoneId: ZoneId = ZoneId.systemDefault(),
) {
    suspend operator fun invoke(packageName: String): Boolean {
        val schedules = blockedAppRepository.getActiveSchedulesForApp(packageName)
        if (schedules.isEmpty()) return true

        val nowInstant = Instant.ofEpochMilli(clock.nowMs())
        val nowTime = LocalTime.ofInstant(nowInstant, zoneId)
        val today = LocalDate.ofInstant(nowInstant, zoneId)
        val todayDayOfWeek = today.dayOfWeek.value

        return schedules.any { schedule ->
            isScheduleActive(schedule, nowTime, todayDayOfWeek, today, packageName)
        }
    }

    private suspend fun isScheduleActive(
        schedule: Schedule,
        now: LocalTime,
        todayDayOfWeek: Int,
        today: LocalDate,
        packageName: String,
    ): Boolean {
        if (schedule.days.isNotEmpty() && todayDayOfWeek !in schedule.days) return false

        return when (schedule.scheduleType) {
            ScheduleType.TIME_WINDOW -> {
                val start = schedule.startTime?.let { LocalTime.parse(it) } ?: return false
                val end = schedule.endTime?.let { LocalTime.parse(it) } ?: return false
                now in start..end
            }
            ScheduleType.DAILY_LIMIT -> {
                val limitMinutes = schedule.dailyLimitMinutes ?: return false
                val dayStartMs = today.atStartOfDay(zoneId).toInstant().toEpochMilli()
                val usedMs = usageRepository.getTodayTotalDurationMs(packageName, dayStartMs)
                val usedMinutes = usedMs / 60_000
                usedMinutes >= limitMinutes
            }
        }
    }
}
