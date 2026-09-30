package com.appblocker.presentation

import android.content.Intent
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import com.appblocker.presentation.screen.OnboardingScreen
import com.appblocker.presentation.theme.AppBlockerTheme
import com.appblocker.presentation.theme.enableDarkEdgeToEdge

class OnboardingActivity : ComponentActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableDarkEdgeToEdge()
        setContent {
            AppBlockerTheme {
                OnboardingScreen(
                    onFinish = {
                        getSharedPreferences("app_blocker_prefs", MODE_PRIVATE)
                            .edit()
                            .putBoolean("onboarding_completed", true)
                            .apply()
                        startActivity(Intent(this, MainActivity::class.java))
                        finish()
                    },
                )
            }
        }
    }
}
