package com.appblocker.domain.model

/**
 * Why an app is blocked right now. The overlay shows this to the user so a
 * block never feels arbitrary. `null` (from [com.appblocker.domain.usecase.IsAppBlockedUseCase])
 * means "not blocked".
 */
sealed interface BlockReason {

    /** A focus session is running; every enabled app on the list is blocked. */
    data object FocusSession : BlockReason

    /** The app is on the list with no schedules, so it is blocked at all times. */
    data object AlwaysBlocked : BlockReason

    /**
     * A time-window schedule covers the current moment.
     * [until] is the window's end as "HH:mm", or null when the window spans the whole day.
     */
    data class ScheduledWindow(val until: String?) : BlockReason

    /** Today's usage reached a daily-limit schedule of [limitMinutes]. */
    data class DailyLimitReached(val limitMinutes: Int) : BlockReason
}
