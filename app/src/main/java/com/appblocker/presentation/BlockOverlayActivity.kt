package com.appblocker.presentation

import android.content.Intent
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.OnBackPressedCallback
import androidx.activity.compose.setContent
import androidx.activity.viewModels
import androidx.compose.runtime.getValue
import androidx.compose.runtime.LaunchedEffect
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.flowWithLifecycle
import androidx.lifecycle.Lifecycle
import com.appblocker.data.local.AppDatabase
import com.appblocker.data.repository.MotivationalMessageRepositoryImpl
import com.appblocker.data.repository.UsageRepositoryImpl
import com.appblocker.domain.usecase.GetMotivationalMessageUseCase
import com.appblocker.domain.usecase.RecordUsageUseCase
import com.appblocker.presentation.screen.BlockOverlayEvent
import com.appblocker.presentation.screen.BlockOverlayScreen
import com.appblocker.presentation.screen.BlockOverlayUiState
import com.appblocker.presentation.theme.AppBlockerTheme
import com.appblocker.service.AppBlockerAccessibilityService
import kotlinx.coroutines.flow.collectLatest

class BlockOverlayActivity : ComponentActivity() {

    companion object {
        const val EXTRA_PACKAGE_NAME = "extra_package_name"
        const val EXTRA_APP_NAME = "extra_app_name"
    }

    private lateinit var blockedPackage: String

    private val viewModel: BlockOverlayViewModel by viewModels {
        val database = AppDatabase.getInstance(this)
        val recordUsage = RecordUsageUseCase(
            UsageRepositoryImpl(database.usageSessionDao(), database.unblockEventDao())
        )
        val getMotivationalMessage = GetMotivationalMessageUseCase(
            MotivationalMessageRepositoryImpl(database.motivationalMessageDao())
        )
        BlockOverlayViewModel.Factory(
            packageName = blockedPackage,
            appName = intent.getStringExtra(EXTRA_APP_NAME) ?: blockedPackage,
            recordUsage = recordUsage,
            getMotivationalMessage = getMotivationalMessage
        )
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        blockedPackage = intent.getStringExtra(EXTRA_PACKAGE_NAME) ?: run {
            finish()
            return
        }

        onBackPressedDispatcher.addCallback(this, object : OnBackPressedCallback(true) {
            override fun handleOnBackPressed() {
                when (viewModel.state.value) {
                    is BlockOverlayUiState.BreathingPause -> goHome()
                    is BlockOverlayUiState.Confirmation -> goHome()
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
                    onSkipBreath = viewModel::onSkipBreath,
                    onLegitimate = viewModel::onLegitimate,
                    onBreakingPlan = viewModel::onBreakingPlan,
                    onGoBack = viewModel::onGoBack,
                    onProceed = viewModel::onProceed,
                )
            }
        }
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
