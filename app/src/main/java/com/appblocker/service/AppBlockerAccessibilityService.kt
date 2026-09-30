package com.appblocker.service

import android.accessibilityservice.AccessibilityService
import android.app.KeyguardManager
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.os.PowerManager
import android.os.SystemClock
import android.util.Log
import android.view.accessibility.AccessibilityEvent
import android.view.inputmethod.InputMethodManager
import androidx.core.content.ContextCompat
import com.appblocker.appContainer
import com.appblocker.domain.model.BlockReason
import com.appblocker.presentation.BlockOverlayActivity
import kotlinx.coroutines.CoroutineExceptionHandler
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.launch
import java.util.concurrent.ConcurrentHashMap

/**
 * Android shell around [ForegroundTracker]: it turns accessibility events and
 * screen broadcasts into tracker calls and shows the block overlay.
 *
 * `onAccessibilityEvent` is the hot path. It filters with a few set lookups
 * and posts to [serviceScope] only when the tracker has something to do.
 */
class AppBlockerAccessibilityService : AccessibilityService() {

    // Single-threaded: ForegroundTracker's contract is that all of its methods
    // run on this scope, which serializes every state transition.
    @OptIn(ExperimentalCoroutinesApi::class)
    private val serviceScope = CoroutineScope(
        SupervisorJob() +
            Dispatchers.IO.limitedParallelism(1) +
            CoroutineExceptionHandler { _, throwable -> Log.w(TAG, "Service coroutine failed", throwable) }
    )

    private lateinit var tracker: ForegroundTracker

    // Written off the event thread, read on it. Set replacement is atomic.
    @Volatile
    private var blockedPackages: Set<String> = emptySet()

    @Volatile
    private var inputMethodPackages: Set<String> = emptySet()

    // Window events while the screen is off (always-on display, lock screen,
    // call screens) are not the user switching apps.
    @Volatile
    private var screenInteractive: Boolean = true

    private var receiverRegistered = false

