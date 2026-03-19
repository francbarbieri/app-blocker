package com.appblocker.service

import android.accessibilityservice.AccessibilityService
import android.view.accessibility.AccessibilityEvent

class AppBlockerAccessibilityService : AccessibilityService() {

    override fun onAccessibilityEvent(event: AccessibilityEvent?) {
        // TODO: Detect blocked app launches and enforce rules
    }

    override fun onInterrupt() {
        // TODO: Handle service interruption
    }
}
