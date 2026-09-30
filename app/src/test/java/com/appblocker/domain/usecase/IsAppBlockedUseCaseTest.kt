package com.appblocker.domain.usecase

import com.appblocker.domain.model.BlockReason
import com.appblocker.domain.model.BlockedApp
import com.appblocker.domain.model.FocusSession
import com.appblocker.domain.model.Schedule
import com.appblocker.domain.model.ScheduleType
import com.appblocker.domain.repository.FocusSessionRepository
import com.appblocker.domain.util.Clock
import com.appblocker.presentation.fakes.FakeBlockedAppRepository
import com.appblocker.presentation.fakes.FakeUsageRepository
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test
import java.time.ZoneOffset

class IsAppBlockedUseCaseTest {

    private val pkg = "com.example.video"

    // 1970-01-01T12:00Z, a Thursday.
    private val noon = 12 * 60 * 60_000L

    private class FakeFocusRepository(var active: FocusSession?) : FocusSessionRepository {
        override suspend fun startSession(durationMs: Long?) = error("unused")
        override suspend fun endActiveSession() = Unit
        override suspend fun getActiveSession(): FocusSession? = active
        override fun observeLatestOpenSession(): Flow<FocusSession?> = flowOf(active)
    }

    private fun useCase(
        enabled: Boolean = true,
        focus: FocusSession? = null,
        schedules: List<Schedule> = emptyList(),
    ): IsAppBlockedUseCase {
        val apps = FakeBlockedAppRepository(
            apps = listOf(BlockedApp(pkg, "Video", isBlockingEnabled = enabled, createdAt = 0L)),
            schedules = schedules,
        )
        return IsAppBlockedUseCase(
            blockedAppRepository = apps,
            isFocusSessionActive = IsFocusSessionActiveUseCase(FakeFocusRepository(focus)),
            isAppOnActiveSchedule = IsAppOnActiveScheduleUseCase(
                apps,
                FakeUsageRepository(),
                Clock { noon },
            ) { ZoneOffset.UTC },
        )
    }

    private val eveningWindow = Schedule(
        appPackageNames = listOf(pkg),
        scheduleType = ScheduleType.TIME_WINDOW,
        startTime = "18:00",
        endTime = "23:00",
    )

    @Test
    fun `disabled app is never blocked, even during a focus session`() = runTest {
        assertNull(useCase(enabled = false, focus = FocusSession(startedAt = 0L))(pkg))
    }

    @Test
    fun `app that is not on the list is not blocked`() = runTest {
        assertNull(useCase()("com.not.listed"))
    }

    @Test
    fun `focus session blocks even outside the app's schedule`() = runTest {
        val blocked = useCase(focus = FocusSession(startedAt = 0L), schedules = listOf(eveningWindow))
        assertEquals(BlockReason.FocusSession, blocked(pkg))
    }

    @Test
    fun `without focus the schedule decides`() = runTest {
        assertNull(useCase(schedules = listOf(eveningWindow))(pkg))
        assertEquals(BlockReason.AlwaysBlocked, useCase()(pkg))
    }
}
