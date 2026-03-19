package com.appblocker.domain.model

data class MotivationalMessage(
    val id: Long = 0,
    val appPackageName: String? = null,
    val message: String,
    val createdAt: Long
)
