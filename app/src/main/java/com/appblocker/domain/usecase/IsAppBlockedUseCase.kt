package com.appblocker.domain.usecase

import com.appblocker.domain.model.Schedule
import com.appblocker.domain.model.ScheduleType
import com.appblocker.domain.repository.BlockedAppRepository
import com.appblocker.domain.repository.FocusSessionRepository
import com.appblocker.domain.repository.UsageRepository
import java.time.LocalDate
import java.time.LocalTime
import java.time.ZoneId

class IsAppBlockedUseCase(
    private val blockedAppRepository: BlockedAppRepository,
    private val usageRepository: UsageRepository,
    private val focusSessionRepository: FocusSessionRepository,
) {
    suspend operator fun invoke(packageName: String): Boolean {
        if (!blockedAppRepository.isBlockingEnabled(packageName)) return false

        // Focus session overrides scheduling: while a session is active, every
        // app the user has marked for blocking is blocked.
        if (focusSessionRepository.getActiveSession() != null) return true

        val schedules = blockedAppRepository.getActiveSchedulesForApp(packageName)

        // No schedules means always blocked (when blocking is enabled)
        if (schedules.isEmpty()) return true

        val now = LocalTime.now()
        val todayDayOfWeek = LocalDate.now().dayOfWeek.value // 1=Monday, 7=Sunday

        return schedules.any { schedule -> isScheduleActive(schedule, now, todayDayOfWeek, packageName) }
    }

    private suspend fun isScheduleActive(
        schedule: Schedule,
        now: LocalTime,
        todayDayOfWeek: Int,
        packageName: String
    ): Boolean {
        // Check if today is one of the scheduled days
        if (schedule.days.isNotEmpty() && todayDayOfWeek !in schedule.days) {
            return false
        }

        return when (schedule.scheduleType) {
            ScheduleType.TIME_WINDOW -> {
                val start = schedule.startTime?.let { LocalTime.parse(it) } ?: return false
                val end = schedule.endTime?.let { LocalTime.parse(it) } ?: return false
                now in start..end
            }
            ScheduleType.DAILY_LIMIT -> {
                val limitMinutes = schedule.dailyLimitMinutes ?: return false
                val dayStartMs = LocalDate.now()
                    .atStartOfDay(ZoneId.systemDefault())
                    .toInstant()
                    .toEpochMilli()
                val usedMs = usageRepository.getTodayTotalDurationMs(packageName, dayStartMs)
                val usedMinutes = usedMs / 60_000
                usedMinutes >= limitMinutes
            }
        }
    }
}
