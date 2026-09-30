package com.appblocker.domain.usecase

import com.appblocker.domain.model.ActivitySummary
import com.appblocker.domain.repository.BlockedAppRepository
import com.appblocker.domain.repository.UsageRepository
import com.appblocker.domain.util.Clock
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.combine
import java.time.Instant
import java.time.ZoneId

/**
 * Live usage and overlay-decision summary for the last [invoke] `days` days,
 * today included. The day range is fixed when the flow is created.
 */
class ObserveActivitySummaryUseCase(
    private val usageRepository: UsageRepository,
    private val blockedAppRepository: BlockedAppRepository,
    private val clock: Clock,
    private val zoneProvider: () -> ZoneId = ZoneId::systemDefault,
) {
    operator fun invoke(days: Int): Flow<ActivitySummary> {
        require(days >= 1) { "days must be >= 1" }
        val zone = zoneProvider()
        val today = Instant.ofEpochMilli(clock.nowMs()).atZone(zone).toLocalDate()
        val fromMs = today.minusDays(days - 1L).atStartOfDay(zone).toInstant().toEpochMilli()
        val toMs = today.plusDays(1).atStartOfDay(zone).toInstant().toEpochMilli()

        return combine(
            usageRepository.observeSessionsOverlapping(fromMs, toMs),
            usageRepository.observeUnblockEventsBetween(fromMs, toMs),
            blockedAppRepository.getAllBlockedApps(),
        ) { sessions, events, apps ->
            ActivitySummary.build(
                sessions = sessions,
                events = events,
                appNames = apps.associate { it.packageName to it.appName },
                lastDay = today,
                days = days,
                zone = zone,
            )
        }
    }
}
