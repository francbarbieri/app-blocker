package com.appblocker.service

import android.accessibilityservice.AccessibilityService
import android.content.Intent
import android.view.accessibility.AccessibilityEvent
import com.appblocker.appContainer
import com.appblocker.domain.usecase.IsAppBlockedUseCase
import com.appblocker.domain.usecase.ObserveEnabledBlockedPackagesUseCase
import com.appblocker.domain.usecase.RecordUsageUseCase
import com.appblocker.presentation.BlockOverlayActivity
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.launch
import java.util.concurrent.ConcurrentHashMap

class AppBlockerAccessibilityService : AccessibilityService() {

    // Single-thread confinement: every mutation of currentTrackedPackage /
    // currentSessionId happens on this one IO thread. The event thread never
    // touches those fields.
    @OptIn(ExperimentalCoroutinesApi::class)
    private val serviceScope = CoroutineScope(
        SupervisorJob() + Dispatchers.IO.limitedParallelism(1)
    )

    private lateinit var isAppBlocked: IsAppBlockedUseCase
    private lateinit var recordUsage: RecordUsageUseCase
    private lateinit var observeEnabledBlockedPackages: ObserveEnabledBlockedPackagesUseCase

    // Mutated only by the Flow collector below (on serviceScope's thread);
    // read on the event thread. @Volatile gives the cross-thread visibility
    // guarantee — Set replacement is atomic, no torn writes.
    @Volatile
    private var blockedPackages: Set<String> = emptySet()
    private var blockedPackagesJob: Job? = null

    // Confined to serviceScope.
    private var currentTrackedPackage: String? = null
    private var currentSessionId: Long? = null

    // Confined to the event thread.
    private var lastOverlayPackage: String? = null
    private var lastOverlayAtMs: Long = 0L

    private val ignoredPackages = setOf(
        "com.appblocker",
        "com.android.systemui",
        "com.android.launcher",
        "com.android.launcher3",
        "com.google.android.apps.nexuslauncher"
    )

    override fun onCreate() {
        super.onCreate()
        val container = applicationContext.appContainer
        isAppBlocked = container.isAppBlockedUseCase
        recordUsage = container.recordUsageUseCase
        observeEnabledBlockedPackages = container.observeEnabledBlockedPackagesUseCase

        blockedPackagesJob = serviceScope.launch {
            observeEnabledBlockedPackages().collectLatest { snapshot ->
                blockedPackages = snapshot
            }
        }
    }

    override fun onAccessibilityEvent(event: AccessibilityEvent?) {
        if (event?.eventType != AccessibilityEvent.TYPE_WINDOW_STATE_CHANGED) return

        val packageName = event.packageName?.toString() ?: return

        if (packageName in ignoredPackages) return

        if (isInGrace(packageName)) return

        // Fast path: O(1) set lookup, no DB, no coroutine.
        // The vast majority of window-state events involve apps the user has
        // not chosen to block — those should cost nothing here.
        if (packageName !in blockedPackages) {
            if (packageName != currentTrackedPackage && currentTrackedPackage != null) {
                serviceScope.launch { endCurrentSessionInternal() }
            }
            return
        }

        // Debounce repeated launches for the same blocked package: prevents
        // the overlay from being recreated if the user dismisses it back to
        // the blocked app and a fresh TYPE_WINDOW_STATE_CHANGED fires.
        val now = System.currentTimeMillis()
        if (packageName == lastOverlayPackage && now - lastOverlayAtMs < OVERLAY_DEBOUNCE_MS) {
            return
        }
        lastOverlayPackage = packageName
        lastOverlayAtMs = now

        serviceScope.launch {
            // Slow path: schedule windows, daily-limit math, focus-session check.
            // Reached only for packages already in the user's blocked set.
            if (!isAppBlocked(packageName)) return@launch
            if (currentTrackedPackage != packageName) {
                endCurrentSessionInternal()
                currentTrackedPackage = packageName
                currentSessionId = recordUsage.startSession(packageName)
            }
            launchBlockOverlay(packageName)
        }
    }

    /** Must run on serviceScope (single-threaded). */
    private suspend fun endCurrentSessionInternal() {
        val sessionId = currentSessionId ?: return
        recordUsage.endSession(sessionId)
        currentTrackedPackage = null
        currentSessionId = null
    }

    private fun launchBlockOverlay(packageName: String) {
        val appName = try {
            val appInfo = packageManager.getApplicationInfo(packageName, 0)
            packageManager.getApplicationLabel(appInfo).toString()
        } catch (_: Exception) {
            packageName
        }

        val intent = Intent(this, BlockOverlayActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP
            putExtra(BlockOverlayActivity.EXTRA_PACKAGE_NAME, packageName)
            putExtra(BlockOverlayActivity.EXTRA_APP_NAME, appName)
        }
        startActivity(intent)
    }

    private fun isInGrace(packageName: String): Boolean {
        val expiry = graceUntilMs[packageName] ?: return false
        if (System.currentTimeMillis() < expiry) return true
        graceUntilMs.remove(packageName)
        return false
    }

    override fun onInterrupt() {
        serviceScope.launch { endCurrentSessionInternal() }
    }

    override fun onDestroy() {
        serviceScope.launch { endCurrentSessionInternal() }
        serviceScope.cancel()
        super.onDestroy()
    }

    companion object {
        const val GRACE_PERIOD_MS: Long = 5 * 60_000L
        private const val OVERLAY_DEBOUNCE_MS: Long = 1_500L

        private val graceUntilMs = ConcurrentHashMap<String, Long>()

        fun grantGrace(packageName: String) {
            graceUntilMs[packageName] = System.currentTimeMillis() + GRACE_PERIOD_MS
        }
    }
}