    private val systemEventReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context, intent: Intent) {
            when (intent.action) {
                Intent.ACTION_SCREEN_OFF -> {
                    screenInteractive = false
                    serviceScope.launch { tracker.onScreenOff() }
                }
                Intent.ACTION_SCREEN_ON -> {
                    screenInteractive = true
                    // Without a lock screen there is no USER_PRESENT.
                    val keyguard = getSystemService(KeyguardManager::class.java)
                    if (keyguard?.isKeyguardLocked == false) resumeAfterUnlock()
                }
                Intent.ACTION_USER_PRESENT -> resumeAfterUnlock()
                Intent.ACTION_INPUT_METHOD_CHANGED -> refreshInputMethodPackages()
            }
        }
    }

    override fun onCreate() {
        super.onCreate()
        val container = applicationContext.appContainer
        val isAppBlocked = container.isAppBlockedUseCase
        tracker = ForegroundTracker(
            scope = serviceScope,
            isAppBlocked = { packageName -> isAppBlocked(packageName) },
            recordUsage = container.recordUsageUseCase,
            isInGrace = ::isInGrace,
            showOverlay = ::showBlockOverlay,
            elapsedRealtimeMs = SystemClock::elapsedRealtime,
            // Log the throwable only: no package names (see CLAUDE.md privacy rules).
            logFailure = { message, error -> Log.w(TAG, message, error) },
        )

        serviceScope.launch {
            container.observeEnabledBlockedPackagesUseCase().collectLatest { snapshot ->
                blockedPackages = snapshot
            }
        }
    }

    override fun onServiceConnected() {
        super.onServiceConnected()
        instance = this
        screenInteractive = getSystemService(PowerManager::class.java)?.isInteractive ?: true
        refreshInputMethodPackages()
        if (!receiverRegistered) {
            val filter = IntentFilter().apply {
                addAction(Intent.ACTION_SCREEN_OFF)
                addAction(Intent.ACTION_SCREEN_ON)
                addAction(Intent.ACTION_USER_PRESENT)
                addAction(Intent.ACTION_INPUT_METHOD_CHANGED)
            }
            // EXPORTED on purpose: USER_PRESENT is sent by System UI, not by
            // system_server, and a non-exported receiver rejects it. All four
            // actions are protected broadcasts that other apps cannot send.
            ContextCompat.registerReceiver(
                this,
                systemEventReceiver,
                filter,
                ContextCompat.RECEIVER_EXPORTED,
            )
            receiverRegistered = true
        }
    }

    override fun onAccessibilityEvent(event: AccessibilityEvent?) {
        if (event?.eventType != AccessibilityEvent.TYPE_WINDOW_STATE_CHANGED) return
        if (!screenInteractive) return

        // Only a full-screen window can replace the app in front. Dialogs,
        // sheets, chat heads and floating activities (of any app, blocked or
        // not) sit on top of it, and the app re-announces itself when they close.
        if (!event.isFullScreen) return

        val packageName = event.packageName?.toString() ?: return

        // Keyboards, the notification shade and system dialogs float over the
        // current app without replacing it.
        if (packageName in TRANSIENT_PACKAGES || packageName in inputMethodPackages) return

        val isTracked = packageName in blockedPackages

        // Fast path: an untracked app came to the front and nothing is tracked.
        if (!isTracked && !tracker.isBusy) return

        serviceScope.launch { tracker.onAppInFront(packageName, isTracked) }
    }

    private fun resumeAfterUnlock() {
        serviceScope.launch { tracker.onUnlocked { it in blockedPackages } }
    }

    private fun showBlockOverlay(packageName: String, reason: BlockReason) {
        val appName = try {
            val appInfo = packageManager.getApplicationInfo(packageName, 0)
            packageManager.getApplicationLabel(appInfo).toString()
        } catch (_: Exception) {
            packageName
        }
        startActivity(BlockOverlayActivity.newIntent(this, packageName, appName, reason))
    }

    private fun refreshInputMethodPackages() {
        val imm = getSystemService(InputMethodManager::class.java) ?: return
        inputMethodPackages = try {
            imm.enabledInputMethodList.mapTo(HashSet()) { it.packageName }
        } catch (e: RuntimeException) {
            Log.w(TAG, "Could not read enabled input methods", e)
            emptySet()
        }
    }

    override fun onInterrupt() {
        // Only meaningful for services that give spoken or haptic feedback.
    }

    override fun onDestroy() {
        if (instance === this) instance = null
        if (receiverRegistered) {
            unregisterReceiver(systemEventReceiver)
            receiverRegistered = false
        }
        // Cancelling the scope cancels the tracking job, whose finally block
        // closes the open usage session.
        serviceScope.cancel()
        super.onDestroy()
    }

    companion object {
        private const val TAG = "AppBlockerService"

        /** How long "planned" and "open anyway" let the user stay before checking in again. */
        const val GRACE_PERIOD_MS: Long = 5 * 60_000L

        /**
         * Packages whose windows sit on top of the current app without
         * replacing it: the status bar and notification shade, system dialogs
         * and the share sheet, and runtime-permission prompts.
         */
        private val TRANSIENT_PACKAGES = setOf(
            "com.android.systemui",
            "android",
            "com.android.intentresolver",
            "com.android.permissioncontroller",
            "com.google.android.permissioncontroller",
        )

        @Volatile
        private var instance: AppBlockerAccessibilityService? = null

        private val graceUntilMs = ConcurrentHashMap<String, Long>()

        fun grantGrace(packageName: String) {
            graceUntilMs[packageName] = System.currentTimeMillis() + GRACE_PERIOD_MS
        }

        private fun isInGrace(packageName: String): Boolean {
            val expiry = graceUntilMs[packageName] ?: return false
            if (System.currentTimeMillis() < expiry) return true
            graceUntilMs.remove(packageName, expiry)
            return false
        }

        /**
         * Called by the overlay from onResume/onPause. While the overlay is in
         * front the service stops tracking the app underneath and never
         * relaunches (and so never resets) the overlay.
         */
        fun onOverlayInFront(inFront: Boolean) {
            val service = instance ?: return
            service.serviceScope.launch { service.tracker.onOverlayInFront(inFront) }
        }
    }
}
