package com.appblocker.presentation

import android.content.Intent
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.OnBackPressedCallback
import androidx.activity.compose.setContent
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import com.appblocker.data.local.AppDatabase
import com.appblocker.data.repository.MotivationalMessageRepositoryImpl
import com.appblocker.data.repository.UsageRepositoryImpl
import com.appblocker.domain.usecase.GetMotivationalMessageUseCase
import com.appblocker.domain.usecase.RecordUsageUseCase
import com.appblocker.presentation.screen.BlockOverlayScreen
import com.appblocker.presentation.theme.AppBlockerTheme
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch

class BlockOverlayActivity : ComponentActivity() {

    companion object {
        const val EXTRA_PACKAGE_NAME = "extra_package_name"
        const val EXTRA_APP_NAME = "extra_app_name"
    }

    private lateinit var recordUsage: RecordUsageUseCase
    private lateinit var getMotivationalMessage: GetMotivationalMessageUseCase

    private var motivationalMessage by mutableStateOf("")

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        val blockedPackage = intent.getStringExtra(EXTRA_PACKAGE_NAME) ?: run {
            finish()
            return
        }
        val appName = intent.getStringExtra(EXTRA_APP_NAME) ?: blockedPackage

        val database = AppDatabase.getInstance(this)
        recordUsage = RecordUsageUseCase(
            UsageRepositoryImpl(database.usageSessionDao(), database.unblockEventDao())
        )
        getMotivationalMessage = GetMotivationalMessageUseCase(
            MotivationalMessageRepositoryImpl(database.motivationalMessageDao())
        )

        onBackPressedDispatcher.addCallback(this, object : OnBackPressedCallback(true) {
            override fun handleOnBackPressed() {
                goHome()
            }
        })

        loadMotivationalMessage(blockedPackage)

        setContent {
            AppBlockerTheme {
                BlockOverlayScreen(
                    appName = appName,
                    motivationalMessage = motivationalMessage,
                    onGoBack = {
                        CoroutineScope(Dispatchers.IO).launch {
                            recordUsage.recordUnblock(blockedPackage, userProceeded = false)
                        }
                        goHome()
                    },
                    onProceedAnyway = {
                        CoroutineScope(Dispatchers.IO).launch {
                            recordUsage.recordUnblock(blockedPackage, userProceeded = true)
                        }
                        finish()
                    },
                )
            }
        }
    }

    private fun loadMotivationalMessage(packageName: String) {
        CoroutineScope(Dispatchers.IO).launch {
            val message = getMotivationalMessage(packageName)
            motivationalMessage = message?.message ?: "You've got this! Stay focused."
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
