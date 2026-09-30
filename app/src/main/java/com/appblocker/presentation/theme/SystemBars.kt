package com.appblocker.presentation.theme

import android.graphics.Color
import androidx.activity.ComponentActivity
import androidx.activity.SystemBarStyle
import androidx.activity.enableEdgeToEdge

/**
 * Edge-to-edge with light system-bar icons. The app always renders a dark
 * theme, so the bars must not follow the phone's light/dark setting (which
 * would draw dark icons on a dark background in light mode).
 */
fun ComponentActivity.enableDarkEdgeToEdge() {
    enableEdgeToEdge(
        statusBarStyle = SystemBarStyle.dark(Color.TRANSPARENT),
        navigationBarStyle = SystemBarStyle.dark(Color.TRANSPARENT),
    )
}
