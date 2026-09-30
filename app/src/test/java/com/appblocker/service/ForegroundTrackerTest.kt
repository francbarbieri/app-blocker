package com.appblocker.service

import com.appblocker.domain.model.BlockReason
import com.appblocker.domain.usecase.RecordUsageUseCase
import com.appblocker.domain.util.Clock
import com.appblocker.presentation.fakes.FakeUsageRepository
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.advanceTimeBy
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class ForegroundTrackerTest {

    private val app = "com.example.social"
    private val other = "com.example.chat"
    private val interval = ForegroundTracker.CHECK_INTERVAL_MS

    private class Harness(scope: TestScope) {
        val blocked = mutableMapOf<String, BlockReason?>()
        var failNextCheck = false
        val graced = mutableSetOf<String>()
        val overlays = mutableListOf<Pair<String, BlockReason>>()
        val failures = mutableListOf<String>()
        val usage = FakeUsageRepository()
        val tracker = ForegroundTracker(
            scope = scope.backgroundScope,
            isAppBlocked = { packageName ->
                if (failNextCheck) {
                    failNextCheck = false
                    error("database unavailable")
                }
                blocked[packageName]
            },
            recordUsage = RecordUsageUseCase(usage, Clock { scope.testScheduler.currentTime }),
            isInGrace = { it in graced },
            showOverlay = { packageName, reason -> overlays += packageName to reason },
            elapsedRealtimeMs = { scope.testScheduler.currentTime },
            logFailure = { message, _ -> failures += message },
        )

        fun sessionsOf(packageName: String) =
            usage.sessions.value.filter { it.appPackageName == packageName }
    }

    @Test
    fun `opening a blocked app shows the overlay at once and opens a session`() = runTest {
        val h = Harness(this)
        h.blocked[app] = BlockReason.AlwaysBlocked

        h.tracker.onAppInFront(app, isTracked = true)
        runCurrent()

        assertEquals(listOf(app to BlockReason.AlwaysBlocked), h.overlays)
        assertEquals(1, h.sessionsOf(app).size)
        assertTrue(h.tracker.isBusy)
    }

    @Test
    fun `a burst of events during a slow write keeps one session and one loop`() = runTest {
        val h = Harness(this)
        h.usage.startSessionDelayMs = 50

        // Apps announce themselves several times when they open.
        repeat(3) {
            h.tracker.onAppInFront(app, isTracked = true)
            runCurrent()
            advanceTimeBy(10)
        }
        advanceTimeBy(100)
        assertEquals(1, h.sessionsOf(app).size)

        h.blocked[app] = BlockReason.AlwaysBlocked
        advanceTimeBy(interval)
        runCurrent()
        assertEquals("exactly one enforcement loop", 1, h.overlays.size)

        // Leaving must stop every loop: an orphaned one would keep firing.
        h.tracker.onAppInFront(other, isTracked = false)
        advanceTimeBy(interval * 4)
        runCurrent()
        assertEquals(1, h.overlays.size)
    }

    @Test
    fun `switching apps closes the session at that moment`() = runTest {
        val h = Harness(this)
        h.tracker.onAppInFront(app, isTracked = true)
        advanceTimeBy(20_000)

        h.tracker.onAppInFront(other, isTracked = false)
        runCurrent()

        val session = h.sessionsOf(app).single()
        assertEquals(0L, session.startTime)
        assertEquals(20_000L, session.endTime)
        assertNull(h.tracker.trackedPackage)
        assertFalse(h.tracker.isBusy)
    }

    @Test
    fun `the pass holds the overlay back, and the check-in returns when it ends`() = runTest {
        val h = Harness(this)
        h.blocked[app] = BlockReason.AlwaysBlocked
        h.graced += app

        h.tracker.onAppInFront(app, isTracked = true)
        advanceTimeBy(interval * 3)
        runCurrent()
        assertTrue(h.overlays.isEmpty())

        h.graced -= app
        advanceTimeBy(interval)
        runCurrent()
        assertEquals(1, h.overlays.size)
    }

    @Test
    fun `a block that starts while the app is open lands within one interval`() = runTest {
        val h = Harness(this)
        h.tracker.onAppInFront(app, isTracked = true)
        advanceTimeBy(10_000)
        assertTrue(h.overlays.isEmpty())

        val reason = BlockReason.ScheduledWindow(until = "17:00")
        h.blocked[app] = reason
        advanceTimeBy(interval)
        runCurrent()
        assertEquals(listOf(app to reason), h.overlays)
    }

    @Test
    fun `while the overlay is in front nothing relaunches it and late events are ignored`() = runTest {
        val h = Harness(this)
        h.blocked[app] = BlockReason.AlwaysBlocked
        h.tracker.onAppInFront(app, isTracked = true)
        runCurrent()
        advanceTimeBy(300)

        h.tracker.onOverlayInFront(true)
        runCurrent()
        assertNull("usage stops while the overlay covers the app", h.tracker.trackedPackage)
        assertEquals(300L, h.sessionsOf(app).single().endTime)

        // A late window event from the app underneath the overlay.
        h.tracker.onAppInFront(app, isTracked = true)
        advanceTimeBy(interval * 4)
        runCurrent()
        assertEquals(1, h.overlays.size)
        assertNull(h.tracker.trackedPackage)

        // "Open anyway": the overlay closes with a pass and the app returns.
        h.graced += app
        h.tracker.onOverlayInFront(false)
        h.tracker.onAppInFront(app, isTracked = true)
        runCurrent()
        assertEquals(app, h.tracker.trackedPackage)
        assertEquals(1, h.overlays.size)
    }

    @Test
    fun `screen off closes the session and unlock resumes the app`() = runTest {
        val h = Harness(this)
        h.tracker.onAppInFront(app, isTracked = true)
        advanceTimeBy(10_000)

        h.tracker.onScreenOff()
        runCurrent()
        assertEquals(10_000L, h.sessionsOf(app).single().endTime)
        assertNull(h.tracker.trackedPackage)
        assertTrue("the paused app keeps the tracker busy", h.tracker.isBusy)

        advanceTimeBy(60_000)
        h.tracker.onUnlocked { true }
        runCurrent()
        assertEquals(app, h.tracker.trackedPackage)
        assertEquals(2, h.sessionsOf(app).size)
        assertEquals(70_000L, h.sessionsOf(app).last().startTime)
    }

    @Test
    fun `a glance at the lock screen does not forget the paused app`() = runTest {
        val h = Harness(this)
        h.tracker.onAppInFront(app, isTracked = true)
        runCurrent()

        h.tracker.onScreenOff()
        // Screen on (lock screen only), then off again: no unlock in between.
        h.tracker.onScreenOff()
        h.tracker.onUnlocked { true }
        runCurrent()

        assertEquals(app, h.tracker.trackedPackage)
    }

    @Test
    fun `after unlock the first check waits for the settle delay`() = runTest {
        val h = Harness(this)
        h.tracker.onAppInFront(app, isTracked = true)
        runCurrent()
        h.tracker.onScreenOff()
        h.blocked[app] = BlockReason.FocusSession

        h.tracker.onUnlocked { true }
        advanceTimeBy(ForegroundTracker.RESUME_SETTLE_MS - 100)
        assertTrue(h.overlays.isEmpty())

        advanceTimeBy(200)
        runCurrent()
        assertEquals(listOf(app to BlockReason.FocusSession), h.overlays)
    }

    @Test
    fun `an app opened from the lock screen wins over the paused one`() = runTest {
        val h = Harness(this)
        h.tracker.onAppInFront(app, isTracked = true)
        runCurrent()
        h.tracker.onScreenOff()
        h.blocked[app] = BlockReason.FocusSession

        h.tracker.onUnlocked { true }
        advanceTimeBy(500)
        h.tracker.onAppInFront(other, isTracked = false)
        advanceTimeBy(interval * 2)
        runCurrent()

        assertTrue(h.overlays.isEmpty())
        assertNull(h.tracker.trackedPackage)
    }

    @Test
    fun `unlock does not resume an app that left the block list`() = runTest {
        val h = Harness(this)
        h.tracker.onAppInFront(app, isTracked = true)
        runCurrent()
        h.tracker.onScreenOff()

        h.tracker.onUnlocked { false }
        runCurrent()
        assertNull(h.tracker.trackedPackage)
        assertFalse(h.tracker.isBusy)
    }

    @Test
    fun `a failing check is logged and retried on the next tick`() = runTest {
        val h = Harness(this)
        h.blocked[app] = BlockReason.AlwaysBlocked
        h.failNextCheck = true

        h.tracker.onAppInFront(app, isTracked = true)
        runCurrent()
        assertTrue(h.overlays.isEmpty())
        assertEquals(listOf("Block check failed"), h.failures)

        advanceTimeBy(interval)
        runCurrent()
        assertEquals(1, h.overlays.size)
    }

    @Test
    fun `reopening within the debounce window does not relaunch the overlay`() = runTest {
        val h = Harness(this)
        h.blocked[app] = BlockReason.AlwaysBlocked
        h.tracker.onAppInFront(app, isTracked = true)
        runCurrent()

        h.tracker.onAppInFront(other, isTracked = false)
        advanceTimeBy(500)
        h.tracker.onAppInFront(app, isTracked = true)
        runCurrent()
        assertEquals(1, h.overlays.size)

        advanceTimeBy(interval)
        runCurrent()
        assertEquals(2, h.overlays.size)
    }

    @Test
    fun `usage is extended by the heartbeat while the app stays in front`() = runTest {
        val h = Harness(this)
        h.tracker.onAppInFront(app, isTracked = true)
        advanceTimeBy(interval * 2 + 1)
        runCurrent()
        assertEquals(interval * 2, h.sessionsOf(app).single().endTime)
    }
}
