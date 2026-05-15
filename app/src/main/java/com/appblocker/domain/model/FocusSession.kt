package com.appblocker.domain.model

data class FocusSession(
    val id: Long = 0,
    val startedAt: Long,
    val expiresAt: Long? = null,
    val endedAt: Long? = null,
) {
    fun isActive(nowEpochMs: Long): Boolean =
        endedAt == null && (expiresAt == null || expiresAt > nowEpochMs)

    fun remainingMs(nowEpochMs: Long): Long? =
        expiresAt?.let { (it - nowEpochMs).coerceAtLeast(0) }
}
