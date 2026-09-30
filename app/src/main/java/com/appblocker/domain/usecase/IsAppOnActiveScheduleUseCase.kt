package com.appblocker.domain.usecase

import com.appblocker.domain.model.BlockReason
import com.appblocker.domain.model.Schedule
import com.appblocker.domain.model.ScheduleType
import com.appblocker.domain.model.TimeWindowRules
import com.appblocker.domain.repository.BlockedAppRepository
import com.appblocker.domain.repository.UsageRepository
import com.appblocker.domain.util.Clock
import java.time.Instant
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.LocalTime
import java.time.ZoneId
import java.time.format.DateTimeParseException

/**
 * Evaluates an app's schedules against the current time.
 *
 * - An app with no schedules at all is always blocked ([BlockReason.AlwaysBlocked]).
 * - Otherwise only active (not paused) schedules can block. A matching time
 *   window wins over a reached daily limit, because its reason ("until 17:00")
 *   is the more specific one to show.
 * - Daily limits count today's foreground time from local midnight, including
 *   the session that is still running. A limit on several apps is a shared
 *   budget: the apps' usage is added up.
 */
class IsAppOnActiveScheduleUseCase(
    private val blockedAppRepository: BlockedAppRepository,
    private val usageRepository: UsageRepository,
    private val clock: Clock,
    private val zoneProvider: () -> ZoneId = ZoneId::systemDefault,
) {
    suspend operator fun invoke(packageName: String): BlockReason? {
        val schedules = blockedAppRepository.getAllSchedulesForApp(packageName)
        if (schedules.isEmpty()) return BlockReason.AlwaysBlocked

        val active = schedules.filter { it.isActive }
        if (active.isEmpty()) return null

        val zone = zoneProvider()
        val nowMs = clock.nowMs()
        val now = Instant.ofEpochMilli(nowMs).atZone(zone)
        val today = now.toLocalDate()
        val timeOfDay = now.toLocalTime()

        activeWindowReason(active, today, timeOfDay)?.let { return it }

        val todaysLimits = active.filter {
            it.scheduleType == ScheduleType.DAILY_LIMIT && it.dailyLimitMinutes != null && it.appliesOn(today)
        }
        if (todaysLimits.isEmpty()) return null

        // A limit on several apps is one shared budget: their usage adds up.
        val dayStartMs = today.atStartOfDay(zone).toInstant().toEpochMilli()
        val reachedLimit = todaysLimits
            .mapNotNull { schedule ->
                val limitMinutes = schedule.dailyLimitMinutes ?: return@mapNotNull null
                val apps = schedule.appPackageNames.toSet() + packageName
                val usedMs = usageRepository.getUsageMs(apps, dayStartMs, nowMs)
                limitMinutes.takeIf { usedMs >= it * MS_PER_MINUTE }
            }
            .minOrNull()
        return reachedLimit?.let { BlockReason.DailyLimitReached(it) }
    }

    /**
     * The reason for the active time windows, or null if none is active.
     * With overlapping windows the latest end wins, since the app stays
     * blocked until then; an active all-day window beats any end time.
     */
    private fun activeWindowReason(
        schedules: List<Schedule>,
        today: LocalDate,
        timeOfDay: LocalTime,
    ): BlockReason? {
        var allDay = false
        var latestEnd: LocalDateTime? = null
        var latestEndLabel: String? = null
        for (schedule in schedules) {
            if (schedule.scheduleType != ScheduleType.TIME_WINDOW) continue
            val start = parseTimeOrNull(schedule.startTime) ?: continue
            val end = parseTimeOrNull(schedule.endTime) ?: continue
            if (!TimeWindowRules.isActive(start, end, schedule.days, today, timeOfDay)) continue
            if (TimeWindowRules.isAllDay(start, end)) {
                allDay = true
                continue
            }
            // An overnight window that started this evening ends tomorrow.
            val endDate = if (TimeWindowRules.isOvernight(start, end) && timeOfDay >= start) {
                today.plusDays(1)
            } else {
                today
            }
            val endAt = LocalDateTime.of(endDate, end)
            if (latestEnd == null || endAt > latestEnd) {
                latestEnd = endAt
                latestEndLabel = schedule.endTime
            }
        }
        return when {
            allDay -> BlockReason.ScheduledWindow(until = null)
            latestEnd != null -> BlockReason.ScheduledWindow(until = latestEndLabel)
            else -> null
        }
    }

    private fun Schedule.appliesOn(date: LocalDate): Boolean =
        days.isEmpty() || date.dayOfWeek.value in days

    private fun parseTimeOrNull(value: String?): LocalTime? = try {
        value?.let(LocalTime::parse)
    } catch (_: DateTimeParseException) {
        null
    }

    private companion object {
        const val MS_PER_MINUTE = 60_000L
    }
}
