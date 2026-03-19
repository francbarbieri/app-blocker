package com.appblocker.domain.model

data class BlockedApp(
    val packageName: String,
    val appName: String,
    val isBlockingEnabled: Boolean,
    val createdAt: Long
)
