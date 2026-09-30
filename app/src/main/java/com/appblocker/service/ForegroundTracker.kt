package com.appblocker.service

import com.appblocker.domain.model.BlockReason
import com.appblocker.domain.usecase.RecordUsageUseCase
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/**
 * The blocking state machine behind [AppBlockerAccessibilityService], kept free
 * of Android types so it runs in JVM tests with virtual time.
 *
 * Threading contract: every public method runs on [scope], which must be
 * single-threaded. No public method suspends, so each state transition is
 * atomic: two events can never interleave halfway through a transition.
 * Suspending work (database access) happens only inside the per-app tracking
 * job, which owns its usage session and closes it when cancelled.
 *
 * While a tracked app is in front, its job re-checks [isAppBlocked] every
 * [checkIntervalMs] (the first check is immediate), so a block lands even when
 * nothing changes on screen: a schedule starting, a daily limit being reached
 * or a 5-minute pass running out.
 */
internal class ForegroundTracker(
    private val scope: CoroutineScope,
    private val isAppBlocked: suspend (packageName: String) -> BlockReason?,
    private val recordUsage: RecordUsageUseCase,
    private val isInGrace: (packageName: String) -> Boolean,
    private val showOverlay: (packageName: String, reason: BlockReason) -> Unit,
    private val elapsedRealtimeMs: () -> Long,
    private val logFailure: (message: String, error: Throwable) -> Unit,
    private val checkIntervalMs: Long = CHECK_INTERVAL_MS,
    private val resumeSettleMs: Long = RESUME_SETTLE_MS,
    private val overlayDebounceMs: Long = OVERLAY_DEBOUNCE_MS,
) {
    private class Tracked(val packageName: String, val job: Job)

    private var tracked: Tracked? = null
    private var pausedPackage: String? = null
    private var overlayInFront = false
    private var lastOverlayPackage: String? = null
    private var lastOverlayAtMs = 0L

    /**
     * False when nothing is tracked or waiting to resume. The event thread
     * reads it to skip untracked apps without posting any work.
     */
    @Volatile
    var isBusy: Boolean = false
        private set

    /** Package currently tracked, for tests and diagnostics. */
    val trackedPackage: String? get() = tracked?.packageName

    /**
     * A full-screen window of [packageName] came to the front. [isTracked]
     * says whether the app is on the (enabled) block list.
     */
    fun onAppInFront(packageName: String, isTracked: Boolean) {
        if (overlayInFront && isTracked) {
            // A late event from the app underneath the overlay. The overlay
            // is what the user sees; tracking resumes when it closes.
            return
        }
        pausedPackage = null
        if (isTracked) startTracking(packageName) else stopTracking()
        publishBusy()
    }

    /**
     * The screen turned off: stop counting usage, remember the app for later.
     * A second screen-off before an unlock (a glance at the lock screen) keeps
     * the app paused by the first one.
     */
    fun onScreenOff() {
        val current = tracked?.packageName
        stopTracking()
        pausedPackage = current ?: pausedPackage
        publishBusy()
    }

    /**
     * The user unlocked. Resume the app that was in front when the screen went
     * off, unless another app's window event has arrived since. The first
     * check waits [resumeSettleMs], so an app opened straight from a lock-screen
     * notification can take over before any overlay is shown.
     */
    fun onUnlocked(isStillTracked: (String) -> Boolean) {
        val packageName = pausedPackage ?: return
        pausedPackage = null
        if (isStillTracked(packageName)) {
            startTracking(packageName, firstCheckDelayMs = resumeSettleMs)
        }
        publishBusy()
    }

    /** The block overlay became (or stopped being) the foreground activity. */
    fun onOverlayInFront(inFront: Boolean) {
        overlayInFront = inFront
        if (inFront) {
            // The user is looking at the overlay, not the app: close its session.
            stopTracking()
            publishBusy()
        }
    }

    fun shutdown() {
        stopTracking()
        pausedPackage = null
        publishBusy()
    }

    private fun startTracking(packageName: String, firstCheckDelayMs: Long = 0L) {
        if (tracked?.packageName == packageName) return
        stopTracking()
        // Assigned before the job runs: launch only schedules it on the
        // single-threaded scope, and this method does not suspend.
        val job = scope.launch { trackWhileInFront(packageName, firstCheckDelayMs) }
        tracked = Tracked(packageName, job)
    }

    private fun stopTracking() {
        val current = tracked ?: return
        tracked = null
        current.job.cancel()
    }

    private fun publishBusy() {
        isBusy = tracked != null || pausedPackage != null
    }

    private suspend fun isCurrentJob(): Boolean =
        tracked?.job === currentCoroutineContext()[Job]

    private suspend fun trackWhileInFront(packageName: String, firstCheckDelayMs: Long) {
        var sessionId: Long? = null
        try {
            sessionId = attempt("Failed to start usage session") { recordUsage.startSession(packageName) }
            if (firstCheckDelayMs > 0) delay(firstCheckDelayMs)
            while (isCurrentJob()) {
                if (!overlayInFront && !isInGrace(packageName)) {
                    val reason = attempt("Block check failed") { isAppBlocked(packageName) }
                    if (reason != null && isCurrentJob()) launchOverlay(packageName, reason)
                }
                delay(checkIntervalMs)
                sessionId?.let { id ->
                    attempt("Failed to extend usage session") { recordUsage.extendSession(id) }
                }
            }
        } finally {
            sessionId?.let { id ->
                withContext(NonCancellable) {
                    attempt("Failed to end usage session") { recordUsage.endSession(id) }
                }
            }
        }
    }

    private fun launchOverlay(packageName: String, reason: BlockReason) {
        val now = elapsedRealtimeMs()
        if (packageName == lastOverlayPackage && now - lastOverlayAtMs < overlayDebounceMs) return
        lastOverlayPackage = packageName
        lastOverlayAtMs = now
        showOverlay(packageName, reason)
    }

    /** Runs [block]; failures are reported and swallowed, cancellation is not. */
    private suspend fun <T> attempt(message: String, block: suspend () -> T): T? = try {
        block()
    } catch (e: CancellationException) {
        throw e
    } catch (e: Exception) {
        logFailure(message, e)
        null
    }

    companion object {
        /** How often a tracked foreground app is re-checked and its usage session extended. */
        const val CHECK_INTERVAL_MS: Long = 15_000L

        /** Delay before the first check after unlock, so a competing app switch can win. */
        const val RESUME_SETTLE_MS: Long = 2_000L

        /** Minimum gap between two overlay launches for the same app. */
        const val OVERLAY_DEBOUNCE_MS: Long = 1_500L
    }
}
