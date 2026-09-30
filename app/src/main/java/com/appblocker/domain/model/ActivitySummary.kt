package com.appblocker.domain.model

import java.time.LocalDate
import java.time.ZoneId

/** How the user answered block overlays in a period. */
data class OutcomeCounts(
    val planned: Int = 0,
    val walkedAway: Int = 0,
    val openedAnyway: Int = 0,
) {
    val total: Int get() = planned + walkedAway + openedAnyway

    /**
     * Share of unplanned attempts the user resisted, 0..1, or null when there
     * were none. Planned opens are excluded: they were never temptations.
     */
    val resistRate: Float?
        get() {
            val unplanned = walkedAway + openedAnyway
            return if (unplanned == 0) null else walkedAway.toFloat() / unplanned
        }

    operator fun plus(other: OutcomeCounts) = OutcomeCounts(
        planned = planned + other.planned,
        walkedAway = walkedAway + other.walkedAway,
        openedAnyway = openedAnyway + other.openedAnyway,
    )

    companion object {
        fun of(outcomes: Iterable<UnblockOutcome>): OutcomeCounts {
            var planned = 0
            var walkedAway = 0
            var openedAnyway = 0
            outcomes.forEach {
                when (it) {
                    UnblockOutcome.LEGITIMATE -> planned++
                    UnblockOutcome.BACKED_OFF -> walkedAway++
                    UnblockOutcome.BROKE_PLAN_PROCEEDED -> openedAnyway++
                }
            }
            return OutcomeCounts(planned, walkedAway, openedAnyway)
        }
    }
}

data class DayUsage(val date: LocalDate, val usageMs: Long)

data class AppActivity(
    val packageName: String,
    val appName: String,
    val usageMs: Long,
    val outcomes: OutcomeCounts,
)

/** Usage and overlay decisions for the last [days] days, today included. */
data class ActivitySummary(
    val days: Int,
    val totalUsageMs: Long,
    val outcomes: OutcomeCounts,
    /** One entry per day, oldest first; always [days] long. */
    val daily: List<DayUsage>,
    /** Apps with any usage or decisions, most used first. */
    val apps: List<AppActivity>,
) {
    companion object {
        /**
         * Builds a summary for the [days] days ending on [lastDay]. Sessions
         * that cross midnight are split between the days they touch, and
         * anything outside the window is clipped away.
         */
        fun build(
            sessions: List<UsageSession>,
            events: List<UnblockEvent>,
            appNames: Map<String, String>,
            lastDay: LocalDate,
            days: Int,
            zone: ZoneId,
        ): ActivitySummary {
            require(days >= 1) { "days must be >= 1" }
            val dates = (days - 1 downTo 0).map { lastDay.minusDays(it.toLong()) }
            val boundaries = (dates + lastDay.plusDays(1)).map { it.atStartOfDay(zone).toInstant().toEpochMilli() }
            val windowStart = boundaries.first()
            val windowEnd = boundaries.last()

            val dailyMs = LongArray(days)
            val usageByApp = HashMap<String, Long>()
            sessions.forEach { session ->
                val start = maxOf(session.startTime, windowStart)
                val end = minOf(session.endTime ?: session.startTime, windowEnd)
                if (end <= start) return@forEach
                usageByApp.merge(session.appPackageName, end - start, Long::plus)
                for (i in 0 until days) {
                    val overlap = minOf(end, boundaries[i + 1]) - maxOf(start, boundaries[i])
                    if (overlap > 0) dailyMs[i] += overlap
                }
            }

            val eventsInWindow = events.filter { it.timestamp in windowStart until windowEnd }
            val outcomesByApp = eventsInWindow
                .groupBy { it.appPackageName }
                .mapValues { (_, list) -> OutcomeCounts.of(list.map { it.outcome }) }

            val apps = (usageByApp.keys + outcomesByApp.keys)
                .map { pkg ->
                    AppActivity(
                        packageName = pkg,
                        appName = appNames[pkg] ?: pkg,
                        usageMs = usageByApp[pkg] ?: 0L,
                        outcomes = outcomesByApp[pkg] ?: OutcomeCounts(),
                    )
                }
                .sortedWith(
                    compareByDescending<AppActivity> { it.usageMs }
                        .thenByDescending { it.outcomes.total }
                        .thenBy { it.appName.lowercase() }
                )

            return ActivitySummary(
                days = days,
                totalUsageMs = dailyMs.sum(),
                outcomes = OutcomeCounts.of(eventsInWindow.map { it.outcome }),
                daily = dates.mapIndexed { i, date -> DayUsage(date, dailyMs[i]) },
                apps = apps,
            )
        }
    }
}
