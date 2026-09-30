package com.appblocker.domain.model

import java.time.LocalDate
import java.time.LocalTime

/**
 * Pure time-window math for [ScheduleType.TIME_WINDOW] schedules.
 *
 * Windows are half-open: `[start, end)`. Days use ISO numbering
 * (1 = Monday … 7 = Sunday); an empty set means every day.
 *
 * - `start < end` — a same-day window, e.g. 09:00–17:00.
 * - `start > end` — an overnight window, e.g. 22:00–06:00. The part after
 *   midnight belongs to the day the window *started*, so a Friday-only
 *   22:00–06:00 schedule still blocks at 01:00 on Saturday.
 * - `start == end` — the whole selected day.
 */
object TimeWindowRules {

    fun isActive(
        start: LocalTime,
        end: LocalTime,
        days: Collection<Int>,
        date: LocalDate,
        now: LocalTime,
    ): Boolean {
        fun appliesOn(day: LocalDate) = days.isEmpty() || day.dayOfWeek.value in days

        return when {
            start == end -> appliesOn(date)
            start < end -> appliesOn(date) && now >= start && now < end
            else -> (appliesOn(date) && now >= start) ||
                (appliesOn(date.minusDays(1)) && now < end)
        }
    }

    /** True when the window runs past midnight into the next day. */
    fun isOvernight(start: LocalTime, end: LocalTime): Boolean = start > end

    /** True when the window covers the whole day. */
    fun isAllDay(start: LocalTime, end: LocalTime): Boolean = start == end
}
