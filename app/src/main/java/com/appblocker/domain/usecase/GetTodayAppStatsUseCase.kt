package com.appblocker.domain.usecase

import com.appblocker.domain.model.TodayAppStats
import com.appblocker.domain.repository.UsageRepository
import com.appblocker.domain.util.Clock
import java.time.Instant
import java.time.ZoneId

/**
 * Today's attempts and screen time for one app, for the block overlay.
 *
 * Every overlay the user answered left an unblock event, so the current
 * attempt is today's event count plus one. Overlays left with the home
 * button record nothing and are not counted.
 */
class GetTodayAppStatsUseCase(
    private val usageRepository: UsageRepository,
    private val clock: Clock,
    private val zoneProvider: () -> ZoneId = ZoneId::systemDefault,
) {
    suspend operator fun invoke(packageName: String): TodayAppStats {
        val now = clock.nowMs()
        val zone = zoneProvider()
        val midnight = Instant.ofEpochMilli(now).atZone(zone).toLocalDate()
            .atStartOfDay(zone).toInstant().toEpochMilli()
        return TodayAppStats(
            attempt = usageRepository.getUnblockEventCountSince(packageName, midnight) + 1,
            usageMs = usageRepository.getUsageMs(listOf(packageName), midnight, now),
        )
    }
}
