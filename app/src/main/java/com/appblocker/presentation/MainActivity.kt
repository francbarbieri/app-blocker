package com.appblocker.presentation

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import com.appblocker.presentation.screen.MainScreen
import com.appblocker.presentation.theme.AppBlockerTheme

class MainActivity : ComponentActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            AppBlockerTheme {
                MainScreen()
            }
        }
    }
}
