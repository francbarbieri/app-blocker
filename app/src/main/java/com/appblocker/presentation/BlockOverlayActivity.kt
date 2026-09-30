package com.appblocker.presentation

import android.content.Context
import android.content.Intent
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.OnBackPressedCallback
import androidx.activity.compose.setContent
import androidx.activity.viewModels
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.flowWithLifecycle
import com.appblocker.appContainer
import com.appblocker.domain.model.BlockReason
import com.appblocker.presentation.screen.BlockOverlayEvent
import com.appblocker.presentation.screen.BlockOverlayScreen
import com.appblocker.presentation.screen.BlockOverlayUiState
import com.appblocker.presentation.theme.AppBlockerTheme
import com.appblocker.presentation.theme.enableDarkEdgeToEdge
import com.appblocker.service.AppBlockerAccessibilityService
import kotlinx.coroutines.flow.collectLatest

class BlockOverlayActivity : ComponentActivity() {

    companion object {
        const val EXTRA_PACKAGE_NAME = "extra_package_name"
        const val EXTRA_APP_NAME = "extra_app_name"
        private const val EXTRA_REASON = "extra_block_reason"
        private const val EXTRA_REASON_UNTIL = "extra_block_reason_until"
        private const val EXTRA_REASON_LIMIT_MINUTES = "extra_block_reason_limit_minutes"

        private const val REASON_FOCUS = "focus"
        private const val REASON_ALWAYS = "always"
        private const val REASON_WINDOW = "window"
        private const val REASON_LIMIT = "limit"

        /**
         * The overlay runs in its own task (see the manifest's empty
         * taskAffinity) so finishing it returns the user to the app they
         * opened, not to App Blocker's main screen.
         */
        fun newIntent(
            context: Context,
            packageName: String,
            appName: String,
            reason: BlockReason,
        ): Intent = Intent(context, BlockOverlayActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP
            putExtra(EXTRA_PACKAGE_NAME, packageName)
            putExtra(EXTRA_APP_NAME, appName)
            when (reason) {
                BlockReason.FocusSession -> putExtra(EXTRA_REASON, REASON_FOCUS)
                BlockReason.AlwaysBlocked -> putExtra(EXTRA_REASON, REASON_ALWAYS)
                is BlockReason.ScheduledWindow -> {
                    putExtra(EXTRA_REASON, REASON_WINDOW)
                    putExtra(EXTRA_REASON_UNTIL, reason.until)
                }
                is BlockReason.DailyLimitReached -> {
                    putExtra(EXTRA_REASON, REASON_LIMIT)
                    putExtra(EXTRA_REASON_LIMIT_MINUTES, reason.limitMinutes)
                }
            }
        }

        private fun Intent.readReason(): BlockReason? = when (getStringExtra(EXTRA_REASON)) {
            REASON_FOCUS -> BlockReason.FocusSession
            REASON_ALWAYS -> BlockReason.AlwaysBlocked
            REASON_WINDOW -> BlockReason.ScheduledWindow(until = getStringExtra(EXTRA_REASON_UNTIL))
            REASON_LIMIT -> BlockReason.DailyLimitReached(
                limitMinutes = getIntExtra(EXTRA_REASON_LIMIT_MINUTES, 0),
            )
            else -> null
        }
    }

    private lateinit var blockedPackage: String

    private val viewModel: BlockOverlayViewModel by viewModels {
        val container = appContainer
        BlockOverlayViewModel.Factory(
            packageName = blockedPackage,
            appName = intent.getStringExtra(EXTRA_APP_NAME) ?: blockedPackage,
            recordUsage = container.recordUsageUseCase,
            getMotivationalMessage = container.getMotivationalMessageUseCase
        )
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        blockedPackage = intent.getStringExtra(EXTRA_PACKAGE_NAME) ?: run {
            finish()
            return
        }
        val reason = intent.readReason()
        enableDarkEdgeToEdge()

        onBackPressedDispatcher.addCallback(this, object : OnBackPressedCallback(true) {
            override fun handleOnBackPressed() {
                when (viewModel.state.value) {
                    is BlockOverlayUiState.BreathingPause,
                    is BlockOverlayUiState.Confirmation -> viewModel.onDismissToHome()
                    is BlockOverlayUiState.Motivational -> viewModel.onBackPressedFromMotivational()
                }
            }
        })

        setContent {
            val state by viewModel.state.collectAsStateWithLifecycle()

            LaunchedEffect(Unit) {
                viewModel.events
                    .flowWithLifecycle(lifecycle, Lifecycle.State.STARTED)
                    .collectLatest { event ->
                        when (event) {
                            BlockOverlayEvent.GrantGraceAndClose -> {
                                AppBlockerAccessibilityService.grantGrace(blockedPackage)
                                finish()
                            }
                            BlockOverlayEvent.GoHome -> goHome()
                        }
                    }
            }

            AppBlockerTheme {
                BlockOverlayScreen(
                    state = state,
                    reason = reason,
                    graceMinutes = (AppBlockerAccessibilityService.GRACE_PERIOD_MS / 60_000L).toInt(),
                    onSkipBreath = viewModel::onSkipBreath,
                    onLegitimate = viewModel::onLegitimate,
                    onBreakingPlan = viewModel::onBreakingPlan,
                    onGoBack = viewModel::onGoBack,
                    onProceed = viewModel::onProceed,
                )
            }
        }
    }

    override fun onResume() {
        super.onResume()
        AppBlockerAccessibilityService.onOverlayInFront(true)
    }

    override fun onPause() {
        AppBlockerAccessibilityService.onOverlayInFront(false)
        super.onPause()
    }

    private fun goHome() {
        val homeIntent = Intent(Intent.ACTION_MAIN).apply {
            addCategory(Intent.CATEGORY_HOME)
            flags = Intent.FLAG_ACTIVITY_NEW_TASK
        }
        startActivity(homeIntent)
        finish()
    }
}
