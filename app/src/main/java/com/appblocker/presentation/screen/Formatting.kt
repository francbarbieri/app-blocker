package com.appblocker.presentation.screen

/** "45 min", "1 hr", "1 hr 30 min". */
internal fun formatMinutesLabel(minutes: Int): String {
    val hours = minutes / 60
    val rest = minutes % 60
    return when {
        hours == 0 -> "$rest min"
        rest == 0 -> "$hours hr"
        else -> "$hours hr $rest min"
    }
}

/** Screen time for display: "0 min", "<1 min", "12 min", "2 hr 5 min". */
internal fun formatUsage(ms: Long): String {
    if (ms <= 0L) return "0 min"
    val minutes = ms / 60_000L
    if (minutes == 0L) return "<1 min"
    return formatMinutesLabel(minutes.toInt())
}

/** Compact form for chart labels: "0", "12m", "2h", "2h5m". */
internal fun formatUsageCompact(ms: Long): String {
    val minutes = (ms / 60_000L).toInt()
    val hours = minutes / 60
    val rest = minutes % 60
    return when {
        minutes == 0 -> "0"
        hours == 0 -> "${rest}m"
        rest == 0 -> "${hours}h"
        else -> "${hours}h${rest}m"
    }
}
