package com.appblocker.domain.model

data class UnblockEvent(
    val id: Long = 0,
    val appPackageName: String,
    val timestamp: Long,
    val userProceeded: Boolean
)
