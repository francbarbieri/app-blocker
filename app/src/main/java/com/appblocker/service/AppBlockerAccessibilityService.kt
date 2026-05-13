package com.appblocker.service

import android.accessibilityservice.AccessibilityService
import android.content.Intent
import android.view.accessibility.AccessibilityEvent
import com.appblocker.data.local.AppDatabase
import com.appblocker.data.repository.BlockedAppRepositoryImpl
import com.appblocker.data.repository.UsageRepositoryImpl
import com.appblocker.domain.usecase.IsAppBlockedUseCase
import com.appblocker.domain.usecase.RecordUsageUseCase
import com.appblocker.presentation.BlockOverlayActivity
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.launch
import java.util.concurrent.ConcurrentHashMap

class AppBlockerAccessibilityService : AccessibilityService() {

    private val serviceScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private lateinit var isAppBlocked: IsAppBlockedUseCase
    private lateinit var recordUsage: RecordUsageUseCase

    private var currentTrackedPackage: String? = null
    private var currentSessionId: Long? = null

    private val ignoredPackages = setOf(
        "com.appblocker",
        "com.android.systemui",
        "com.android.launcher",
        "com.android.launcher3",
        "com.google.android.apps.nexuslauncher"
    )

    override fun onCreate() {
        super.onCreate()
        val database = AppDatabase.getInstance(this)
        val blockedAppRepo = BlockedAppRepositoryImpl(
            database.blockedAppDao(),
            database.scheduleDao()
        )
        val usageRepo = UsageRepositoryImpl(
            database.usageSessionDao(),
            database.unblockEventDao()
        )
        isAppBlocked = IsAppBlockedUseCase(blockedAppRepo, usageRepo)
        recordUsage = RecordUsageUseCase(usageRepo)
    }

    override fun onAccessibilityEvent(event: AccessibilityEvent?) {
        if (event?.eventType != AccessibilityEvent.TYPE_WINDOW_STATE_CHANGED) return

        val packageName = event.packageName?.toString() ?: return

        if (packageName in ignoredPackages) return

        if (isInGrace(packageName)) return

        if (packageName != currentTrackedPackage) {
            endCurrentSession()
        }

        serviceScope.launch {
            val blocked = isAppBlocked(packageName)
            if (blocked) {
                if (currentTrackedPackage != packageName) {
                    currentTrackedPackage = packageName
                    currentSessionId = recordUsage.startSession(packageName)
                }
                launchBlockOverlay(packageName)
            }
        }
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

    private fun endCurrentSession() {
        val sessionId = currentSessionId ?: return
        serviceScope.launch {
            recordUsage.endSession(sessionId)
        }
        currentTrackedPackage = null
        currentSessionId = null
    }

    override fun onInterrupt() {
        endCurrentSession()
    }

    override fun onDestroy() {
        endCurrentSession()
        serviceScope.cancel()
        super.onDestroy()
    }

    companion object {
        const val GRACE_PERIOD_MS: Long = 5 * 60_000L

        private val graceUntilMs = ConcurrentHashMap<String, Long>()

        fun grantGrace(packageName: String) {
            graceUntilMs[packageName] = System.currentTimeMillis() + GRACE_PERIOD_MS
        }
    }
}
