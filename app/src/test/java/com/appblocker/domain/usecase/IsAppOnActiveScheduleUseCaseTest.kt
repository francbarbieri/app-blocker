package com.appblocker.domain.usecase

import com.appblocker.domain.model.BlockReason
import com.appblocker.domain.model.Schedule
import com.appblocker.domain.model.ScheduleType
import com.appblocker.domain.util.Clock
import com.appblocker.presentation.fakes.FakeBlockedAppRepository
import com.appblocker.presentation.fakes.FakeUsageRepository
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.ZoneId

class IsAppOnActiveScheduleUseCaseTest {

    private val zone = ZoneId.of("America/Argentina/Buenos_Aires")
    private val pkg = "com.example.social"
    private val friday = LocalDate.of(2026, 9, 25)

    private fun ms(date: LocalDate, time: String): Long =
        LocalDateTime.of(date, java.time.LocalTime.parse(time)).atZone(zone).toInstant().toEpochMilli()

    private fun window(start: String, end: String, days: List<Int> = emptyList(), active: Boolean = true) =
        Schedule(
            id = 0,
            appPackageNames = listOf(pkg),
            scheduleType = ScheduleType.TIME_WINDOW,
            startTime = start,
            endTime = end,
            isActive = active,
            days = days,
        )

    private fun limit(
        minutes: Int,
        days: List<Int> = emptyList(),
        active: Boolean = true,
        apps: List<String> = listOf(pkg),
    ) =
        Schedule(
            id = 0,
            appPackageNames = apps,
            scheduleType = ScheduleType.DAILY_LIMIT,
            dailyLimitMinutes = minutes,
            isActive = active,
            days = days,
        )

    private class Setup(
        val useCase: IsAppOnActiveScheduleUseCase,
        val usage: FakeUsageRepository,
    )

    private fun setup(nowMs: Long, vararg schedules: Schedule): Setup {
        val repo = FakeBlockedAppRepository(schedules = schedules.toList())
        val usage = FakeUsageRepository()
        val useCase = IsAppOnActiveScheduleUseCase(repo, usage, Clock { nowMs }) { zone }
        return Setup(useCase, usage)
    }

    @Test
    fun `app without schedules is always blocked`() = runTest {
        val s = setup(ms(friday, "12:00"))
        assertEquals(BlockReason.AlwaysBlocked, s.useCase(pkg))
    }

    @Test
    fun `app whose only schedules are paused is not blocked`() = runTest {
        val s = setup(ms(friday, "12:00"), window("09:00", "17:00", active = false))
        assertNull(s.useCase(pkg))
    }

    @Test
    fun `active time window reports its end time`() = runTest {
        val s = setup(ms(friday, "12:00"), window("09:00", "17:00"))
        assertEquals(BlockReason.ScheduledWindow(until = "17:00"), s.useCase(pkg))
    }

    @Test
    fun `outside every window the app is not blocked`() = runTest {
        val s = setup(ms(friday, "18:00"), window("09:00", "17:00"))
        assertNull(s.useCase(pkg))
    }

    @Test
    fun `overnight window blocks after midnight`() = runTest {
        val s = setup(ms(friday.plusDays(1), "01:30"), window("22:00", "06:00", days = listOf(5)))
        assertEquals(BlockReason.ScheduledWindow(until = "06:00"), s.useCase(pkg))
    }

    @Test
    fun `all-day window reports no end time`() = runTest {
        val s = setup(ms(friday, "03:00"), window("00:00", "00:00"))
        assertEquals(BlockReason.ScheduledWindow(until = null), s.useCase(pkg))
    }

    @Test
    fun `daily limit blocks once today's usage reaches it`() = runTest {
        val now = ms(friday, "20:00")
        val s = setup(now, limit(30))
        s.usage.addSession(pkg, ms(friday, "10:00"), ms(friday, "10:20"))
        assertNull(s.useCase(pkg))

        s.usage.addSession(pkg, ms(friday, "15:00"), ms(friday, "15:10"))
        assertEquals(BlockReason.DailyLimitReached(limitMinutes = 30), s.useCase(pkg))
    }

    @Test
    fun `daily limit counts only today's part of a session that crossed midnight`() = runTest {
        val now = ms(friday, "00:40")
        val s = setup(now, limit(30))
        // 23:30 yesterday → 00:20 today: only 20 minutes belong to today.
        s.usage.addSession(pkg, ms(friday.minusDays(1), "23:30"), ms(friday, "00:20"))
        assertNull(s.useCase(pkg))

        val (queriedPkgs, from, to) = s.usage.usageQueries.single()
        assertEquals(setOf(pkg), queriedPkgs)
        assertEquals(ms(friday, "00:00"), from)
        assertEquals(now, to)
    }

    @Test
    fun `daily limit on other days does not apply today`() = runTest {
        val s = setup(ms(friday, "20:00"), limit(10, days = listOf(6, 7)))
        s.usage.addSession(pkg, ms(friday, "10:00"), ms(friday, "11:00"))
        assertNull(s.useCase(pkg))
    }

    @Test
    fun `smallest reached limit is reported`() = runTest {
        val s = setup(ms(friday, "20:00"), limit(60), limit(15))
        s.usage.addSession(pkg, ms(friday, "10:00"), ms(friday, "11:30"))
        assertEquals(BlockReason.DailyLimitReached(limitMinutes = 15), s.useCase(pkg))
    }

    @Test
    fun `active time window wins over a reached daily limit`() = runTest {
        val s = setup(ms(friday, "12:00"), limit(5), window("09:00", "17:00"))
        s.usage.addSession(pkg, ms(friday, "08:00"), ms(friday, "09:00"))
        assertEquals(BlockReason.ScheduledWindow(until = "17:00"), s.useCase(pkg))
    }

    @Test
    fun `a limit on several apps is one shared budget`() = runTest {
        val other = "com.example.video"
        val s = setup(ms(friday, "20:00"), limit(30, apps = listOf(pkg, other)))
        s.usage.addSession(pkg, ms(friday, "10:00"), ms(friday, "10:20"))
        assertNull("20 of 30 shared minutes used", s.useCase(pkg))

        s.usage.addSession(other, ms(friday, "12:00"), ms(friday, "12:10"))
        assertEquals(BlockReason.DailyLimitReached(limitMinutes = 30), s.useCase(pkg))
        assertEquals(setOf(pkg, other), s.usage.usageQueries.last().first)
    }

    @Test
    fun `overlapping windows report the latest end`() = runTest {
        val s = setup(ms(friday, "11:30"), window("09:00", "12:00"), window("11:00", "17:00"))
        assertEquals(BlockReason.ScheduledWindow(until = "17:00"), s.useCase(pkg))
    }

    @Test
    fun `an overnight window that started tonight outlasts a same-day window`() = runTest {
        val s = setup(ms(friday, "22:30"), window("21:00", "23:00"), window("22:00", "06:00"))
        assertEquals(BlockReason.ScheduledWindow(until = "06:00"), s.useCase(pkg))
    }

    @Test
    fun `an active all-day window beats any end time`() = runTest {
        val s = setup(ms(friday, "10:00"), window("09:00", "17:00"), window("00:00", "00:00"))
        assertEquals(BlockReason.ScheduledWindow(until = null), s.useCase(pkg))
    }

    @Test
    fun `malformed times are ignored instead of crashing`() = runTest {
        val s = setup(ms(friday, "12:00"), window("9am", "17:00"))
        assertNull(s.useCase(pkg))
    }
}
