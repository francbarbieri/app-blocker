package com.appblocker.presentation

import android.content.Intent
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import com.appblocker.presentation.screen.AppNavigation
import com.appblocker.presentation.theme.AppBlockerTheme
import com.appblocker.presentation.theme.enableDarkEdgeToEdge

class MainActivity : ComponentActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        val prefs = getSharedPreferences("app_blocker_prefs", MODE_PRIVATE)
        if (!prefs.getBoolean("onboarding_completed", false)) {
            startActivity(Intent(this, OnboardingActivity::class.java))
            finish()
            return
        }

        enableDarkEdgeToEdge()
        setContent {
            AppBlockerTheme {
                AppNavigation()
            }
        }
    }
}
