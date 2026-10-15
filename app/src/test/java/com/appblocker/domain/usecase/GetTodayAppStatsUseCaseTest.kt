package com.appblocker.domain.usecase

import com.appblocker.domain.model.TodayAppStats
import com.appblocker.domain.model.UnblockEvent
import com.appblocker.domain.model.UnblockOutcome
import com.appblocker.domain.util.Clock
import com.appblocker.presentation.fakes.FakeUsageRepository
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Test
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.LocalTime
import java.time.ZoneId

class GetTodayAppStatsUseCaseTest {

    private val zone = ZoneId.of("America/Argentina/Buenos_Aires")
    private val pkg = "com.example.social"
    private val today = LocalDate.of(2026, 10, 15)
    private val min = 60_000L

    private fun ms(date: LocalDate, time: String): Long =
        LocalDateTime.of(date, LocalTime.parse(time)).atZone(zone).toInstant().toEpochMilli()

    private fun useCase(repo: FakeUsageRepository, now: Long) =
        GetTodayAppStatsUseCase(repo, Clock { now }, zoneProvider = { zone })

    @Test
    fun `first overlay of the day is attempt one with no usage`() = runTest {
        val stats = useCase(FakeUsageRepository(), ms(today, "09:00"))(pkg)
        assertEquals(TodayAppStats(attempt = 1, usageMs = 0L), stats)
    }

    @Test
    fun `counts today's answered overlays of this app only`() = runTest {
        val repo = FakeUsageRepository()
        listOf(
            UnblockEvent(appPackageName = pkg, timestamp = ms(today.minusDays(1), "23:59"), outcome = UnblockOutcome.LEGITIMATE),
            UnblockEvent(appPackageName = pkg, timestamp = ms(today, "08:00"), outcome = UnblockOutcome.LEGITIMATE),
            UnblockEvent(appPackageName = pkg, timestamp = ms(today, "08:30"), outcome = UnblockOutcome.BACKED_OFF),
            UnblockEvent(appPackageName = "other", timestamp = ms(today, "08:40"), outcome = UnblockOutcome.LEGITIMATE),
        ).forEach { repo.recordUnblockEvent(it) }

        val stats = useCase(repo, ms(today, "09:00"))(pkg)

        assertEquals(3, stats.attempt)
    }

    @Test
    fun `usage counts from local midnight and clips a session that crossed it`() = runTest {
        val repo = FakeUsageRepository()
        repo.addSession(pkg, ms(today.minusDays(1), "23:50"), ms(today, "00:20"))
        repo.addSession(pkg, ms(today, "08:00"), ms(today, "08:15"))
        repo.addSession("other", ms(today, "08:00"), ms(today, "09:00"))

        val stats = useCase(repo, ms(today, "09:00"))(pkg)

        assertEquals(35 * min, stats.usageMs)
    }
}
