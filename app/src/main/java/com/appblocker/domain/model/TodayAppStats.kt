package com.appblocker.domain.model

/** What the block overlay holds up to the user before they decide. */
data class TodayAppStats(
    /** This overlay's place among today's attempts to open the app, 1-based. */
    val attempt: Int,
    /** Foreground time in the app since local midnight. */
    val usageMs: Long,
)

/**
 * How long the overlay keeps its choices locked. It grows with each attempt
 * today, so pressing through it on reflex gets slower instead of easier:
 * 5 s for the first attempts, 15 s on the fifth, capped at 30 s.
 */
fun reflectionPauseSeconds(attempt: Int): Int =
    (attempt * PAUSE_SECONDS_PER_ATTEMPT).coerceIn(MIN_PAUSE_SECONDS, MAX_PAUSE_SECONDS)

const val PAUSE_SECONDS_PER_ATTEMPT = 3
const val MIN_PAUSE_SECONDS = 5
const val MAX_PAUSE_SECONDS = 30
