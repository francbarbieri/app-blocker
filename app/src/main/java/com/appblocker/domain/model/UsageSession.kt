package com.appblocker.domain.model

data class UsageSession(
    val id: Long = 0,
    val appPackageName: String,
    val startTime: Long,
    val endTime: Long? = null,
    val durationMs: Long? = null
)
