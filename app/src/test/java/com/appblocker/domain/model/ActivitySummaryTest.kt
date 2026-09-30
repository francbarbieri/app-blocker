package com.appblocker.domain.model

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.LocalTime
import java.time.ZoneId

class ActivitySummaryTest {

    private val zone = ZoneId.of("America/Argentina/Buenos_Aires")
    private val today = LocalDate.of(2026, 9, 29)
    private val min = 60_000L

    private fun ms(date: LocalDate, time: String): Long =
        LocalDateTime.of(date, LocalTime.parse(time)).atZone(zone).toInstant().toEpochMilli()

    private fun session(pkg: String, start: Long, end: Long) =
        UsageSession(appPackageName = pkg, startTime = start, endTime = end, durationMs = end - start)

    private fun event(pkg: String, at: Long, outcome: UnblockOutcome) =
        UnblockEvent(appPackageName = pkg, timestamp = at, outcome = outcome)

    @Test
    fun `session crossing midnight is split between the two days`() {
        val summary = ActivitySummary.build(
            sessions = listOf(session("a", ms(today.minusDays(1), "23:40"), ms(today, "00:10"))),
            events = emptyList(),
            appNames = mapOf("a" to "App A"),
            lastDay = today,
            days = 7,
            zone = zone,
        )
        assertEquals(7, summary.daily.size)
        assertEquals(today, summary.daily.last().date)
        assertEquals(20 * min, summary.daily[5].usageMs)
        assertEquals(10 * min, summary.daily[6].usageMs)
        assertEquals(30 * min, summary.totalUsageMs)
    }

    @Test
    fun `usage outside the window is clipped away`() {
        val summary = ActivitySummary.build(
            sessions = listOf(session("a", ms(today.minusDays(1), "23:00"), ms(today, "00:30"))),
            events = emptyList(),
            appNames = emptyMap(),
            lastDay = today,
            days = 1,
            zone = zone,
        )
        assertEquals(30 * min, summary.totalUsageMs)
        assertEquals(30 * min, summary.apps.single().usageMs)
    }

    @Test
    fun `outcomes are counted overall and per app`() {
        val at = ms(today, "10:00")
        val summary = ActivitySummary.build(
            sessions = emptyList(),
            events = listOf(
                event("a", at, UnblockOutcome.BACKED_OFF),
                event("a", at, UnblockOutcome.BACKED_OFF),
                event("a", at, UnblockOutcome.BROKE_PLAN_PROCEEDED),
                event("b", at, UnblockOutcome.LEGITIMATE),
                // Yesterday: outside a 1-day window.
                event("b", ms(today.minusDays(1), "10:00"), UnblockOutcome.BACKED_OFF),
            ),
            appNames = mapOf("a" to "App A", "b" to "App B"),
            lastDay = today,
            days = 1,
            zone = zone,
        )
        assertEquals(OutcomeCounts(planned = 1, walkedAway = 2, openedAnyway = 1), summary.outcomes)
        assertEquals(2f / 3f, summary.outcomes.resistRate!!, 0.0001f)
        val byPkg = summary.apps.associateBy { it.packageName }
        assertEquals(OutcomeCounts(walkedAway = 2, openedAnyway = 1), byPkg.getValue("a").outcomes)
        assertEquals(OutcomeCounts(planned = 1), byPkg.getValue("b").outcomes)
    }

    @Test
    fun `apps are sorted by usage and fall back to the package name`() {
        val summary = ActivitySummary.build(
            sessions = listOf(
                session("small", ms(today, "09:00"), ms(today, "09:05")),
                session("big", ms(today, "10:00"), ms(today, "11:00")),
            ),
            events = emptyList(),
            appNames = mapOf("big" to "Big App"),
            lastDay = today,
            days = 1,
            zone = zone,
        )
        assertEquals(listOf("big", "small"), summary.apps.map { it.packageName })
        assertEquals("Big App", summary.apps[0].appName)
        assertEquals("small", summary.apps[1].appName)
    }

    @Test
    fun `resist rate ignores planned opens and is null without temptations`() {
        assertNull(OutcomeCounts(planned = 4).resistRate)
        assertEquals(0.5f, OutcomeCounts(planned = 4, walkedAway = 1, openedAnyway = 1).resistRate!!, 0f)
    }
}
